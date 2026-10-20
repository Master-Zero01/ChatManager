package org.me.chatManager.spam;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Tracks previous messages per player.
 *
 * Used by:
 *
 * - duplicate detection
 * - similarity checks
 * - repetition detection
 * - spam history
 */
public class DuplicateTracker {


    private final Map<UUID, Deque<String>> messages =
            new ConcurrentHashMap<>();


    private final int maxHistory;



    public DuplicateTracker(
            int maxHistory
    ) {

        this.maxHistory =
                Math.max(
                        1,
                        maxHistory
                );
    }




    /**
     * Checks if message matches
     * the latest message.
     */
    public boolean isDuplicate(
            UUID uuid,
            String normalized
    ) {

        if (uuid == null ||
                normalized == null ||
                normalized.isEmpty()) {

            return false;
        }


        Deque<String> history =
                messages.get(uuid);


        if (history == null ||
                history.isEmpty()) {

            return false;
        }


        synchronized (history) {

            return normalized.equals(
                    history.peekLast()
            );
        }
    }





    /**
     * Checks whether this player
     * recently sent this message.
     */
    public boolean hasRecentMessage(
            UUID uuid,
            String normalized
    ) {

        if (uuid == null ||
                normalized == null) {

            return false;
        }


        Deque<String> history =
                messages.get(uuid);


        if (history == null) {
            return false;
        }


        synchronized (history) {

            return history.contains(normalized);
        }
    }





    /**
     * Adds a normalized message.
     */
    public void update(
            UUID uuid,
            String normalized
    ) {

        if (uuid == null ||
                normalized == null ||
                normalized.isEmpty()) {

            return;
        }


        Deque<String> history =
                messages.computeIfAbsent(
                        uuid,
                        k -> new ArrayDeque<>()
                );


        synchronized (history) {


            history.addLast(normalized);


            while (history.size() > maxHistory) {

                history.removeFirst();

            }
        }
    }





    /**
     * Returns message history copy.
     *
     * Used by similarity checks.
     */
    public Deque<String> getHistory(
            UUID uuid
    ) {

        Deque<String> history =
                messages.get(uuid);


        if (history == null) {

            return new ArrayDeque<>();

        }


        synchronized (history) {

            return new ArrayDeque<>(history);

        }
    }





    /**
     * Returns latest message.
     */
    public String getPrevious(
            UUID uuid
    ) {

        Deque<String> history =
                messages.get(uuid);


        if (history == null ||
                history.isEmpty()) {

            return null;
        }


        synchronized (history) {

            return history.peekLast();

        }
    }





    /**
     * Removes player data.
     */
    public void remove(
            UUID uuid
    ) {

        if (uuid != null) {

            messages.remove(uuid);

        }
    }





    /**
     * Clears all tracking.
     */
    public void clear() {

        messages.clear();

    }
}