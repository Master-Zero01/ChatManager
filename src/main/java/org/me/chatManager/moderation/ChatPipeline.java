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
import java.util.regex.Pattern;


public class ChatPipeline {


    private final ChatManager plugin;

    private final TextNormalizer normalizer;

    private final PhraseMatcher phraseMatcher;

    private final DuplicateTracker duplicateTracker;

    private final RateLimiter rateLimiter;

    private final SimilarityEngine similarityEngine;

    private final TemplateSpamAnalyzer templateSpamAnalyzer;

    /** Recent accepted messages, compared with spaces and word boundaries preserved. */
    private final RecentSimilarityTracker recentSimilarityTracker;




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
        this.recentSimilarityTracker = new RecentSimilarityTracker(
                plugin.getConfig().getInt("chat.anti-spam.max-history", 10));

        this.duplicateTracker = new DuplicateTracker(
                plugin.getConfig().getInt("chat.anti-spam.max-history", 20),
                plugin.getConfig().getLong("chat.no-duplicate.time-window-ms", 30_000L));


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
                                ),
                        plugin.getConfig().getLong("chat.anti-spam.time-window-ms", 10_000L)
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

        // Strip appended 12+ character tokens from the uncollapsed version.
        // Counting before repeat-collapse matters: normalization could shrink a
        // 12-character nonce such as "rZEJgFUlFfFo" below the length threshold.
        String spamComparisonMessage =
                SpamMessageNormalizer.stripTrailingLongTokens(repetitionMessage);
        String normalized = normalizer.normalizeComparison(spamComparisonMessage);
        String canonicalSpamMessage = normalizer.normalizeModeration(spamComparisonMessage);

        // Similarity checks use this same suffix-free canonical message so
        // duplicate and similarity tracking agree on what constitutes the body.
        if (cfg.getBoolean("chat.anti-spam.enabled", true)
                && cfg.getBoolean("chat.anti-spam.recent-similarity.enabled", true)
                && !hasBypass(player, cfg, "chat.anti-spam.bypass-permission", "chatmanager.bypass.spam")) {
            double legacyThreshold = cfg.getInt("chat.anti-spam.similarity-threshold", 80) / 100.0;
            double threshold = cfg.getDouble(
                    "chat.anti-spam.recent-similarity.threshold", legacyThreshold);
            long similarityWindow = cfg.getLong(
                    "chat.anti-spam.recent-similarity.time-window-ms",
                    cfg.getLong("chat.no-duplicate.time-window-ms", 30_000L));
            double similarity = recentSimilarityTracker.findSimilarity(
                    uuid, canonicalSpamMessage, threshold, similarityWindow);
            if (similarity >= 0.0) {
                return ModerationResult.blocked(
                        cfg.getString("chat.messages.spam",
                                "&cPlease don't send very similar messages repeatedly."),
                        "RECENT_SIMILAR_MESSAGE", similarity * 100.0);
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
        if (!hasBypass(player, cfg, "chat.phrases.bypass-permission", "chatmanager.bypass.phrases")) {
            if (cfg.getBoolean("chat.phrases.enabled", true)
                    && phraseMatcher.firstMatch(moderationMessage,
                            cfg.getStringList("chat.phrases.words")).isPresent()) {
                return ModerationResult.blocked(
                        cfg.getString("chat.messages.phrase-blocked",
                                "&cYour message contains blocked content."),
                        "BLOCKED_PHRASE");
            }

            // Domain protection is independent of the configurable phrase list.
            if (cfg.getBoolean("chat.phrases.block-domain-patterns", true)
                    && isDomainLike(message)) {
                return ModerationResult.blocked(
                        cfg.getString("chat.messages.phrase-blocked",
                                "&cYour message contains blocked content."),
                        "BLOCKED_DOMAIN");
            }
        }



        /*
         * Chat cooldown
         */
        if (cfg.getBoolean("chat.cooldown.enabled", true)
                && !hasBypass(player, cfg, "chat.cooldown.bypass-permission", "chatmanager.bypass.cooldown")) {


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





        // Exact duplicate checks must run for every message, including short
        // one-word messages. Only broader similarity/template analysis is
        // gated to reduce false positives on tiny, common chat responses.
        boolean skipTemplateChecks =
                normalized.length() < 8
                        || wordCount(spamComparisonMessage) < 2;





        /*
         * Character flooding. Repeated word/sequence checks are controlled
         * separately by chat.anti-spam.prevent-repetition.
         */
        if (cfg.getBoolean("chat.anti-spam.enabled", true)
                && !hasBypass(player, cfg, "chat.anti-spam.bypass-permission", "chatmanager.bypass.spam")
                && cfg.getBoolean("chat.anti-spam.prevent-flood", true)
                && REPEATED_CHARACTER.matcher(repetitionMessage).find()) {
            return ModerationResult.blocked(
                    cfg.getString("chat.messages.flood", "&cPlease avoid character flooding."),
                    "CHARACTER_FLOOD");
        }

        if (cfg.getBoolean("chat.anti-spam.enabled", true)
                && !hasBypass(player, cfg, "chat.anti-spam.bypass-permission", "chatmanager.bypass.spam")
                && cfg.getBoolean("chat.anti-spam.prevent-repetition", true)) {
            if (REPEATED_SEQUENCE.matcher(repetitionMessage.replace(" ", "")).find()) {
                return ModerationResult.blocked(
                        cfg.getString("chat.messages.flood", "&cPlease avoid repetitive messages."),
                        "REPEATED_SEQUENCE");
            }
            if (REPEATED_WORD.matcher(moderationMessage).find()) {
                return ModerationResult.blocked(
                        cfg.getString("chat.messages.flood", "&cPlease avoid repetitive messages."),
                        "WORD_FLOOD");
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
        if (cfg.getBoolean("chat.anti-spam.enabled", true)
                && !hasBypass(player, cfg, "chat.anti-spam.bypass-permission", "chatmanager.bypass.spam")
                && cfg.getBoolean("chat.anti-spam.prevent-caps", true)) {


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
        if (cfg.getBoolean("chat.no-duplicate.enabled", true)) {

            if (!hasBypass(player, cfg, "chat.no-duplicate.bypass-permission", "chatmanager.bypass.duplicate")) {



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
        if (cfg.getBoolean("chat.anti-spam.enabled", true)) {

            if (!hasBypass(player, cfg, "chat.anti-spam.bypass-permission", "chatmanager.bypass.spam")) {


                String previous = duplicateTracker.getPrevious(
                        uuid,
                        cfg.getLong("chat.anti-spam.time-window-ms", 10_000L));



                if (previous != null
                        && previous.length() >= 4
                        && normalized.length() >= 4) {


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
        if (!skipTemplateChecks
                && cfg.getBoolean("chat.anti-spam.enabled", true)
                && cfg.getBoolean("chat.anti-spam.template-detection.enabled", true)) {

            if (!hasBypass(player, cfg, "chat.anti-spam.bypass-permission", "chatmanager.bypass.spam")) {


                double templateScore = templateSpamAnalyzer.score(
                        uuid, repetitionMessage);
                double templateThreshold = cfg.getDouble(
                        "chat.anti-spam.template-detection.message-word-repeat-ratio", 0.5);

                if (templateScore >= templateThreshold) {
                    return ModerationResult.blocked(
                            cfg.getString("chat.messages.template-spam",
                                    "&cPlease stop sending repetitive spam-like messages."),
                            "TEMPLATE_SPAM", templateScore * 100);
                }

                // Only accepted messages enter the analysis history. A blocked
                // attempt must not mutate the baseline used for future scoring.
                templateSpamAnalyzer.update(uuid, repetitionMessage);

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

        recentSimilarityTracker.record(
                uuid, canonicalSpamMessage,
                cfg.getLong("chat.anti-spam.recent-similarity.time-window-ms",
                        cfg.getLong("chat.no-duplicate.time-window-ms", 30_000L)));

        return ModerationResult.allowed();

    }









    /**
     * OP status does not silently disable moderation. Operators can opt into
     * the historical global bypass with chat.operators-bypass, or receive a
     * specific bypass node explicitly through permissions.
     */
    private boolean hasBypass(Player player, FileConfiguration cfg,
                              String permissionPath, String defaultPermission) {
        if (player.isOp() && cfg.getBoolean("chat.operators-bypass", false)) {
            return true;
        }
        String permission = cfg.getString(permissionPath, defaultPermission);
        return permission != null && player.hasPermission(permission);
    }

    private boolean isDomainLike(String message) {
        return DomainDetector.containsDomainLike(normalizer.normalizeForDomainDetection(message));
    }


    /**
     * Reset stateful moderation trackers after configuration changes. Trackers
     * whose sizing/window settings are captured at construction are rebuilt in
     * place; all transient history is cleared so stale messages do not leak
     * across changed rules.
     */
    public void reloadConfiguration() {
        FileConfiguration cfg = plugin.getConfig();
        duplicateTracker.reconfigure(
                cfg.getInt("chat.anti-spam.max-history", 20),
                cfg.getLong("chat.no-duplicate.time-window-ms", 30_000L));
        recentSimilarityTracker.reconfigure(
                cfg.getInt("chat.anti-spam.max-history", 10));
        templateSpamAnalyzer.reconfigure(
                cfg.getInt("chat.anti-spam.template-detection.history-window", 20),
                cfg.getInt("chat.anti-spam.template-detection.word-repeat-count-threshold", 4),
                cfg.getInt("chat.anti-spam.template-detection.min-significant-words", 2),
                cfg.getLong("chat.anti-spam.time-window-ms", 10_000L));
        rateLimiter.clear();
        phraseMatcher.clearCache();
    }


    /** Drop transient per-player history on quit. */
    public void removePlayer(UUID uuid) {
        if (uuid == null) return;
        recentSimilarityTracker.remove(uuid);
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



        recentSimilarityTracker.remove(uuid);
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
        recentSimilarityTracker.clear();


        rateLimiter.clear();


        templateSpamAnalyzer.clear();

    }


}