package org.me.chatManager.util;


import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;



public final class IgnoreUtil {


    /*
     * Player UUID -> ignored UUIDs
     */
    private static final Map<UUID, Set<UUID>> IGNORES =
            new ConcurrentHashMap<>();



    private IgnoreUtil() {
    }







    // ==============================
    // Ignore
    // ==============================


    public static void ignore(
            @NotNull Player player,
            @NotNull Player target
    ) {

        ignore(
                player.getUniqueId(),
                target.getUniqueId()
        );

    }






    public static void ignore(
            @NotNull UUID playerId,
            @NotNull UUID targetId
    ) {


        IGNORES
                .computeIfAbsent(
                        playerId,
                        key -> ConcurrentHashMap.newKeySet()
                )
                .add(targetId);

    }







    // ==============================
    // Unignore
    // ==============================


    public static void unignore(
            @NotNull Player player,
            @NotNull Player target
    ) {

        unignore(
                player.getUniqueId(),
                target.getUniqueId()
        );

    }






    public static void unignore(
            @NotNull UUID playerId,
            @NotNull UUID targetId
    ) {


        Set<UUID> ignored =
                IGNORES.get(playerId);



        if (ignored == null) {
            return;
        }



        ignored.remove(targetId);



        if (ignored.isEmpty()) {

            IGNORES.remove(playerId);

        }

    }







    // ==============================
    // Checks
    // ==============================


    public static boolean isIgnoring(
            @NotNull Player player,
            @NotNull Player target
    ) {


        return isIgnoring(
                player.getUniqueId(),
                target.getUniqueId()
        );

    }







    public static boolean isIgnoring(
            @NotNull UUID playerId,
            @NotNull UUID targetId
    ) {


        Set<UUID> ignored =
                IGNORES.get(playerId);



        return ignored != null
                &&
                ignored.contains(targetId);

    }







    // ==============================
    // Accessors
    // ==============================


    public static Set<UUID> getIgnored(
            @NotNull Player player
    ) {

        return getIgnored(
                player.getUniqueId()
        );

    }






    public static Set<UUID> getIgnored(
            @NotNull UUID playerId
    ) {


        Set<UUID> ignored =
                IGNORES.get(playerId);



        if (ignored == null) {

            return Collections.emptySet();

        }



        return Collections.unmodifiableSet(
                new HashSet<>(ignored)
        );

    }







    // ==============================
    // Cleanup
    // ==============================


    /**
     * Remove player and remove them
     * from other ignore lists.
     */
    public static void clear(
            @NotNull UUID playerId
    ) {


        IGNORES.remove(playerId);



        for (Set<UUID> ignored :
                IGNORES.values()) {


            ignored.remove(playerId);

        }

    }







    // ==============================
    // Persistence
    // ==============================


    public static Map<UUID, Set<UUID>> getAll() {


        Map<UUID, Set<UUID>> copy =
                new HashMap<>();



        IGNORES.forEach(
                (uuid, ignored) ->
                        copy.put(
                                uuid,
                                new HashSet<>(ignored)
                        )
        );



        return copy;

    }







    public static void setAll(
            Map<UUID, Set<UUID>> data
    ) {


        IGNORES.clear();



        if (data == null) {
            return;
        }



        data.forEach(
                (uuid, ignored) ->

                        IGNORES.put(
                                uuid,
                                ConcurrentHashMap.newKeySet(
                                )
                        )
        );



        data.forEach(
                (uuid, ignored) ->

                        IGNORES.get(uuid)
                                .addAll(ignored)

        );

    }

}