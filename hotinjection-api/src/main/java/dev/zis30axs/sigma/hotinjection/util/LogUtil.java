package dev.zis30axs.sigma.hotinjection.util;

public final class LogUtil {
    private static final String PREFIX = "[Sigma-HotInjection] ";

    private LogUtil() {
    }

    public static void info(String message) {
        System.out.println(PREFIX + message);
    }

    public static void warn(String message) {
        System.err.println(PREFIX + "WARN: " + message);
    }

    public static void error(String message, Throwable error) {
        System.err.println(PREFIX + "ERROR: " + message);
        if (error != null) {
            error.printStackTrace(System.err);
        }
    }
}
