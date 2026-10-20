package org.me.chatManager.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.chatManager.util.ToggleUtil;

/**
 * Enforces per-player toggle preferences:
 *
 * - /togglechat
 *   Player no longer sees chat messages.
 *
 * - /toggleconnectionmsgs
 *   Player no longer sees join/quit messages.
 *
 * - /toggletells
 *   Enforced in WhisperCommand / ReplyCommand.
 */
public class ToggleListener implements Listener {

    /*
     * =========================================================
     * CHAT TOGGLE
     *
     * Removes chat messages for players who disabled chat.
     *
     * =========================================================
     */

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(
            AsyncPlayerChatEvent event
    ) {

        event.getRecipients().removeIf(
                recipient -> ToggleUtil.isDisabled(
                        recipient,
                        ToggleUtil.Toggle.CHAT
                )
        );
    }

    /*
     * =========================================================
     * CONNECTION MESSAGE TOGGLE
     *
     * Removes join messages only for players
     * who disabled connection messages.
     *
     * Does NOT affect the joining player's message.
     *
     * =========================================================
     */

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(
            PlayerJoinEvent event
    ) {

        String message = event.getJoinMessage();

        if (message == null) {
            return;
        }

        /*
         * Prevent Bukkit from broadcasting
         * the message automatically.
         */
        event.setJoinMessage(null);

        /*
         * Send only to players who allow
         * connection messages.
         */
        for (Player online : Bukkit.getOnlinePlayers()) {

            if (!ToggleUtil.isDisabled(
                    online,
                    ToggleUtil.Toggle.CONNECTION_MESSAGES
            )) {

                online.sendMessage(message);

            }
        }

        Bukkit.getConsoleSender().sendMessage(message);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(
            PlayerQuitEvent event
    ) {

        String message = event.getQuitMessage();

        if (message == null) {
            return;
        }

        /*
         * Prevent Bukkit from broadcasting
         * the message automatically.
         */
        event.setQuitMessage(null);

        /*
         * Send only to players who allow
         * connection messages.
         */
        for (Player online : Bukkit.getOnlinePlayers()) {

            if (!ToggleUtil.isDisabled(
                    online,
                    ToggleUtil.Toggle.CONNECTION_MESSAGES
            )) {

                online.sendMessage(message);

            }
        }

        Bukkit.getConsoleSender().sendMessage(message);
    }
}