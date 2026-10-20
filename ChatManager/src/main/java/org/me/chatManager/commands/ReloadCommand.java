package org.me.chatManager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;

public class ReloadCommand implements CommandExecutor {

    private final ChatManager plugin;

    public ReloadCommand(ChatManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        String permission = "chatmanager.reload";

        if (sender instanceof Player player && !player.hasPermission(permission)) {
            player.sendMessage(ChatUtil.color("&cYou do not have permission to execute this command."));
            return true;
        }

        plugin.reloadConfig();

        // Restart tasks that depend on the config
        plugin.startBroadcastTask();

        sender.sendMessage(ChatUtil.color("&aChatManager configuration reloaded successfully!"));
        plugin.getLogger().info("ChatManager configuration reloaded by " + sender.getName());

        return true;
    }
}