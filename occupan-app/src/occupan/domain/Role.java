package occupan.domain;

import java.util.Set;

/** Roles and what each may do (FR-1 / security requirement). */
public enum Role {
    ADMINISTRATOR("administrator", Set.of(
            "dashboard", "search", "resource.create", "resource.update", "resource.retire",
            "status.change", "assign", "release", "history", "report")),
    CLINICAL_STAFF("clinical_staff", Set.of(
            "dashboard", "search", "status.change", "assign", "release", "history", "report"));

    private final String wire;
    private final Set<String> actions;

    Role(String wire, Set<String> actions) {
        this.wire = wire;
        this.actions = actions;
    }

    public String wire() { return wire; }

    public boolean allows(String action) { return actions.contains(action); }
}
