package org.me.chatManager.util;

import java.util.regex.Pattern;

/** Removes user-supplied legacy and hex color markers when formatting is not permitted. */
public final class ColorCodeSanitizer {

    private static final Pattern COLOR_CODE = Pattern.compile(
            "(?i)[&§](?:#[0-9a-f]{6}|[0-9a-fk-orx])");

    private ColorCodeSanitizer() {
    }

    /**
     * Strips standard (&a / §a), alternate hex (&#RRGGBB), and legacy hex
     * sequences (&x&F&F... / §x§F§F...) while leaving ordinary text intact.
     */
    public static String strip(String message) {
        return message == null ? "" : COLOR_CODE.matcher(message).replaceAll("");
    }
}
