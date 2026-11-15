package occupan.domain;

/** Class diagram: StatusHistory. previousStatus is null when the resource was first created. */
public record StatusChange(String resourceId, String previousStatus, String newStatus,
                           String changedBy, String changedAt) {}
