package org.me.chatManager.spam;


import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Detects "templated" spam.
 *
 * A common bot/spammer trick is to rotate through a small
 * set of fixed phrases while tacking on a random token to
 * each message (e.g. a random suffix). That defeats plain
 * duplicate detection and consecutive-message similarity
 * checks, since the message text is "different" every time
 * and consecutive messages often use different templates.
 *
 * Instead of comparing a message only to the previous one,
 * this tracks how often each significant word has recently
 * been used by a player. A message built mostly out of
 * words the player keeps repeating is very likely templated
 * spam, even though the message as a whole looks new.
 */
public final class TemplateSpamAnalyzer {


    /*
     * Extremely common words are excluded so that normal
     * conversation doesn't trip the detector. A message that
     * boils down to just one of these leftover words (e.g.
     * "again", "thanks") should never be flagged on its own
     * — see minSignificantWords below for the other half of
     * that fix.
     */
    private static final Set<String> STOPWORDS =
            Set.of(
                    "a", "an", "the", "i", "im", "you", "he", "she",
                    "it", "we", "they", "is", "am", "are", "was",
                    "were", "be", "been", "being", "to", "of", "in",
                    "on", "at", "for", "and", "or", "but", "with",
                    "this", "that", "these", "those", "my", "your",
                    "his", "her", "its", "our", "their", "me", "him",
                    "us", "them", "do", "does", "did", "not", "no",
                    "yes", "ok", "okay", "lol", "lmao", "haha", "hi",
                    "hey", "yo", "hello", "sup", "gm", "gn", "morning",
                    "night", "bye", "cya", "wb", "brb", "afk", "gtg",
                    "what", "why", "who", "when", "where", "again",
                    "please", "thanks", "thank", "ty", "np", "gg",
                    "yeah", "yep", "yea", "nope", "sure", "maybe",
                    "sorry", "welcome", "nice", "cool", "wow", "same",
                    "true", "right", "wrong", "here", "there", "now",
                    "just", "so", "if", "then", "can", "will", "would"
            );



    private volatile int windowSize;

    private volatile int repeatCountThreshold;

    private volatile int minSignificantWords;

    private volatile long timeWindowMillis;



    /*
     * Player UUID -> word -> occurrences within the window
     */
    private final Map<UUID, Map<String, Integer>> wordCounts =
            new ConcurrentHashMap<>();


    /*
     * Player UUID -> per-message word sets,
     * oldest first, used to age counts out
     * of the window.
     */
    private final Map<UUID, Deque<WordEntry>> history =
            new ConcurrentHashMap<>();



    public TemplateSpamAnalyzer(
            int windowSize,
            int repeatCountThreshold,
            int minSignificantWords
    ) {
        this(windowSize, repeatCountThreshold, minSignificantWords, 10_000L);
    }

    public TemplateSpamAnalyzer(
            int windowSize,
            int repeatCountThreshold,
            int minSignificantWords,
            long timeWindowMillis
    ) {
        this.windowSize = Math.max(1, windowSize);
        this.repeatCountThreshold = Math.max(1, repeatCountThreshold);
        this.minSignificantWords = Math.max(1, minSignificantWords);
        this.timeWindowMillis = Math.max(1L, timeWindowMillis);
    }

    /** Apply new configuration and discard data scored under old settings. */
    public void reconfigure(
            int windowSize,
            int repeatCountThreshold,
            int minSignificantWords,
            long timeWindowMillis
    ) {
        this.windowSize = Math.max(1, windowSize);
        this.repeatCountThreshold = Math.max(1, repeatCountThreshold);
        this.minSignificantWords = Math.max(1, minSignificantWords);
        this.timeWindowMillis = Math.max(1L, timeWindowMillis);
        clear();
    }




    /**
     * Fraction (0.0-1.0) of this message's significant words
     * that the player has already repeated at least
     * {repeatCountThreshold} times recently.
     *
     * Returns 0 if the message has fewer than
     * {minSignificantWords} significant words (short/simple
     * messages are never flagged on their own — a single
     * reused word like "again" shouldn't be enough) or the
     * player has no tracked history yet.
     */
    public double score(
            UUID uuid,
            String message
    ) {

        if (uuid == null) {
            return 0;
        }


        Set<String> words =
                significantWords(message);

        if (words.size() < minSignificantWords) {
            return 0;
        }


        Deque<WordEntry> deque = history.get(uuid);
        Map<String, Integer> counts = wordCounts.get(uuid);
        if (deque == null || counts == null) {
            return 0;
        }

        synchronized (deque) {
            pruneExpired(uuid, deque, counts, System.nanoTime());
            int repeated = 0;
            for (String word : words) {
                if (counts.getOrDefault(word, 0) >= repeatCountThreshold) {
                    repeated++;
                }
            }
            return (double) repeated / words.size();
        }

    }




    /**
     * Records this message's significant words so future
     * messages can be scored against them.
     */
    public void update(
            UUID uuid,
            String message
    ) {

        if (uuid == null) {
            return;
        }


        Set<String> words = significantWords(message);
        if (words.isEmpty()) {
            return;
        }

        Map<String, Integer> counts = wordCounts.computeIfAbsent(
                uuid, key -> new ConcurrentHashMap<>());
        Deque<WordEntry> deque = history.computeIfAbsent(
                uuid, key -> new ArrayDeque<>());

        synchronized (deque) {
            long now = System.nanoTime();
            pruneExpired(uuid, deque, counts, now);
            for (String word : words) {
                counts.merge(word, 1, Integer::sum);
            }
            deque.addLast(new WordEntry(words, now));
            while (deque.size() > windowSize) {
                removeEntry(deque.removeFirst(), counts);
            }
        }
    }

    private void pruneExpired(UUID uuid, Deque<WordEntry> deque,
                              Map<String, Integer> counts, long nowNanos) {
        long windowNanos = timeWindowMillis > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE : Math.max(1L, timeWindowMillis) * 1_000_000L;
        while (!deque.isEmpty() && nowNanos - deque.peekFirst().recordedNanos > windowNanos) {
            removeEntry(deque.removeFirst(), counts);
        }
    }

    private void removeEntry(WordEntry entry, Map<String, Integer> counts) {
        for (String word : entry.words) {
            counts.computeIfPresent(word, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    private static final class WordEntry {
        private final Set<String> words;
        private final long recordedNanos;

        private WordEntry(Set<String> words, long recordedNanos) {
            this.words = words;
            this.recordedNanos = recordedNanos;
        }
    }


    /**
     * Splits a message into lowercase alphanumeric tokens,
     * dropping stopwords and very short tokens. Deliberately
     * does not collapse repeated characters (unlike
     * TextNormalizer) so short all-caps spam tokens such as
     * "AAAAA" or "BBBB" remain distinguishable words.
     */
    private Set<String> significantWords(
            String message
    ) {

        if (message == null || message.isBlank()) {
            return Collections.emptySet();
        }


        Set<String> words =
                new HashSet<>();

        for (String token :
                message.toLowerCase(Locale.ROOT)
                        .split("[^\\p{L}\\p{Nd}]+")) {

            if (token.length() < 3
                    ||
                    STOPWORDS.contains(token)) {

                continue;

            }

            words.add(token);

        }

        return words;

    }




    public void remove(
            UUID uuid
    ) {

        wordCounts.remove(uuid);
        history.remove(uuid);

    }



    public void clear() {

        wordCounts.clear();
        history.clear();

    }

}
