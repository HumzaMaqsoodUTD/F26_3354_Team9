package occupan;

import java.nio.file.Path;

/** Settings read from environment variables (see README.md). */
public final class Config {
    private Config() {}

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static int port() { return Integer.parseInt(env("OCCUPAN_PORT", "8000")); }

    /** Only used to create the two fake demo accounts when the app starts. */
    public static String demoPassword() { return env("OCCUPAN_DEMO_PASSWORD", "occupan-demo"); }

    public static int sessionHours() { return Integer.parseInt(env("OCCUPAN_SESSION_HOURS", "8")); }

    public static Path staticDir() { return Path.of(env("OCCUPAN_STATIC_DIR", "static")); }
}
