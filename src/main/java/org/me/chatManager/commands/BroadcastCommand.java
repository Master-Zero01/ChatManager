package org.me.chatManager.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.me.chatManager.ChatManager;

public final class BroadcastCommand implements CommandExecutor {

    private final ChatManager plugin;

    public BroadcastCommand(ChatManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        // --- Config Values ---
        String permission = plugin.getConfig().getString(
                "broadcast.permission",
                "chatmanager.broadcast"
        );

        String noPermissionMessage = colorize(
                plugin.getConfig().getString(
                        "messages.no-permission",
                        "&cYou do not have permission to use this command."
                )
        );

        String usageMessage = colorize(
                plugin.getConfig().getString(
                        "messages.usage-broadcast",
                        "&cUsage: /broadcast <message>"
                )
        );

        String format = plugin.getConfig().getString(
                "broadcast.format",
                "&6[Broadcast]&r {message}"
        );

        boolean sendToConsole = plugin.getConfig().getBoolean(
                "broadcast.send-to-console",
                true
        );

        // --- Permission Check (console always allowed) ---
        if (!sender.hasPermission(permission) && sender != Bukkit.getConsoleSender()) {
            sender.sendMessage(noPermissionMessage);
            return true;
        }

        // --- Validate Message ---
        if (args.length == 0) {
            sender.sendMessage(usageMessage);
            return true;
        }

        // --- Build Broadcast ---
        String message = String.join(" ", args);
        String formattedMessage = colorize(
                format.replace("{message}", message)
        );

        // --- Send to Players ---
        Bukkit.getOnlinePlayers().forEach(player ->
                player.sendMessage(formattedMessage)
        );

        // --- Optional Console Output ---
        if (sendToConsole) {
            Bukkit.getConsoleSender().sendMessage(formattedMessage);
        }

        return true;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
