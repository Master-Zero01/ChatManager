package org.me.chatManager.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;
import org.me.chatManager.util.ColorCodeSanitizer;
import org.me.chatManager.util.IgnoreUtil;
import org.me.chatManager.util.ToggleUtil;

import java.util.List;
import java.util.stream.Collectors;

public class WhisperCommand implements CommandExecutor, TabCompleter {

    private final ChatManager plugin;

    public WhisperCommand(ChatManager plugin) {
        this.plugin = plugin;
    }

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

        if (args.length < 2) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.usage", "&cUsage: /msg <player> <message>")
            ));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatUtil.color(
                    cfg.getString("whisper.messages.not-online", "&cThat player is not online.")
            ));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatUtil.color("&cYou cannot message yourself."));
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

        String rawMessage = String.join(" ", args).substring(args[0].length() + 1);

        // ==============================
        // Color permission
        // ==============================
        if (cfg.getBoolean("whisper.allow-color", true)
                && player.hasPermission("chatmanager.whisper.color")) {
            rawMessage = ChatUtil.color(rawMessage);
        } else {
            // Remove all supported marker forms before the final message template
            // is colorized, or user-supplied codes would inherit the template's permission.
            rawMessage = ColorCodeSanitizer.strip(rawMessage);
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

        ReplyCommand.setLastMessaged(player, target);
        ReplyCommand.setLastMessaged(target, player);

        return true;
    }

    // ==============================
    // TAB COMPLETION
    // ==============================

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .filter(p -> !IgnoreUtil.isIgnoring(player, p))
                    .filter(p -> !IgnoreUtil.isIgnoring(p, player))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
