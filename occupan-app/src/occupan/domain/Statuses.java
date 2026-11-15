package occupan.domain;

import java.util.Arrays;
import java.util.stream.Collectors;
import occupan.AssignmentService.Status;

/** Display names for the Status enum of the tested unit (AVAILABLE -> "Available"). */
public final class Statuses {
    private Statuses() {}

    public static String label(Status status) {
        String name = status.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    public static Status parse(String value) {
        for (Status s : Status.values()) if (label(s).equals(value)) return s;
        throw DomainException.validation("Status must be one of: "
                + Arrays.stream(Status.values()).map(Statuses::label).collect(Collectors.joining(", ")) + ".");
    }
}
