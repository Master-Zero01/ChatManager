package org.me.chatManager.commands;

import org.me.chatManager.ChatManager;
import org.me.chatManager.util.IgnoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class IgnoreListCommand implements CommandExecutor {

    public IgnoreListCommand(ChatManager chatManager) {
        // no-op
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Set<UUID> ignored = IgnoreUtil.getIgnored(player.getUniqueId());

        if (ignored.isEmpty()) {
            player.sendMessage("§eYou are not ignoring anyone.");
            return true;
        }

        String names = ignored.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(p -> p.getName() != null ? p.getName() : "Unknown")
                .collect(Collectors.joining(", "));

        player.sendMessage("§aIgnored players (§f" + ignored.size() + "§a): §6" + names);
        return true;
    }
}
