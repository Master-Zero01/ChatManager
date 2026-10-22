package org.me.chatManager.normalizer;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


/**
 * Converts Unicode homoglyphs, formatting tricks,
 * and invisible characters into comparable text.
 *
 * Used by:
 *
 * - Blacklist
 * - PhraseMatcher
 * - SpamAnalyzer
 * - SimilarityCheck
 */
public final class HomoglyphNormalizer {


    private static final Map<Character, Character> HOMOGLYPHS =
            new HashMap<>();


    private static final Map<Character, Character> LEETSPEAK =
            new HashMap<>();


    static {


        /*
         * Unicode homoglyphs
         */

        add('а','a');
        add('А','A');

        add('е','e');
        add('Е','E');

        add('о','o');
        add('О','O');

        add('р','p');
        add('Р','P');

        add('с','c');
        add('С','C');

        add('х','x');
        add('Х','X');

        add('у','y');
        add('У','Y');

        add('і','i');
        add('І','I');

        add('к','k');
        add('К','K');

        add('м','m');
        add('М','M');

        add('т','t');
        add('Т','T');



        /*
         * Fullwidth characters
         */
        add('０','0');
        add('１','1');
        add('２','2');
        add('３','3');
        add('４','4');
        add('５','5');
        add('６','6');
        add('７','7');
        add('８','8');
        add('９','9');



        /*
         * Common Minecraft bypasses
         */
        add('ⅼ','l');
        add('ℓ','l');
        add('ǀ','l');

        add('ı','i');
        add('ɩ','i');



        /*
         * Aggressive leetspeak.
         *
         * Only used in
         * normalizeForComparison().
         */
        leet('0','o');
        leet('1','i');
        leet('!','i');
        leet('|','i');

        leet('2','z');
        leet('3','e');

        leet('4','a');
        leet('@','a');

        leet('5','s');
        leet('$','s');

        leet('6','g');
        leet('7','t');

        leet('8','b');
        leet('9','g');
    }





    private static void add(
            char unicode,
            char ascii
    ) {

        HOMOGLYPHS.put(
                unicode,
                ascii
        );
    }




    private static void leet(
            char symbol,
            char replacement
    ) {

        LEETSPEAK.put(
                symbol,
                replacement
        );
    }





    /**
     * Normalization preserving spacing.
     */
    public String normalize(
            String input
    ) {


        if (input == null ||
                input.isEmpty()) {

            return "";
        }



        String text =
                Normalizer.normalize(
                        input,
                        Normalizer.Form.NFKD
                );



        text =
                text.replaceAll(
                        "\\p{M}",
                        ""
                );



        text =
                text.replaceAll(
                        "(?i)[§&][0-9A-FK-ORX]",
                        ""
                );



        text =
                text.replaceAll(
                        "[\\u200B-\\u200D\\uFEFF\\u2060]",
                        ""
                );



        StringBuilder out =
                new StringBuilder(
                        text.length()
                );



        for (char c :
                text.toCharArray()) {

            out.append(
                    HOMOGLYPHS.getOrDefault(
                            c,
                            c
                    )
            );
        }



        return out.toString();
    }







    /**
     * Moderation normalization that applies leetspeak mappings while
     * preserving normalized word boundaries for spam and repetition checks.
     */
    public String normalizeForModeration(String input) {
        String text = normalize(input);
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            out.append(LEETSPEAK.getOrDefault(c, c));
        }
        text = out.toString().toLowerCase(Locale.ROOT);
        text = text.replaceAll("[^a-z0-9\\s]", "");
        text = text.replaceAll("\\s+", " ");
        return text.trim();
    }


    /**
     * Aggressive comparison normalization.
     *
     * Used for:
     *
     * - blacklist
     * - spam similarity
     * - bypass detection
     */
    public String normalizeForComparison(
            String input
    ) {


        String text =
                normalize(input);



        StringBuilder out =
                new StringBuilder(
                        text.length()
                );



        for (char c :
                text.toCharArray()) {

            out.append(
                    LEETSPEAK.getOrDefault(
                            c,
                            c
                    )
            );
        }



        text =
                out.toString()
                        .toLowerCase(
                                Locale.ROOT
                        );



        text =
                text.replaceAll(
                        "[^a-z0-9\\s]",
                        ""
                );



        text =
                text.replaceAll(
                        "\\s+",
                        " "
                );



        return text.replace(
                " ",
                ""
        );
    }
}