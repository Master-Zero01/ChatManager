package org.me.chatManager.moderation;

public class ModerationResult {

    private final boolean allowed;

    private final String message;

    private final String reason;

    /*
     * Similarity score used by:
     * - Similar message detection
     * - Staff notifications
     *
     * Value:
     * -1 = not applicable
     * 0-100 = similarity percentage
     */
    private final double similarity;


    public ModerationResult(
            boolean allowed,
            String message,
            String reason,
            double similarity
    ) {

        this.allowed = allowed;
        this.message = message;
        this.reason = reason;
        this.similarity = similarity;
    }


    /*
     * Creates an allowed result.
     */
    public static ModerationResult allowed() {

        return new ModerationResult(
                true,
                null,
                null,
                -1
        );
    }


    /*
     * Creates a blocked result without similarity data.
     */
    public static ModerationResult blocked(
            String message,
            String reason
    ) {

        return new ModerationResult(
                false,
                message,
                reason,
                -1
        );
    }


    /*
     * Creates a blocked result with similarity data.
     */
    public static ModerationResult blocked(
            String message,
            String reason,
            double similarity
    ) {

        return new ModerationResult(
                false,
                message,
                reason,
                similarity
        );
    }


    public boolean isAllowed() {

        return allowed;
    }


    public String getMessage() {

        return message;
    }


    public String getReason() {

        return reason;
    }


    public double getSimilarity() {

        return similarity;
    }
}