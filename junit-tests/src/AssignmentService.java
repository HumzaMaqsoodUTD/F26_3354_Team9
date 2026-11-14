package occupan;

import java.util.HashMap;
import java.util.Map;

/** Standalone in-memory sample for Occupan FR-5; not database-integrated. */
public final class AssignmentService {
    public enum Status {
        AVAILABLE, OCCUPIED, RESERVED, CLEANING, MAINTENANCE, UNAVAILABLE
    }

    public record Assignment(String resourceId, String patientId, String staffId) {}
    public record Snapshot(Status status, Assignment assignment) {}

    private final Map<String, Snapshot> resources = new HashMap<>();

    public AssignmentService(Map<String, Status> initialResources) {
        initialResources.forEach((id, status) -> {
            requireText(id, "resourceId");
            if (status == null) throw new IllegalArgumentException("Status is required");
            resources.put(id, new Snapshot(status, null));
        });
    }

    // Synchronization makes the availability check and update atomic within
    // this service instance. A shared database needs its own transaction rule.
    public synchronized Assignment assignResource(
            String resourceId, String patientId, String staffId) {
        requireText(resourceId, "resourceId");
        requireText(patientId, "patientId");
        requireText(staffId, "staffId");
        Snapshot current = snapshot(resourceId);
        if (current.status() != Status.AVAILABLE || current.assignment() != null) {
            throw new IllegalStateException("Resource is not available");
        }
        Assignment assignment = new Assignment(resourceId, patientId, staffId);
        resources.put(resourceId, new Snapshot(Status.OCCUPIED, assignment));
        return assignment;
    }

    public synchronized Snapshot snapshot(String resourceId) {
        Snapshot current = resources.get(resourceId);
        if (current == null) throw new IllegalArgumentException("Unknown resource");
        return current;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
