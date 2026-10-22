package org.me.chatManager.moderation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compares a message against a bounded history of a player's last accepted
 * messages. Word boundaries are preserved while trailing long suffix tokens are filtered.
 * Each player's queue is guarded independently for async chat events.
 */
public final class RecentSimilarityTracker {

    private static final int MIN_COMPARISON_LENGTH = 10;

    private volatile int historyLimit;
    private final Map<UUID, Deque<Entry>> histories = new ConcurrentHashMap<>();

    public RecentSimilarityTracker(int historyLimit) {
        this.historyLimit = Math.max(1, historyLimit);
    }

    /**
     * Returns the strongest similarity in [0, 1] when it meets the threshold,
     * or -1 when no recent accepted message matches.
     */
    public double findSimilarity(UUID uuid, String message, double threshold, long timeWindowMillis) {
        if (uuid == null) {
            return -1.0;
        }

        String comparison = comparisonText(message);
        if (comparison.length() < MIN_COMPARISON_LENGTH) {
            return -1.0;
        }

        double effectiveThreshold = normalizeThreshold(threshold);
        Deque<Entry> history = histories.get(uuid);
        if (history == null) {
            return -1.0;
        }

        long now = System.nanoTime();
        long windowNanos = toNanos(timeWindowMillis);
        synchronized (history) {
            discardExpired(history, now, windowNanos);
            double best = -1.0;
            for (Entry previous : history) {
                int maxLength = Math.max(comparison.length(), previous.comparison.length());
                if (maxLength == 0) {
                    continue;
                }
                // If even a perfect prefix match could not reach the configured
                // score, avoid the O(n*m) edit-distance calculation.
                if (Math.abs(comparison.length() - previous.comparison.length())
                        > maxLength * (1.0 - effectiveThreshold)) {
                    continue;
                }
                double similarity = similarity(comparison, previous.comparison);
                if (similarity >= effectiveThreshold && similarity > best) {
                    best = similarity;
                }
            }
            return best;
        }
    }

    /** Store messages long enough for broader similarity; exact checks handle shorter messages. */
    public void record(UUID uuid, String message, long timeWindowMillis) {
        if (uuid == null) {
            return;
        }

        String comparison = comparisonText(message);
        if (comparison.length() < MIN_COMPARISON_LENGTH) {
            return;
        }

        Deque<Entry> history = histories.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        long now = System.nanoTime();
        long windowNanos = toNanos(timeWindowMillis);
        synchronized (history) {
            discardExpired(history, now, windowNanos);
            history.addLast(new Entry(comparison, now));
            while (history.size() > historyLimit) {
                history.removeFirst();
            }
        }
    }

    /** Reapply the configured per-player history limit after a config reload. */
    public void reconfigure(int historyLimit) {
        this.historyLimit = Math.max(1, historyLimit);
        clear();
    }

    public void remove(UUID uuid) {
        if (uuid != null) {
            histories.remove(uuid);
        }
    }

    public void clear() {
        histories.clear();
    }

    static String comparisonText(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        return SpamMessageNormalizer.stripTrailingLongTokens(
                message.trim().toLowerCase(Locale.ROOT));
    }

    private static double similarity(String first, String second) {
        int maxLength = Math.max(first.length(), second.length());
        if (maxLength == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(first, second);
        return 1.0 - (distance / (double) maxLength);
    }

    private static int levenshteinDistance(String first, String second) {
        // Keep the DP row on the shorter string to cap auxiliary memory.
        if (first.length() < second.length()) {
            String swap = first;
            first = second;
            second = swap;
        }
        int n = first.length();
        int m = second.length();
        if (m == 0) {
            return n;
        }

        int[] current = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            current[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            int previousDiagonal = current[0];
            current[0] = i;
            for (int j = 1; j <= m; j++) {
                int previousRow = current[j];
                current[j] = first.charAt(i - 1) == second.charAt(j - 1)
                        ? previousDiagonal
                        : Math.min(Math.min(current[j - 1], current[j]), previousDiagonal) + 1;
                previousDiagonal = previousRow;
            }
        }
        return current[m];
    }

    private static double normalizeThreshold(double threshold) {
        if (Double.isNaN(threshold) || Double.isInfinite(threshold)) {
            return 0.80;
        }
        return Math.max(0.0, Math.min(1.0, threshold));
    }

    private static long toNanos(long millis) {
        long safeMillis = Math.max(1L, millis);
        if (safeMillis > Long.MAX_VALUE / 1_000_000L) {
            return Long.MAX_VALUE;
        }
        return safeMillis * 1_000_000L;
    }

    private static void discardExpired(Deque<Entry> history, long now, long windowNanos) {
        while (!history.isEmpty() && now - history.peekFirst().createdNanos > windowNanos) {
            history.removeFirst();
        }
    }

    private static final class Entry {
        private final String comparison;
        private final long createdNanos;

        private Entry(String comparison, long createdNanos) {
            this.comparison = comparison;
            this.createdNanos = createdNanos;
        }
    }
}
