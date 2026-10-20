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



    private final int windowSize;

    private final int repeatCountThreshold;

    private final int minSignificantWords;



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
    private final Map<UUID, Deque<Set<String>>> history =
            new ConcurrentHashMap<>();



    public TemplateSpamAnalyzer(
            int windowSize,
            int repeatCountThreshold,
            int minSignificantWords
    ) {

        this.windowSize =
                Math.max(1, windowSize);

        this.repeatCountThreshold =
                Math.max(1, repeatCountThreshold);

        this.minSignificantWords =
                Math.max(1, minSignificantWords);

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


        Map<String, Integer> counts =
                wordCounts.get(uuid);

        if (counts == null) {
            return 0;
        }


        int repeated = 0;

        for (String word : words) {

            if (counts.getOrDefault(word, 0)
                    >= repeatCountThreshold) {

                repeated++;

            }

        }


        return (double) repeated / words.size();

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


        Set<String> words =
                significantWords(message);

        if (words.isEmpty()) {
            return;
        }


        Map<String, Integer> counts =
                wordCounts.computeIfAbsent(
                        uuid,
                        key -> new ConcurrentHashMap<>()
                );


        Deque<Set<String>> deque =
                history.computeIfAbsent(
                        uuid,
                        key -> new ArrayDeque<>()
                );


        synchronized (deque) {

            for (String word : words) {
                counts.merge(word, 1, Integer::sum);
            }

            deque.addLast(words);

            while (deque.size() > windowSize) {

                Set<String> oldest =
                        deque.removeFirst();

                for (String word : oldest) {

                    counts.computeIfPresent(
                            word,
                            (key, count) ->
                                    count <= 1 ? null : count - 1
                    );

                }

            }

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
