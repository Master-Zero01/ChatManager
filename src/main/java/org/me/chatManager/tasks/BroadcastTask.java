package org.me.chatManager.tasks;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.me.chatManager.ChatManager;
import org.me.chatManager.util.ChatUtil;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class BroadcastTask extends BukkitRunnable {

    private final ChatManager plugin;

    // Tracks position for "SEQUENTIAL" broadcast.mode
    private final AtomicInteger sequentialIndex = new AtomicInteger(0);

    public BroadcastTask(ChatManager plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("broadcast.enabled", true)) return;

        List<String> messages = plugin.getConfig().getStringList("broadcast.messages");
        if (messages.isEmpty()) return;

        String mode = plugin.getConfig().getString("broadcast.mode", "RANDOM")
                .toUpperCase(Locale.ROOT);

        String selectedMessage;

        if (mode.equals("SEQUENTIAL")) {
            int index = sequentialIndex.getAndUpdate(
                    current -> (current + 1) % messages.size()
            );
            selectedMessage = messages.get(index);
        } else {
            selectedMessage = messages.get(
                    ThreadLocalRandom.current().nextInt(messages.size())
            );
        }

        String prefix = ChatUtil.color(
                plugin.getConfig().getString("broadcast.prefix", "")
        );

        String format = plugin.getConfig().getString(
                "broadcast.format", "{prefix}{message}"
        );

        String msg = format
                .replace("{prefix}", prefix)
                .replace("{message}", selectedMessage);

        Bukkit.broadcastMessage(ChatUtil.color(msg));
    }
}
