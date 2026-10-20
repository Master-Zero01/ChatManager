package org.me.chatManager;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.me.chatManager.commands.*;
import org.me.chatManager.listeners.ChatListener;
import org.me.chatManager.listeners.ToggleListener;
import org.me.chatManager.moderation.ChatPipeline;
import org.me.chatManager.normalizer.PhraseMatcher;
import org.me.chatManager.normalizer.TextNormalizer;
import org.me.chatManager.spam.SimilarityEngine;
import org.me.chatManager.tasks.BroadcastTask;
import org.me.chatManager.util.IgnoreUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;


public class ChatManager extends JavaPlugin {


    private static ChatManager instance;

    private BroadcastTask broadcastTask;

    private File ignoreFile;

    private YamlConfiguration ignoreConfig;

    private volatile long cachedWorldSize = 0;

    private TextNormalizer textNormalizer;

    private PhraseMatcher phraseMatcher;

    private SimilarityEngine similarityEngine;

    private ChatPipeline chatPipeline;





    @Override
    public void onEnable() {


        instance = this;


        saveDefaultConfig();


        loadIgnores();


        textNormalizer =
                new TextNormalizer();



        phraseMatcher =
                new PhraseMatcher();



        similarityEngine =
                new SimilarityEngine();



        chatPipeline =
                new ChatPipeline(
                        this,
                        textNormalizer,
                        phraseMatcher,
                        similarityEngine
                );




        getServer()
                .getPluginManager()
                .registerEvents(
                        new ChatListener(
                                this,
                                chatPipeline
                        ),
                        this
                );



        getServer()
                .getPluginManager()
                .registerEvents(
                        new ToggleListener(),
                        this
                );






        registerCommand(
                "broadcast",
                new BroadcastCommand(this)
        );


        registerCommand(
                "chatmanager",
                new ReloadCommand(this)
        );


        registerCommand(
                "suicide",
                new SuicideCommand(this)
        );



        JoinDateCommand joinDate =
                new JoinDateCommand(this);


        registerCommandWithTab(
                "joindate",
                joinDate
        );



        registerCommand(
                "msg",
                new WhisperCommand(this)
        );


        registerCommand(
                "reply",
                new ReplyCommand(this)
        );



        registerCommandWithTab(
                "ignore",
                new IgnoreCommand(this)
        );


        registerCommandWithTab(
                "unignore",
                new UnignoreCommand(this)
        );


        registerCommand(
                "ignorelist",
                new IgnoreListCommand(this)
        );


        registerCommand(
                "stats",
                new StatsCommand(this)
        );


        registerCommand(
                "togglechat",
                new ToggleChatCommand(this)
        );


        registerCommand(
                "toggletells",
                new ToggleTellsCommand(this)
        );


        registerCommand(
                "toggleconnectionmsgs",
                new ToggleConnectionMsgsCommand(this)
        );






        startBroadcastTask();


        scanWorldSizeAsync();



        Bukkit.getScheduler()
                .runTaskTimerAsynchronously(
                        this,
                        this::scanWorldSizeAsync,
                        30L * 60L * 20L,
                        30L * 60L * 20L
                );



        getLogger()
                .info("ChatManager enabled.");

    }

    @Override
    public void onDisable() {


        saveIgnores();


        if (chatPipeline != null) {

            chatPipeline.shutdown();

        }


        stopBroadcastTask();


        getLogger()
                .info("ChatManager disabled.");

    }








    public TextNormalizer getTextNormalizer() {

        return textNormalizer;

    }





    public PhraseMatcher getPhraseMatcher() {

        return phraseMatcher;

    }





    public SimilarityEngine getSimilarityEngine() {

        return similarityEngine;

    }





    public ChatPipeline getChatPipeline() {

        return chatPipeline;

    }





    public long getCachedWorldSize() {

        return cachedWorldSize;

    }









    private void scanWorldSizeAsync() {


        AtomicLong total =
                new AtomicLong();



        List<File> worlds =
                Arrays.asList(
                        new File(
                                Bukkit.getWorldContainer(),
                                "world"
                        ),
                        new File(
                                Bukkit.getWorldContainer(),
                                "world_nether"
                        ),
                        new File(
                                Bukkit.getWorldContainer(),
                                "world_the_end"
                        )
                );



        for (File folder : worlds) {


            if (!folder.exists()) {
                continue;
            }



            try {


                Files.walkFileTree(
                        folder.toPath(),
                        new SimpleFileVisitor<>() {


                            @Override
                            public FileVisitResult visitFile(
                                    Path file,
                                    BasicFileAttributes attrs
                            ) {


                                total.addAndGet(
                                        attrs.size()
                                );


                                return FileVisitResult.CONTINUE;
                            }

                        }
                );


            } catch (IOException e) {


                getLogger()
                        .warning(
                                "Failed scanning "
                                        +
                                        folder.getName()
                        );

            }

        }



        cachedWorldSize =
                total.get();

    }









    private void loadIgnores() {


        ignoreFile =
                new File(
                        getDataFolder(),
                        "ignores.yml"
                );



        if (!ignoreFile.exists()) {


            ignoreFile.getParentFile()
                    .mkdirs();



            ignoreConfig =
                    new YamlConfiguration();


            return;

        }



        ignoreConfig =
                YamlConfiguration
                        .loadConfiguration(
                                ignoreFile
                        );



        Map<UUID, Set<UUID>> loaded =
                new HashMap<>();



        for (String key :
                ignoreConfig.getKeys(false)) {


            try {


                UUID uuid =
                        UUID.fromString(key);



                Set<UUID> ignored =
                        new HashSet<>();



                for (String id :
                        ignoreConfig.getStringList(key)) {


                    ignored.add(
                            UUID.fromString(id)
                    );

                }



                loaded.put(
                        uuid,
                        ignored
                );



            } catch (Exception ignored) {

            }

        }



        IgnoreUtil.setAll(
                loaded
        );

    }









    private void saveIgnores() {


        if (ignoreFile == null) {

            return;

        }



        ignoreConfig =
                new YamlConfiguration();



        for (Map.Entry<UUID, Set<UUID>> entry :
                IgnoreUtil.getAll().entrySet()) {


            ignoreConfig.set(
                    entry.getKey().toString(),
                    entry.getValue()
                            .stream()
                            .map(UUID::toString)
                            .toList()
            );

        }




        try {


            ignoreConfig.save(
                    ignoreFile
            );


        } catch (IOException ignored) {

        }

    }









    public void startBroadcastTask() {


        stopBroadcastTask();



        if (!getConfig()
                .getBoolean(
                        "broadcast.enabled",
                        true
                )) {

            return;

        }



        broadcastTask =
                new BroadcastTask(this);



        broadcastTask.runTaskTimer(
                this,
                0L,
                getConfig()
                        .getInt(
                                "broadcast.interval-seconds",
                                60
                        )
                        * 20L
        );

    }










    public void stopBroadcastTask() {


        if (broadcastTask != null) {


            broadcastTask.cancel();


            broadcastTask = null;

        }

    }










    public static ChatManager getInstance() {

        return instance;

    }










    private void registerCommand(
            String name,
            CommandExecutor executor
    ) {


        Objects.requireNonNull(
                        getCommand(name)
                )
                .setExecutor(executor);

    }










    private void registerCommandWithTab(
            String name,
            TabExecutor executor
    ) {


        Objects.requireNonNull(
                        getCommand(name)
                )
                .setExecutor(executor);



        getCommand(name)
                .setTabCompleter(executor);

    }

}