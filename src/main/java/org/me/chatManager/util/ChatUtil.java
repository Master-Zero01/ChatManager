package org.me.chatManager.util;


import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;



public final class ChatUtil {


    private ChatUtil() {
    }





    private static final Pattern HEX_PATTERN =
            Pattern.compile(
                    "&#([A-Fa-f0-9]{6})"
            );






    // =========================================
    // Color Handling
    // =========================================


    public static String color(
            String text
    ) {


        if (text == null ||
                text.isEmpty()) {

            return "";

        }




        Matcher matcher =
                HEX_PATTERN.matcher(text);



        StringBuffer buffer =
                new StringBuffer();



        while (matcher.find()) {


            String hex =
                    matcher.group(1);



            matcher.appendReplacement(
                    buffer,
                    ChatColor.of(
                            "#" + hex
                    ).toString()
            );

        }



        matcher.appendTail(buffer);



        return org.bukkit.ChatColor
                .translateAlternateColorCodes(
                        '&',
                        buffer.toString()
                );
    }








    public static List<String> colorList(
            List<String> lines
    ) {


        if (lines == null ||
                lines.isEmpty()) {

            return Collections.emptyList();

        }



        return lines.stream()
                .map(ChatUtil::color)
                .collect(Collectors.toList());

    }








    public static String stripColor(
            String message
    ) {


        if (message == null ||
                message.isEmpty()) {

            return "";

        }



        return org.bukkit.ChatColor
                .stripColor(
                        color(message)
                );

    }








    public static String getDirectColorCode(
            String colorName
    ) {


        if (colorName == null ||
                colorName.isEmpty()) {

            return "";

        }



        try {

            return org.bukkit.ChatColor
                    .valueOf(
                            colorName.toUpperCase(
                                    Locale.ROOT
                            )
                    )
                    .toString();


        } catch (IllegalArgumentException ignored) {

            return "";

        }

    }








    public static boolean hasColorCodes(
            String text
    ) {


        if (text == null ||
                text.isEmpty()) {

            return false;

        }



        return text.contains("&")
                ||
                HEX_PATTERN.matcher(text)
                        .find();

    }







    // =========================================
    // Utility Helpers
    // =========================================



    public static boolean isEmpty(
            String text
    ) {

        return text == null ||
                text.trim()
                        .isEmpty();

    }

}