package org.me.chatManager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;

public class StatsCommand implements CommandExecutor {

    private final ChatManager plugin;

    public StatsCommand(ChatManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {

        // --------------------
        // Server Age (Real Time from GameTime)
        // --------------------
        String serverAge;

        try {
            if (Bukkit.getWorlds().isEmpty()) {
                serverAge = "Unknown";
            } else {
                long ticks = Bukkit.getWorlds().get(0).getGameTime();

                long totalSeconds = ticks / 20;
                long days = totalSeconds / 86400;
                long hours = (totalSeconds % 86400) / 3600;

                if (days > 0) {
                    serverAge = days + " day" + (days != 1 ? "s" : "")
                            + ", " + hours + " hour" + (hours != 1 ? "s" : "");
                } else {
                    serverAge = hours + " hour" + (hours != 1 ? "s" : "");
                }
            }
        } catch (Exception e) {
            serverAge = "Unknown";
        }

        // --------------------
        // Total Players
        // --------------------
        int totalPlayers = Bukkit.getOfflinePlayers().length;

        // --------------------
        // Cached World Size (async scanned at startup)
        // --------------------
        long worldSize = plugin.getCachedWorldSize();

        // --------------------
        // Output
        // --------------------
        sender.sendMessage(ChatUtil.color("&6--- 3b3tpe Stats ---"));
        sender.sendMessage(ChatUtil.color("&eServer Age: &f" + serverAge));
        sender.sendMessage(ChatUtil.color("&eTotal Players: &f" + totalPlayers));
        sender.sendMessage(ChatUtil.color("&eWorld Size: &f" + formatFileSize(worldSize)));

        return true;
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "Calculating...";

        double kb = size / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;

        if (gb >= 1) return String.format("%.2f GB", gb);
        if (mb >= 1) return String.format("%.2f MB", mb);
        if (kb >= 1) return String.format("%.2f KB", kb);

        return size + " B";
    }
}