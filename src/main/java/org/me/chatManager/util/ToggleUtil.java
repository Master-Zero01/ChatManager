package org.me.chatManager.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player toggle preferences.
 *
 * Backs:
 * - /togglechat
 * - /toggletells
 * - /toggleconnectionmsgs
 */
public final class ToggleUtil {

    public enum Toggle {

        CHAT,

        TELLS,

        CONNECTION_MESSAGES

    }

    /*
     * Player UUID -> disabled toggles
     */
    private static final Map<UUID, Set<Toggle>> DISABLED =
            new ConcurrentHashMap<>();

    private ToggleUtil() {
        throw new IllegalStateException("Utility class");
    }

    // ==============================
    // Toggle
    // ==============================

    /**
     * Flips a toggle for a player.
     *
     * @return true if the toggle is now disabled,
     *         false if it is now enabled.
     */
    public static boolean toggle(
            @NotNull UUID uuid,
            @NotNull Toggle toggle
    ) {

        Set<Toggle> set =
                DISABLED.computeIfAbsent(
                        uuid,
                        key -> EnumSet.noneOf(Toggle.class)
                );

        synchronized (set) {

            if (set.contains(toggle)) {

                set.remove(toggle);

                if (set.isEmpty()) {
                    DISABLED.remove(uuid);
                }

                return false;
            }

            set.add(toggle);

            return true;
        }
    }

    // ==============================
    // Checks
    // ==============================

    /**
     * Checks if a player has a toggle disabled.
     */
    public static boolean isDisabled(
            @NotNull UUID uuid,
            @NotNull Toggle toggle
    ) {

        Set<Toggle> set = DISABLED.get(uuid);

        if (set == null) {
            return false;
        }

        synchronized (set) {
            return set.contains(toggle);
        }
    }

    /**
     * Checks if a player has a toggle disabled.
     */
    public static boolean isDisabled(
            @NotNull Player player,
            @NotNull Toggle toggle
    ) {

        return isDisabled(
                player.getUniqueId(),
                toggle
        );
    }

    /**
     * Opposite helper for readability.
     */
    public static boolean isEnabled(
            @NotNull UUID uuid,
            @NotNull Toggle toggle
    ) {

        return !isDisabled(uuid, toggle);
    }

    /**
     * Opposite helper for readability.
     */
    public static boolean isEnabled(
            @NotNull Player player,
            @NotNull Toggle toggle
    ) {

        return !isDisabled(player, toggle);
    }

    // ==============================
    // Specific Helpers
    // ==============================

    public static boolean hasChatDisabled(
            @NotNull UUID uuid
    ) {

        return isDisabled(
                uuid,
                Toggle.CHAT
        );
    }

    public static boolean hasTellDisabled(
            @NotNull UUID uuid
    ) {

        return isDisabled(
                uuid,
                Toggle.TELLS
        );
    }

    public static boolean hasConnectionMessagesDisabled(
            @NotNull UUID uuid
    ) {

        return isDisabled(
                uuid,
                Toggle.CONNECTION_MESSAGES
        );
    }

    // ==============================
    // Cleanup
    // ==============================

    /**
     * Removes all preferences for a player.
     */
    public static void clear(
            @NotNull UUID uuid
    ) {

        DISABLED.remove(uuid);
    }

    /**
     * Clears all stored toggles.
     */
    public static void clearAll() {

        DISABLED.clear();
    }
}