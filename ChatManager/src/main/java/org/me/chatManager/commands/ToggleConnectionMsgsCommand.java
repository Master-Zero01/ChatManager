package org.me.chatManager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;
import org.me.chatManager.util.ToggleUtil;

public class ToggleConnectionMsgsCommand implements CommandExecutor {

    private final ChatManager plugin;

    public ToggleConnectionMsgsCommand(ChatManager plugin) {
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
                ToggleUtil.Toggle.CONNECTION_MESSAGES
        );

        String message = nowDisabled
                ? plugin.getConfig().getString(
                        "toggles.connection.disabled",
                        "&eYou will no longer see join/quit messages."
                )
                : plugin.getConfig().getString(
                        "toggles.connection.enabled",
                        "&aYou will now see join/quit messages."
                );

        player.sendMessage(ChatUtil.color(message));

        return true;
    }
}
