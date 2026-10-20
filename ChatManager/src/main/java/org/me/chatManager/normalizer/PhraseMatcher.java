package org.me.chatManager.normalizer;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


/**
 * High-performance moderation phrase matcher.
 *
 * Features:
 * - Unicode normalization
 * - Homoglyph replacement
 * - Leetspeak normalization
 * - Case insensitive matching
 * - Removes punctuation
 * - Removes whitespace
 * - Cached normalized phrases
 *
 * Designed to detect bypass attempts:
 *
 * h3ll0
 * hеllо
 * h.e.l.l.o
 *
 * Separate from spam detection.
 */
public final class PhraseMatcher {


    /**
     * Cached normalized strings.
     */
    private final Map<String, String> normalizedCache =
            new ConcurrentHashMap<>();


    private final HomoglyphNormalizer homoglyphNormalizer =
            new HomoglyphNormalizer();





    /**
     * Normalize text for phrase comparison.
     */
    public String normalize(
            String input
    ) {

        if (input == null ||
                input.isEmpty()) {

            return "";

        }



        String cached =
                normalizedCache.get(input);



        if (cached != null) {

            return cached;

        }





        String normalized =
                homoglyphNormalizer.normalize(
                        input
                );





        StringBuilder output =
                new StringBuilder(
                        normalized.length()
                );





        for (char c :
                normalized.toCharArray()) {


            c =
                    Character.toLowerCase(
                            c
                    );



            /*
             * Remove spaces,
             * punctuation,
             * symbols.
             */
            if (Character.isLetterOrDigit(c)) {

                output.append(c);

            }

        }





        String result =
                output.toString();




        normalizedCache.put(
                input,
                result
        );



        return result;

    }








    /**
     * Check a single phrase.
     */
    public boolean matches(
            String message,
            String phrase
    ) {


        return normalize(message)
                .contains(
                        normalize(phrase)
                );

    }








    /**
     * Returns the first matching phrase.
     */
    public Optional<String> firstMatch(
            String message,
            Collection<String> phrases
    ) {


        String normalizedMessage =
                normalize(message);





        for (String phrase :
                phrases) {


            if (normalizedMessage.contains(
                    normalize(phrase)
            )) {


                return Optional.of(
                        phrase
                );

            }

        }





        return Optional.empty();

    }








    /**
     * Counts matching phrases.
     */
    public int countMatches(
            String message,
            Collection<String> phrases
    ) {


        int count = 0;



        String normalizedMessage =
                normalize(message);





        for (String phrase :
                phrases) {


            if (normalizedMessage.contains(
                    normalize(phrase)
            )) {


                count++;

            }

        }





        return count;

    }








    /**
     * Weighted phrase scoring.
     *
     * Example:
     *
     * advertisement = 5
     * spam link = 10
     */
    public int score(
            String message,
            Map<String, Integer> phrases
    ) {


        int score = 0;



        String normalizedMessage =
                normalize(message);





        for (Map.Entry<String,Integer> entry :
                phrases.entrySet()) {



            if (normalizedMessage.contains(
                    normalize(entry.getKey())
            )) {


                score += entry.getValue();

            }

        }





        return score;

    }








    /**
     * Clears normalization cache.
     */
    public void clearCache() {

        normalizedCache.clear();

    }


}