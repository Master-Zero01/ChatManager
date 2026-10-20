package org.me.chatManager.spam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Per-player chat cooldown tracker.
 *
 * Thread safe for AsyncPlayerChatEvent.
 *
 * Used for:
 *
 * - chat cooldown
 * - flood prevention
 * - burst spam protection
 */
public class RateLimiter {


    private final Map<UUID, Long> timestamps =
            new ConcurrentHashMap<>();




    /**
     * Attempts to consume a chat action.
     *
     * @param uuid player UUID
     * @param cooldownMillis cooldown duration
     *
     * @return true if allowed
     */
    public boolean tryAcquire(
            UUID uuid,
            long cooldownMillis
    ) {


        if (uuid == null) {
            return true;
        }



        long now =
                System.currentTimeMillis();



        final boolean[] allowed =
                {false};



        timestamps.compute(
                uuid,
                (key, last) -> {


                    if (last == null
                            ||
                            now - last >= cooldownMillis) {


                        allowed[0] = true;

                        return now;
                    }


                    return last;
                }
        );



        return allowed[0];
    }







    /**
     * Convenience method using seconds.
     */
    public boolean tryAcquireSeconds(
            UUID uuid,
            int seconds
    ) {


        return tryAcquire(
                uuid,
                seconds * 1000L
        );
    }







    /**
     * Returns remaining cooldown in milliseconds.
     */
    public long getRemaining(
            UUID uuid,
            long cooldownMillis
    ) {


        if (uuid == null) {
            return 0;
        }



        long last =
                timestamps.getOrDefault(
                        uuid,
                        0L
                );



        long remaining =
                cooldownMillis -
                        (
                                System.currentTimeMillis()
                                        -
                                        last
                        );



        return Math.max(
                0,
                remaining
        );
    }







    /**
     * Returns remaining cooldown seconds.
     */
    public long getRemainingSeconds(
            UUID uuid,
            int seconds
    ) {


        long millis =
                getRemaining(
                        uuid,
                        seconds * 1000L
                );



        return (millis + 999) / 1000;
    }







    /**
     * Removes player cooldown data.
     *
     * Used when players leave
     * to prevent memory buildup.
     */
    public void remove(
            UUID uuid
    ) {


        if (uuid != null) {

            timestamps.remove(uuid);

        }
    }







    /**
     * Clears all cooldown data.
     */
    public void clear() {

        timestamps.clear();

    }
}