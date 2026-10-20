package org.me.chatManager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;
import org.me.chatManager.util.ToggleUtil;

public class ToggleChatCommand implements CommandExecutor {

    private final ChatManager plugin;

    public ToggleChatCommand(ChatManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }

        boolean nowDisabled = ToggleUtil.toggle(
                player.getUniqueId(),
                ToggleUtil.Toggle.CHAT
        );

        String message = nowDisabled
                ? plugin.getConfig().getString(
                        "toggles.chat.disabled",
                        "&eYou will no longer see chat messages."
                )
                : plugin.getConfig().getString(
                        "toggles.chat.enabled",
                        "&aYou will now see chat messages."
                );

        player.sendMessage(ChatUtil.color(message));

        return true;
    }
}
