package org.me.chatManager.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class JoinDateCommand implements TabExecutor {

    private final ChatManager plugin;

    public JoinDateCommand(ChatManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {

        // --- Feature Toggle ---
        if (!plugin.getConfig().getBoolean("joindate.enabled", true)) {
            sender.sendMessage(ChatUtil.color(
                    "&cThe /joindate command is currently disabled."
            ));
            return true;
        }

        // --- Config Values ---
        String messageFormat = plugin.getConfig().getString(
                "joindate.message",
                "&aPlayer &e{player} &afirst joined on: &e{date}"
        );

        String neverJoinedMessage = plugin.getConfig().getString(
                "joindate.never-joined",
                "&e{player} &ahas never joined before."
        );

        String consoleUsageMessage = plugin.getConfig().getString(
                "joindate.console-usage",
                "&cConsole must specify a player name."
        );

        String notFoundMessage = plugin.getConfig().getString(
                "joindate.not-found",
                "&cPlayer &e{player} &cis not found."
        );

        String dateFormatPattern = plugin.getConfig().getString(
                "joindate.date-format",
                "yyyy-MM-dd HH:mm:ss"
        );

        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);

        // --- Resolve Target Player ---
        OfflinePlayer target;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatUtil.color(consoleUsageMessage));
                return true;
            }
            target = player;
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);

            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(ChatUtil.color(
                        notFoundMessage.replace("{player}", args[0])
                ));
                return true;
            }
        }

        // --- Retrieve Join Date ---
        long firstPlayed = target.getFirstPlayed();
        String targetName = target.getName() != null ? target.getName() : "Unknown";

        if (firstPlayed <= 0L) {
            sender.sendMessage(ChatUtil.color(
                    neverJoinedMessage.replace("{player}", targetName)
            ));
            return true;
        }

        String formattedDate = dateFormat.format(new Date(firstPlayed));

        // --- Send Response ---
        sender.sendMessage(ChatUtil.color(
                messageFormat
                        .replace("{player}", targetName)
                        .replace("{date}", formattedDate)
                        .replace("{format}", dateFormatPattern)
        ));

        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length != 1) {
            return List.of();
        }

        String input = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase().startsWith(input)) {
                completions.add(name);
            }
        }

        return completions;
    }
}
