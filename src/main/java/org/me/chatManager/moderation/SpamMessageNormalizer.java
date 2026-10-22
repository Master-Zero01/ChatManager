package org.me.chatManager.moderation;

import java.util.regex.Pattern;

/**
 * Produces a stable message signature for spam detection by removing opaque
 * long tokens appended to the end of a message. The original message is never
 * changed for display, phrase filtering, or logging.
 */
public final class SpamMessageNormalizer {

    /** Twelve-character random suffixes are common enough to be treated as noise. */
    public static final int MIN_TRAILING_TOKEN_LENGTH = 12;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private SpamMessageNormalizer() {
    }

    /**
     * Remove one or more trailing tokens whose length is 12+ characters.
     * Long tokens inside the body are preserved to avoid over-normalizing
     * legitimate text; only standalone suffix tokens are considered noise.
     */
    public static String stripTrailingLongTokens(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String trimmed = message.trim();
        String[] words = WHITESPACE.split(trimmed);
        int end = words.length;
        while (end > 0 && words[end - 1].length() >= MIN_TRAILING_TOKEN_LENGTH) {
            end--;
        }

        if (end == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder(trimmed.length());
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                result.append(' ');
            }
            result.append(words[i]);
        }
        return result.toString();
    }
}
