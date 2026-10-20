package org.me.chatManager.moderation;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.me.chatManager.ChatManager;
import org.me.chatManager.normalizer.PhraseMatcher;
import org.me.chatManager.normalizer.TextNormalizer;
import org.me.chatManager.spam.DuplicateTracker;
import org.me.chatManager.spam.RateLimiter;
import org.me.chatManager.spam.SimilarityEngine;
import org.me.chatManager.spam.TemplateSpamAnalyzer;

import java.util.UUID;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;


public class ChatPipeline {


    private final ChatManager plugin;

    private final TextNormalizer normalizer;

    private final PhraseMatcher phraseMatcher;

    private final DuplicateTracker duplicateTracker;

    private final RateLimiter rateLimiter;

    private final SimilarityEngine similarityEngine;

    private final TemplateSpamAnalyzer templateSpamAnalyzer;

    /** Raw recent messages used by the three-message similarity guard. */
    private final Map<UUID, Deque<String>> rawMessageHistory = new ConcurrentHashMap<>();




    private static final Pattern REPEATED_CHARACTER =
            Pattern.compile("(.)\\1{5,}");



    private static final Pattern REPEATED_WORD =
            Pattern.compile(
                    "\\b(\\w+)\\b(?:\\s+\\1\\b){2,}",
                    Pattern.CASE_INSENSITIVE
            );



    private static final Pattern REPEATED_SEQUENCE =
            Pattern.compile(
                    "(.{2,8})\\1{3,}"
            );



    /*
     * Matches anything shaped like a domain/link
     * ("qroma.org", "discord.com", "test.com/invite")
     * regardless of whether it's on the static phrase
     * list — a fixed word list can never keep up with new
     * domains a spammer registers.
     *
     * Deliberately checked against the RAW message (see
     * below) rather than moderationMessage, since that
     * normalization strips punctuation entirely — the dot
     * is exactly what this pattern needs to see.
     */
    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile(
                    "(?i)\\b[a-z0-9-]{2,}\\s*(?:\\.|\\bdot\\b|\\bd0t\\b)\\s*(com|net|org|gg|io|co|me|link|xyz|" +
                            "tv|info|biz|shop|store|site|dev|app|club|online|website|us|uk|de|ru|cn|top|pro|wiki|gg)\\b"
            );

    private static final int RAW_HISTORY_LIMIT = 3;
    private static final double RAW_SIMILARITY_THRESHOLD = 0.80;
    private static final Pattern LONG_WORD_SPLIT = Pattern.compile("\\s+");







    public ChatPipeline(
            ChatManager plugin,
            TextNormalizer normalizer,
            PhraseMatcher phraseMatcher,
            SimilarityEngine similarityEngine
    ) {

        this.plugin = plugin;

        this.normalizer = normalizer;

        this.phraseMatcher = phraseMatcher;

        this.similarityEngine = similarityEngine;

        this.duplicateTracker =
                new DuplicateTracker(
                        plugin.getConfig()
                                .getInt(
                                        "chat.anti-spam.max-history",
                                        20
                                )
                );


        this.rateLimiter =
                new RateLimiter();


        this.templateSpamAnalyzer =
                new TemplateSpamAnalyzer(
                        plugin.getConfig()
                                .getInt(
                                        "chat.anti-spam.template-detection.history-window",
                                        20
                                ),
                        plugin.getConfig()
                                .getInt(
                                        "chat.anti-spam.template-detection.word-repeat-count-threshold",
                                        4
                                ),
                        plugin.getConfig()
                                .getInt(
                                        "chat.anti-spam.template-detection.min-significant-words",
                                        2
                                )
                );

    }









    /**
     * Main moderation pipeline
     *
     * Original message
     * ↓
     * Moderation normalization
     * ↓
     * Phrase matching
     * ↓
     * Cooldown
     * ↓
     * Flood
     * ↓
     * Caps
     * ↓
     * Duplicate
     * ↓
     * Similarity
     * ↓
     * Templated spam
     * ↓
     * Store normalized message
     */
    public ModerationResult process(
            Player player,
            String message
    ) {


        if (message == null ||
                message.isBlank()) {

            return ModerationResult.allowed();

        }



        UUID uuid =
                player.getUniqueId();



        FileConfiguration cfg =
                plugin.getConfig();





        /*
         * This is the text used for ALL moderation.
         *
         * Handles:
         *
         * - Unicode normalization
         * - Homoglyphs
         * - Leetspeak
         * - Alternating caps
         * - Case differences
         *
         * Example:
         *
         * jOiN My SeRvEr
         *
         * becomes:
         *
         * join my server
         */
        String moderationMessage =
                normalizer.normalizeModeration(
                        message
                );






        String normalized =
                normalizer.normalizeComparison(
                        moderationMessage
                );




        /*
         * Same normalization as moderationMessage, but WITHOUT
         * collapsing repeated characters. moderationMessage
         * collapses any run to 2 characters (so "hello" and
         * "helloooo" compare as equal for phrase/duplicate
         * purposes) — but that means checks that need to see
         * the actual repeat count (flood, repeated-sequence,
         * template-word tracking) would go blind if they used
         * moderationMessage: a 26-character flood or a short
         * spam word like "AAA" would already look like "aa"
         * by the time they got to inspect it.
         */
        String repetitionMessage =
                normalizer.normalizeForRepetition(
                        message
                );


        // Mirror the proven three-message similarity guard: ignore very short
        // messages and long individual tokens so coordinate strings / elongated
        // words do not dominate the comparison. History is only committed after
        // the message passes moderation.
        if (!player.hasPermission(cfg.getString(
                        "chat.anti-spam.bypass-permission",
                        "chatmanager.bypass.spam"
                ))
                && normalized.length() >= 20
                && cfg.getBoolean("chat.anti-spam.recent-similarity.enabled", true)) {
            String comparison = filterLongWords(normalized);
            Deque<String> history = rawMessageHistory.get(uuid);
            if (history != null) {
                synchronized (history) {
                    for (String previous : history) {
                        if (previous.length() < 20) continue;
                        if (similarityPercentage(filterLongWords(previous), comparison)
                                >= cfg.getDouble("chat.anti-spam.recent-similarity.threshold", RAW_SIMILARITY_THRESHOLD)) {
                            return ModerationResult.blocked(
                                    cfg.getString("chat.messages.spam", "&cPlease don't send very similar messages repeatedly."),
                                    "RECENT_SIMILAR_MESSAGE",
                                    similarityPercentage(filterLongWords(previous), comparison) * 100.0
                            );
                        }
                    }
                }
            }
        }









        /*
         * Phrase matching
         *
         * Detects:
         *
         * discord.gg
         * d!scord.gg
         * dіscord.gg
         *
         */
        if (cfg.getBoolean(
                "chat.phrases.enabled",
                true
        )) {


            if (!player.hasPermission(
                    cfg.getString(
                            "chat.phrases.bypass-permission",
                            "chatmanager.bypass.phrases"
                    )
            )) {



                if (phraseMatcher.firstMatch(
                        moderationMessage,
                        cfg.getStringList(
                                "chat.phrases.words"
                        )
                ).isPresent()) {



                    return ModerationResult.blocked(
                            cfg.getString(
                                    "chat.messages.phrase-blocked",
                                    "&cYour message contains blocked content."
                            ),
                            "BLOCKED_PHRASE"
                    );

                }



                if (cfg.getBoolean(
                        "chat.phrases.block-domain-patterns",
                        true
                )
                        &&
                        isDomainLike(message)) {


                    return ModerationResult.blocked(
                            cfg.getString(
                                    "chat.messages.phrase-blocked",
                                    "&cYour message contains blocked content."
                            ),
                            "BLOCKED_DOMAIN"
                    );

                }

            }

        }









        /*
         * Chat cooldown
         */
        if (!player.hasPermission(
                cfg.getString(
                        "chat.cooldown.bypass-permission",
                        "chatmanager.bypass.cooldown"
                )
        )) {


            int seconds =
                    cfg.getInt(
                            "chat.cooldown.time-seconds",
                            3
                    );



            if (!rateLimiter.tryAcquireSeconds(
                    uuid,
                    seconds
            )) {


                return ModerationResult.blocked(
                        cfg.getString(
                                "chat.messages.cooldown",
                                "&cPlease wait {time}s before chatting again."
                        ).replace(
                                "{time}",
                                String.valueOf(
                                        rateLimiter.getRemainingSeconds(
                                                uuid,
                                                seconds
                                        )
                                )
                        ),
                        "CHAT_COOLDOWN"
                );

            }

        }





        boolean skipSpamChecks =
                normalized.length() < 8
                        ||
                        wordCount(moderationMessage) < 2;





        /*
         * Flood checks
         */
        if (cfg.getBoolean(
                "chat.anti-spam.prevent-flood",
                true
        )) {


            if (REPEATED_CHARACTER.matcher(
                    repetitionMessage
            ).find()) {


                return ModerationResult.blocked(
                        cfg.getString(
                                "chat.messages.flood",
                                "&cPlease avoid character flooding."
                        ),
                        "CHARACTER_FLOOD"
                );

            }

            if (REPEATED_SEQUENCE.matcher(
                    repetitionMessage.replace(" ", "")
            ).find()) {


                return ModerationResult.blocked(
                        cfg.getString(
                                "chat.messages.flood",
                                "&cPlease avoid repetitive messages."
                        ),
                        "REPEATED_SEQUENCE"
                );

            }




            if (REPEATED_WORD.matcher(
                    moderationMessage
            ).find()) {


                return ModerationResult.blocked(
                        cfg.getString(
                                "chat.messages.flood",
                                "&cPlease avoid repetitive messages."
                        ),
                        "WORD_FLOOD"
                );

            }

        }









        /*
         * Caps detection
         *
         * Uses the ORIGINAL message.
         *
         * Alternating caps have already been
         * normalized above, so they cannot bypass
         * phrase/spam checks.
         *
         * Normal shouting still gets detected:
         *
         * JOIN MY SERVER NOW
         */
        if (cfg.getBoolean(
                "chat.anti-spam.prevent-caps",
                true
        )) {


            int letters =
                    countLetters(message);



            if (letters >= 10) {


                double ratio =
                        capsRatio(message);



                double max =
                        cfg.getDouble(
                                "chat.anti-spam.max-caps-ratio",
                                0.60
                        );



                if (ratio >= max) {


                    return ModerationResult.blocked(
                            cfg.getString(
                                    "chat.messages.caps",
                                    "&cPlease avoid excessive capital letters."
                            ),
                            "CAPS"
                    );

                }

            }

        }









        /*
         * Duplicate detection
         *
         * Uses normalized moderation text.
         *
         * These become identical:
         *
         * Hello World
         * hELLo WoRLD
         * h3ll0 world
         */
        if (!skipSpamChecks
                &&
                cfg.getBoolean(
                        "chat.no-duplicate.enabled",
                        true
                )) {


            if (!player.hasPermission(
                    cfg.getString(
                            "chat.no-duplicate.bypass-permission",
                            "chatmanager.bypass.duplicate"
                    )
            )) {



                if (duplicateTracker.isDuplicate(
                        uuid,
                        normalized
                )) {


                    return ModerationResult.blocked(
                            cfg.getString(
                                    "chat.messages.duplicate",
                                    "&cPlease don't send the same message twice."
                            ),
                            "DUPLICATE"
                    );

                }

            }

        }









        /*
         * Similarity detection
         */
        if (!skipSpamChecks
                &&
                cfg.getBoolean(
                        "chat.anti-spam.enabled",
                        true
                )) {


            if (!player.hasPermission(
                    cfg.getString(
                            "chat.anti-spam.bypass-permission",
                            "chatmanager.bypass.spam"
                    )
            )) {


                String previous =
                        duplicateTracker.getPrevious(
                                uuid
                        );



                if (previous != null
                        &&
                        previous.length() >= 8) {


                    double similarity =
                            similarityEngine.compare(
                                    previous,
                                    normalized
                            );



                    int threshold =
                            cfg.getInt(
                                    "chat.anti-spam.similarity-threshold",
                                    90
                            );



                    if (similarity >= threshold) {


                        return ModerationResult.blocked(
                                cfg.getString(
                                        "chat.messages.spam",
                                        "&cPlease don't send very similar messages repeatedly."
                                ),
                                "SIMILAR_MESSAGE",
                                similarity
                        );

                    }

                }

            }

        }









        /*
         * Templated spam detection
         *
         * Catches spam that rotates through a small set of
         * phrases with a random token appended to dodge the
         * duplicate and consecutive-similarity checks above,
         * by watching for words that keep reappearing across
         * a window of the player's recent messages instead of
         * only comparing to the single previous message.
         *
         * Uses the same normalized moderation text as phrase
         * matching above, so homoglyph/leetspeak substitution
         * can't be used to dodge this either.
         */
        if (!skipSpamChecks
                &&
                cfg.getBoolean(
                        "chat.anti-spam.template-detection.enabled",
                        true
                )) {


            if (!player.hasPermission(
                    cfg.getString(
                            "chat.anti-spam.bypass-permission",
                            "chatmanager.bypass.spam"
                    )
            )) {


                double templateScore =
                        templateSpamAnalyzer.score(
                                uuid,
                                repetitionMessage
                        );


                templateSpamAnalyzer.update(
                        uuid,
                        repetitionMessage
                );


                double templateThreshold =
                        cfg.getDouble(
                                "chat.anti-spam.template-detection.message-word-repeat-ratio",
                                0.5
                        );


                if (templateScore >= templateThreshold) {


                    return ModerationResult.blocked(
                            cfg.getString(
                                    "chat.messages.template-spam",
                                    "&cPlease stop sending repetitive spam-like messages."
                            ),
                            "TEMPLATE_SPAM",
                            templateScore * 100
                    );

                }

            }

        }




        /*
         * Store normalized message history
         *
         * The stored value is already cleaned:
         *
         * - lowercase
         * - homoglyph corrected
         * - leetspeak corrected
         * - alternating caps removed
         */
        duplicateTracker.update(
                uuid,
                normalized
        );

        Deque<String> rawHistory = rawMessageHistory.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        synchronized (rawHistory) {
            rawHistory.addLast(normalized);
            while (rawHistory.size() > RAW_HISTORY_LIMIT) rawHistory.removeFirst();
        }

        return ModerationResult.allowed();

    }









    /** Remove words longer than 12 characters before similarity comparison. */
    private String filterLongWords(String message) {
        StringBuilder result = new StringBuilder();
        for (String word : LONG_WORD_SPLIT.split(message.trim())) {
            if (word.length() <= 12) {
                if (!result.isEmpty()) result.append(' ');
                result.append(word);
            }
        }
        return result.toString();
    }

    private boolean isDomainLike(String message) {
        String expanded = message.toLowerCase(Locale.ROOT)
                .replaceAll("\\bdot\\b", ".")
                .replaceAll("\\bd0t\\b", ".");
        return DOMAIN_PATTERN.matcher(expanded).find();
    }

    private double similarityPercentage(String first, String second) {
        int max = Math.max(first.length(), second.length());
        if (max == 0) return 1.0;
        if (Math.abs(first.length() - second.length()) > max * (1.0 - RAW_SIMILARITY_THRESHOLD)) return 0.0;
        int distance = levenshteinDistance(first, second);
        return 1.0 - ((double) distance / max);
    }

    private int levenshteinDistance(String first, String second) {
        if (first.length() < second.length()) return levenshteinDistance(second, first);
        int n = first.length(), m = second.length();
        if (m == 0) return n;
        int[] costs = new int[m + 1];
        for (int j = 0; j <= m; j++) costs[j] = j;
        for (int i = 1; i <= n; i++) {
            int previous = costs[0];
            costs[0] = i;
            for (int j = 1; j <= m; j++) {
                int old = costs[j];
                costs[j] = first.charAt(i - 1) == second.charAt(j - 1)
                        ? previous : Math.min(Math.min(costs[j - 1], costs[j]), previous) + 1;
                previous = old;
            }
        }
        return costs[m];
    }

    /** Drop transient per-player history on quit. */
    public void removePlayer(UUID uuid) {
        if (uuid == null) return;
        rawMessageHistory.remove(uuid);
        duplicateTracker.remove(uuid);
        templateSpamAnalyzer.remove(uuid);
        rateLimiter.remove(uuid);
    }

    private int countLetters(
            String message
    ) {

        int count = 0;


        for (char c :
                message.toCharArray()) {


            if (Character.isLetter(c)) {

                count++;

            }

        }


        return count;

    }









    private double capsRatio(
            String message
    ) {

        int letters = 0;

        int capitals = 0;



        for (char c :
                message.toCharArray()) {


            if (Character.isLetter(c)) {


                letters++;


                if (Character.isUpperCase(c)) {

                    capitals++;

                }

            }

        }



        if (letters == 0) {

            return 0;

        }



        return (double) capitals / letters;

    }









    private int wordCount(
            String message
    ) {

        if (message == null ||
                message.isBlank()) {

            return 0;

        }



        return message.trim()
                .split("\\s+")
                .length;

    }

    /**
     * Removes cached data for a player.
     *
     * Called on quit/kick.
     */
    public void cleanup(
            UUID uuid
    ) {


        if (uuid == null) {

            return;

        }



        duplicateTracker.remove(
                uuid
        );



        rateLimiter.remove(
                uuid
        );



        templateSpamAnalyzer.remove(
                uuid
        );

    }









    /**
     * Clears moderation memory.
     *
     * Called during plugin shutdown.
     */
    public void shutdown() {


        duplicateTracker.clear();


        rateLimiter.clear();


        templateSpamAnalyzer.clear();

    }


}