package occupan.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum ResourceType {
    BED("Bed"), ROOM("Room"), EQUIPMENT("Equipment");

    private final String label;

    ResourceType(String label) { this.label = label; }

    public String label() { return label; }

    public static ResourceType parse(String value) {
        for (ResourceType t : values()) if (t.label.equals(value)) return t;
        throw DomainException.validation("Type must be one of: "
                + Arrays.stream(values()).map(ResourceType::label).collect(Collectors.joining(", ")) + ".");
    }
}
