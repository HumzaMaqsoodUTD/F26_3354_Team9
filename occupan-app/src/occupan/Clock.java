package occupan;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** One place for time, so every timestamp in the system looks the same (UTC, to the second). */
public final class Clock {
    private Clock() {}

    public static Instant now() { return Instant.now().truncatedTo(ChronoUnit.SECONDS); }

    public static String nowIso() { return now().toString(); }
}
