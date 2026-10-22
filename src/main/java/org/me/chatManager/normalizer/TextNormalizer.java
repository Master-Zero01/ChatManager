package org.me.chatManager.normalizer;

import java.text.Normalizer;
import java.util.Locale;


/**
 * Produces canonical representations of chat messages.
 *
 * Used by:
 *
 * - duplicate detection
 * - similarity comparison
 * - phrase matching
 * - moderation bypass detection
 */
public class TextNormalizer {


    private final HomoglyphNormalizer homoglyphNormalizer;



    public TextNormalizer() {

        this.homoglyphNormalizer =
                new HomoglyphNormalizer();

    }







    /**
     * General text normalization.
     *
     * Handles:
     *
     * - Unicode normalization
     * - Homoglyph replacement
     * - Lowercase conversion
     * - Removes punctuation
     * - Preserves spaces
     * - Collapses repeats
     *
     * Example:
     *
     * "Hеllo   WORLD!!!"
     *
     * becomes:
     *
     * "hello world"
     */
    public String normalize(
            String input
    ) {

        if (input == null) {

            return "";

        }



        String text =
                homoglyphNormalizer.normalize(
                        input
                );



        text =
                Normalizer.normalize(
                        text,
                        Normalizer.Form.NFKD
                );



        text =
                text.replaceAll(
                        "\\p{M}",
                        ""
                );



        text =
                text.toLowerCase(
                        Locale.ROOT
                );



        StringBuilder builder =
                new StringBuilder(
                        text.length()
                );



        boolean space = false;



        for (char c :
                text.toCharArray()) {


            if (Character.isWhitespace(c)) {


                if (!space) {

                    builder.append(' ');
                    space = true;

                }


                continue;

            }



            if (Character.isLetterOrDigit(c)) {


                builder.append(c);

                space = false;

            }

        }



        return collapseRepeats(
                builder.toString()
        )
                .trim();

    }









    /**
     * Moderation-specific normalization.
     *
     * Used before:
     *
     * - phrase matching
     * - blacklist detection
     * - spam analysis
     *
     * Defeats:
     *
     * - Alternating caps
     * - Homoglyph bypasses
     * - Unicode tricks
     * - Punctuation bypasses
     * - Excessive spacing
     * - Leetspeak (handled by HomoglyphNormalizer)
     *
     * Examples:
     *
     * jOiN mY sErVeR
     *
     * becomes:
     *
     * join my server
     *
     *
     * dіsc0rd.gg
     *
     * becomes:
     *
     * discord gg
     */
    public String normalizeModeration(
            String input
    ) {

        return collapseRepeats(
                normalizeModerationBase(input)
        )
                .trim();

    }




    /**
     * Same as normalizeModeration, but does NOT collapse
     * repeated characters.
     *
     * Used by:
     *
     * - character flood detection
     * - repeated-sequence detection
     * - template-spam word tracking
     *
     * collapseRepeats() caps any run at 2 characters, which
     * is exactly what those checks need to see UNCOLLAPSED -
     * a 26-character flood ("aaaaaaaaaaaaaaaaaaaaaaaaaa") or
     * a short repeated-letter spam word ("AAA", "BBBB")
     * would otherwise be shrunk down to 2 characters before
     * the check ever runs, making it invisible.
     */
    public String normalizeForRepetition(
            String input
    ) {

        return normalizeModerationBase(input)
                .trim();

    }




    private String normalizeModerationBase(
            String input
    ) {

        if (input == null) {

            return "";

        }



        // The moderation path intentionally uses the aggressive mapping table
        // (0->o, 3->e, etc.) so common leetspeak variants cannot evade phrase
        // filters or spam comparisons. Repetition checks still receive the
        // uncollapsed result, so long runs remain visible.
        String text =
                homoglyphNormalizer.normalizeForModeration(
                        input
                );



        text =
                Normalizer.normalize(
                        text,
                        Normalizer.Form.NFKD
                );



        text =
                text.replaceAll(
                        "\\p{M}",
                        ""
                );



        text =
                text.toLowerCase(
                        Locale.ROOT
                );



        StringBuilder builder =
                new StringBuilder(
                        text.length()
                );



        boolean space = false;



        for (char c :
                text.toCharArray()) {


            if (Character.isWhitespace(c)) {


                if (!space) {

                    builder.append(' ');
                    space = true;

                }


                continue;
            }



            if (Character.isLetterOrDigit(c)) {


                builder.append(c);

                space = false;

            }

        }



        return builder.toString();

    }









    /** Normalize Unicode lookalikes while preserving punctuation for URL detection. */
    public String normalizeForDomainDetection(String input) {
        if (input == null) {
            return "";
        }
        return homoglyphNormalizer.normalize(input).toLowerCase(Locale.ROOT);
    }


    /** Removes spacing differences for duplicate and similarity comparison. */
    public String normalizeComparison(
            String input
    ) {


        return normalizeModeration(
                input
        )
                .replace(
                        " ",
                        ""
                );

    }









    /**
     * Limits repeated characters.
     *
     * Example:
     *
     * yoooooo
     *
     * becomes:
     *
     * yoo
     */
    private String collapseRepeats(
            String input
    ) {


        if (input.length() < 3) {

            return input;

        }



        StringBuilder output =
                new StringBuilder();



        char previous = 0;

        int count = 0;



        for (char c :
                input.toCharArray()) {


            if (c == previous) {

                count++;

            } else {

                previous = c;

                count = 1;

            }



            if (count <= 2) {

                output.append(c);

            }

        }



        return output.toString();

    }

}