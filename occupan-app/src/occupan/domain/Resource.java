package occupan.domain;

/**
 * Class diagram: Resource (id, type, location). A resource's status and active assignment are kept by
 * the tested unit, AssignmentService, so they are not duplicated here.
 */
public final class Resource {
    private final String resourceId;
    private final ResourceType type;
    private String location;
    private String retiredAt;

    public Resource(String resourceId, ResourceType type, String location) {
        this.resourceId = resourceId;
        this.type = type;
        this.location = location;
    }

    public String resourceId() { return resourceId; }
    public ResourceType type() { return type; }
    public String location() { return location; }
    public String retiredAt() { return retiredAt; }
    public boolean retired() { return retiredAt != null; }

    public void setLocation(String location) { this.location = location; }
    public void retire(String when) { this.retiredAt = when; }
}
