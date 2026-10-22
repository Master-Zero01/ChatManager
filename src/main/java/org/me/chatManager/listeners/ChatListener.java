package org.me.chatManager.listeners;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.chatManager.ChatManager;
import org.me.chatManager.moderation.ChatPipeline;
import org.me.chatManager.moderation.ModerationResult;
import org.me.chatManager.util.ChatUtil;
import org.me.chatManager.util.ColorCodeSanitizer;
import org.me.chatManager.util.IgnoreUtil;


public class ChatListener implements Listener {


    private final ChatManager plugin;

    private final ChatPipeline pipeline;




    public ChatListener(
            ChatManager plugin,
            ChatPipeline pipeline
    ) {

        this.plugin = plugin;

        this.pipeline = pipeline;

    }









    /*
     * =========================================================
     * PRE PROCESS
     *
     * Unicode
     * Colors
     *
     * Phrase matching moved into ChatPipeline.
     *
     * =========================================================
     */


    @EventHandler(priority = EventPriority.LOWEST)
    public void preprocess(
            AsyncPlayerChatEvent event
    ) {


        if (event.isCancelled()) {

            return;

        }



        Player player =
                event.getPlayer();



        FileConfiguration cfg =
                plugin.getConfig();







        if (!cfg.getBoolean(
                "chat.enabled",
                true
        )) {


            event.setCancelled(true);

            return;

        }








        String message =
                event.getMessage();






        if (message == null ||
                message.isBlank()) {


            event.setCancelled(true);

            return;

        }








        /*
         * Unicode blocking
         */
        if (cfg.getBoolean(
                "chat.block-unicode",
                false
        )
                &&
                containsUnicode(message)) {


            deny(
                    player,
                    "&cYour message contains unsupported characters."
            );


            event.setCancelled(true);

            return;

        }










        /*
         * Color handling
         */
        boolean colorAllowed = cfg.getBoolean("chat.allow-color", true)
                && player.hasPermission("chatmanager.color");
        if (colorAllowed) {
            message = ChatUtil.color(message);
        } else {
            // Strip both legacy and &#RRGGBB codes when formatting is disabled
            // or the sender lacks permission; otherwise hex attempts can leak
            // into chat as literal text.
            message = ColorCodeSanitizer.strip(message);
        }









        if (ChatUtil.stripColor(message)
                .trim()
                .isEmpty()) {


            event.setCancelled(true);

            return;

        }






        event.setMessage(message);

    }













    /*
     * =========================================================
     * MODERATION PIPELINE
     * =========================================================
     */


    @EventHandler(priority = EventPriority.NORMAL)
    public void moderation(
            AsyncPlayerChatEvent event
    ) {


        if (event.isCancelled()) {

            return;

        }







        ModerationResult result =
                pipeline.process(
                        event.getPlayer(),
                        event.getMessage()
                );






        if (!result.isAllowed()) {


            event.setCancelled(true);



            event.getPlayer()
                    .sendMessage(
                            ChatUtil.color(
                                    result.getMessage()
                            )
                    );



            notifyStaff(
                    event.getPlayer(),
                    result
            );

        }

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pipeline.removePlayer(event.getPlayer().getUniqueId());
    }


    /*
     * =========================================================
     * IGNORE FILTER
     *
     * Prevent ignored players
     * from seeing chat.
     *
     * =========================================================
     */


    @EventHandler(priority = EventPriority.HIGH)
    public void ignore(
            AsyncPlayerChatEvent event
    ) {


        Player sender =
                event.getPlayer();




        event.getRecipients()
                .removeIf(
                        recipient ->
                                IgnoreUtil.isIgnoring(
                                        recipient,
                                        sender
                                )
                );

    }












    /*
     * =========================================================
     * LOGGING
     * =========================================================
     */


    @EventHandler(priority = EventPriority.MONITOR)
    public void log(
            AsyncPlayerChatEvent event
    ) {


        if (event.isCancelled()) {

            return;

        }








        if (!plugin.getConfig()
                .getBoolean(
                        "chat.log-to-console",
                        true
                )) {

            return;

        }







        plugin.getLogger()
                .info(
                        "[CHAT] <"
                                +
                                event.getPlayer()
                                        .getName()
                                +
                                "> "
                                +
                                ChatUtil.stripColor(
                                        event.getMessage()
                                )
                );

    }












    /*
     * =========================================================
     * HELPERS
     * =========================================================
     */


    private void deny(
            Player player,
            String message
    ) {


        player.sendMessage(
                ChatUtil.color(
                        message
                )
        );

    }









    private boolean containsUnicode(
            String text
    ) {


        for (char c :
                text.toCharArray()) {


            if (c > 127) {

                return true;

            }

        }


        return false;

    }












    private void notifyStaff(
            Player offender,
            ModerationResult result
    ) {



        FileConfiguration cfg =
                plugin.getConfig();






        if (!cfg.getBoolean(
                "chat.notify-staff",
                false
        )) {

            return;

        }








        String permission =
                cfg.getString(
                        "chat.staff-notify-permission",
                        "chatmanager.notify"
                );








        String message =
                cfg.getString(
                        "chat.staff-notify-message",
                        "&e[Staff] {player} triggered chat moderation."
                );








        message =
                message.replace(
                        "{player}",
                        offender.getName()
                );






        message =
                message.replace(
                        "{reason}",
                        result.getReason()
                );







        message =
                message.replace(
                        "{similarity}",
                        String.format(
                                "%.1f",
                                result.getSimilarity()
                        )
                );









        for (Player staff :
                Bukkit.getOnlinePlayers()) {



            if (staff.hasPermission(permission)) {


                staff.sendMessage(
                        ChatUtil.color(
                                message
                        )
                );

            }

        }

    }


}