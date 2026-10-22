package org.me.chatManager.spam;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, per-player message history for duplicate and adjacent-similarity checks. */
public class DuplicateTracker {

    private final Map<UUID, Deque<TrackedMessage>> messages = new ConcurrentHashMap<>();
    private volatile int maxHistory;
    private volatile long timeWindowMillis;

    public DuplicateTracker(int maxHistory) {
        this(maxHistory, 10_000L);
    }

    public DuplicateTracker(int maxHistory, long timeWindowMillis) {
        this.maxHistory = Math.max(1, maxHistory);
        this.timeWindowMillis = Math.max(1L, timeWindowMillis);
    }

    /** True when the normalized message appears anywhere in the retained history. */
    public boolean isDuplicate(UUID uuid, String normalized) {
        return hasRecentMessage(uuid, normalized);
    }

    public boolean hasRecentMessage(UUID uuid, String normalized) {
        if (uuid == null || normalized == null) {
            return false;
        }
        Deque<TrackedMessage> history = messages.get(uuid);
        if (history == null) {
            return false;
        }
        synchronized (history) {
            pruneExpired(history, System.nanoTime());
            for (TrackedMessage entry : history) {
                if (normalized.equals(entry.message)) {
                    return true;
                }
            }
            return false;
        }
    }

    public void update(UUID uuid, String normalized) {
        if (uuid == null || normalized == null || normalized.isEmpty()) {
            return;
        }
        Deque<TrackedMessage> history = messages.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        synchronized (history) {
            long now = System.nanoTime();
            pruneExpired(history, now);
            history.addLast(new TrackedMessage(normalized, now));
            while (history.size() > maxHistory) {
                history.removeFirst();
            }
        }
    }

    /** A detached copy of unexpired messages, oldest first. */
    public Deque<String> getHistory(UUID uuid) {
        Deque<String> result = new ArrayDeque<>();
        Deque<TrackedMessage> history = uuid == null ? null : messages.get(uuid);
        if (history == null) {
            return result;
        }
        synchronized (history) {
            pruneExpired(history, System.nanoTime());
            for (TrackedMessage entry : history) {
                result.addLast(entry.message);
            }
        }
        return result;
    }

    public String getPrevious(UUID uuid) {
        return getPrevious(uuid, timeWindowMillis);
    }

    /** Return the most recent message only if it is within the requested age limit. */
    public String getPrevious(UUID uuid, long maxAgeMillis) {
        Deque<TrackedMessage> history = uuid == null ? null : messages.get(uuid);
        if (history == null) {
            return null;
        }
        long now = System.nanoTime();
        synchronized (history) {
            pruneExpired(history, now);
            TrackedMessage latest = history.peekLast();
            if (latest == null || now - latest.recordedNanos > millisToNanos(maxAgeMillis)) {
                return null;
            }
            return latest.message;
        }
    }

    /** Reapply config-backed limits without requiring a server restart. */
    public void reconfigure(int maxHistory, long timeWindowMillis) {
        this.maxHistory = Math.max(1, maxHistory);
        this.timeWindowMillis = Math.max(1L, timeWindowMillis);
        clear();
    }

    public void remove(UUID uuid) {
        if (uuid != null) {
            messages.remove(uuid);
        }
    }

    public void clear() {
        messages.clear();
    }

    private void pruneExpired(Deque<TrackedMessage> history, long nowNanos) {
        long windowNanos = millisToNanos(timeWindowMillis);
        while (!history.isEmpty() && nowNanos - history.peekFirst().recordedNanos > windowNanos) {
            history.removeFirst();
        }
    }

    private static long millisToNanos(long millis) {
        return millis > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE
                : Math.max(1L, millis) * 1_000_000L;
    }

    private static final class TrackedMessage {
        private final String message;
        private final long recordedNanos;

        private TrackedMessage(String message, long recordedNanos) {
            this.message = message;
            this.recordedNanos = recordedNanos;
        }
    }
}
