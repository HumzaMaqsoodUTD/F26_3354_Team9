package occupan.domain;

/** Class diagram: User. */
public record User(String userId, String username, String name, Role role) {
    public boolean can(String action) { return role.allows(action); }

    public void require(String action) {
        if (!can(action)) throw DomainException.permissionDenied("Your role cannot perform: " + action);
    }
}
