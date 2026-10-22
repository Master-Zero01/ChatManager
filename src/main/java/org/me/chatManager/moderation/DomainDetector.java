package org.me.chatManager.moderation;

import java.util.regex.Pattern;

/** Detects domain-shaped text without relying on a stale hard-coded TLD list. */
public final class DomainDetector {

    /*
     * Any alphabetic TLD-like suffix is accepted so newer gTLDs are not missed.
     * Literal dots must be adjacent to labels; whitespace is accepted only for
     * verbalized separators ("dot" / "d0t"). This avoids treating ordinary
     * sentence punctuation like "That is fine. Me too" as a link.
     */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.|\\s+dot\\s+|\\s+d0t\\s+)[a-z]{2,63}\\b");

    private DomainDetector() {
    }

    /** Expects Unicode-normalized input with punctuation preserved. */
    public static boolean containsDomainLike(String normalizedMessage) {
        return normalizedMessage != null && DOMAIN_PATTERN.matcher(normalizedMessage).find();
    }
}
