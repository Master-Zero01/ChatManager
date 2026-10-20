package org.me.chatManager.spam;


/**
 * Calculates message similarity.
 *
 * Uses optimized Levenshtein distance.
 *
 * Expected input:
 *
 * TextNormalizer.normalizeComparison()
 *
 * Returns percentage:
 *
 * 100 = identical
 * 0   = completely different
 */
public class SimilarityEngine {


    private static final int MIN_LENGTH = 3;

    private static final int MAX_LENGTH = 512;



    /**
     * Compares two messages.
     *
     * @return similarity percentage from 0-100
     */
    public double compare(
            String first,
            String second
    ) {


        if (first == null ||
                second == null) {

            return 0;
        }



        if (first.equals(second)) {

            return 100.0;
        }




        /*
         * Ignore extremely short messages.
         */
        if (first.length() < MIN_LENGTH ||
                second.length() < MIN_LENGTH) {

            return 0;
        }




        /*
         * Prevent abuse with huge messages.
         */
        if (first.length() > MAX_LENGTH) {

            first =
                    first.substring(
                            0,
                            MAX_LENGTH
                    );
        }


        if (second.length() > MAX_LENGTH) {

            second =
                    second.substring(
                            0,
                            MAX_LENGTH
                    );
        }




        int max =
                Math.max(
                        first.length(),
                        second.length()
                );


        if (max == 0) {

            return 100.0;
        }




        int distance =
                levenshtein(
                        first,
                        second
                );



        double similarity =
                (
                        1.0 -
                                ((double) distance / max)
                )
                        *
                        100.0;



        return Math.max(
                0,
                similarity
        );
    }






    /**
     * Optimized Levenshtein distance.
     */
    private int levenshtein(
            String a,
            String b
    ) {


        int[] costs =
                new int[b.length() + 1];



        for (int i = 0;
             i <= b.length();
             i++) {

            costs[i] = i;
        }




        for (int i = 1;
             i <= a.length();
             i++) {


            costs[0] = i;


            int previous =
                    i - 1;




            for (int j = 1;
                 j <= b.length();
                 j++) {


                int current =
                        costs[j];



                int insert =
                        costs[j - 1] + 1;


                int delete =
                        costs[j] + 1;


                int replace =
                        previous +
                                (
                                        a.charAt(i - 1)
                                                ==
                                                b.charAt(j - 1)
                                                ?
                                                0
                                                :
                                                1
                                );



                costs[j] =
                        Math.min(
                                insert,
                                Math.min(
                                        delete,
                                        replace
                                )
                        );



                previous =
                        current;
            }
        }



        return costs[b.length()];
    }
}