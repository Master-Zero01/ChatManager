package org.me.chatManager.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.me.chatManager.ChatManager;

import java.util.Collections;
import java.util.List;

public class SuicideCommand implements CommandExecutor, TabExecutor {


    private final ChatManager plugin;


    public SuicideCommand(
            ChatManager plugin
    ) {

        this.plugin = plugin;

    }




    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {


        if (!(sender instanceof Player player)) {

            sender.sendMessage(
                    ChatColor.RED +
                            "This command can only be used by players."
            );

            return true;

        }



        if (!plugin.getConfig()
                .getBoolean(
                        "suicide.enabled",
                        true
                )) {


            player.sendMessage(
                    ChatColor.RED +
                            "The suicide command is currently disabled."
            );

            return true;

        }



        if (!player.hasPermission(
                "chatmanager.suicide"
        )) {


            player.sendMessage(
                    ChatColor.RED +
                            "You do not have permission to use this command."
            );

            return true;

        }



        player.setHealth(0.0);


        return true;

    }





    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        return Collections.emptyList();

    }

}