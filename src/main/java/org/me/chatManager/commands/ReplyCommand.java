package org.me.chatManager.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;
import org.me.chatManager.util.IgnoreUtil;
import org.me.chatManager.util.ToggleUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReplyCommand implements CommandExecutor {

    private static final Map<UUID, UUID> lastMessaged = new HashMap<>();
    private final ChatManager plugin;

    public ReplyCommand(ChatManager plugin) {
        this.plugin = plugin;
    }

    // ==============================
    // Tracking
    // ==============================

    public static void setLastMessaged(Player sender, Player target) {
        lastMessaged.put(sender.getUniqueId(), target.getUniqueId());
    }

    // ==============================
    // Command
    // ==============================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtil.color("&cOnly players can use this command."));
            return true;
        }

        FileConfiguration cfg = plugin.getConfig();

        // ==============================
        // Enabled check
        // ==============================
        if (!cfg.getBoolean("whisper.enabled", true)) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.disabled", "&cPrivate messages are disabled.")
            ));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.reply-usage", "&cUsage: /reply <message>")
            ));
            return true;
        }

        UUID targetId = lastMessaged.get(player.getUniqueId());
        if (targetId == null) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.no-reply-target", "&cYou have no one to reply to.")
            ));
            return true;
        }

        Player target = plugin.getServer().getPlayer(targetId);
        if (target == null) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.not-online", "&cThat player is no longer online.")
            ));
            return true;
        }

        // ==============================
        // Ignore enforcement
        // ==============================
        if (IgnoreUtil.isIgnoring(player, target)) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.you-ignore", "&cYou are ignoring that player.")
            ));
            return true;
        }

        if (IgnoreUtil.isIgnoring(target, player)) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.they-ignore", "&cThat player is ignoring you.")
            ));
            return true;
        }

        // ==============================
        // Tells toggle enforcement
        // ==============================
        if (ToggleUtil.isDisabled(target, ToggleUtil.Toggle.TELLS)) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.tells-disabled", "&cThat player has private messages disabled.")
            ));
            return true;
        }

        String rawMessage = String.join(" ", args);

        // ==============================
        // Color permission
        // ==============================
        if (cfg.getBoolean("whisper.allow-color", true)) {
            if (player.hasPermission("chatmanager.whisper.color")) {
                rawMessage = ChatUtil.color(rawMessage);
            } else {
                rawMessage = rawMessage.replaceAll("&(?i)[0-9a-fk-or]", "");
            }
        }

        if (ChatUtil.stripColor(rawMessage).trim().isEmpty()) {
            return true;
        }

        // ==============================
        // Formatting
        // ==============================
        String senderFormat = cfg.getString(
                "whisper.format.sender",
                "&7[To &e{target}&7] &f{message}"
        );

        String targetFormat = cfg.getString(
                "whisper.format.target",
                "&7[From &e{sender}&7] &f{message}"
        );

        senderFormat = ChatUtil.color(
                senderFormat
                        .replace("{target}", target.getName())
                        .replace("{sender}", player.getName())
                        .replace("{message}", rawMessage)
        );

        targetFormat = ChatUtil.color(
                targetFormat
                        .replace("{sender}", player.getName())
                        .replace("{target}", target.getName())
                        .replace("{message}", rawMessage)
        );

        player.sendMessage(senderFormat);
        target.sendMessage(targetFormat);

        setLastMessaged(player, target);
        setLastMessaged(target, player);

        return true;
    }
}
