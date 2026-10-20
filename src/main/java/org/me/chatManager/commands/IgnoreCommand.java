package org.me.chatManager.commands;

import org.me.chatManager.ChatManager;
import org.me.chatManager.util.IgnoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class IgnoreCommand implements TabExecutor {

    public IgnoreCommand(ChatManager chatManager) {
        // no-op
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /ignore <player>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage("§cThat player is not online.");
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§cYou cannot ignore yourself.");
            return true;
        }

        if (IgnoreUtil.isIgnoring(player, target)) {
            player.sendMessage("§eYou are already ignoring §6" + target.getName() + "§e.");
            return true;
        }

        IgnoreUtil.ignore(player, target);
        player.sendMessage("§aYou are now ignoring §6" + target.getName() + "§a.");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }

        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> !IgnoreUtil.isIgnoring(player, p))
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
    }
}
