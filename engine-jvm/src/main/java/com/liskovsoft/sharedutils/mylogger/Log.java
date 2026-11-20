package com.liskovsoft.sharedutils.mylogger;

import java.util.logging.Level;
import java.util.logging.Logger;

/** JVM logger used in place of android.util.Log. */
public final class Log {
    private static final Logger LOGGER = Logger.getLogger("NuvioEngine");

    private Log() {
    }

    public static void d(String tag, String message, Object... args) {
        log(Level.FINE, tag, message, args);
    }

    public static void i(String tag, String message, Object... args) {
        log(Level.INFO, tag, message, args);
    }

    public static void w(String tag, String message, Object... args) {
        log(Level.WARNING, tag, message, args);
    }

    public static void e(String tag, String message, Object... args) {
        log(Level.WARNING, tag, message, args);
    }

    private static void log(Level level, String tag, String message, Object... args) {
        if (!LOGGER.isLoggable(level)) return;
        String text;
        try {
            text = args == null || args.length == 0 ? message : String.format(message, args);
        } catch (RuntimeException e) {
            text = message;
        }
        LOGGER.log(level, tag + ": " + text);
    }
}
