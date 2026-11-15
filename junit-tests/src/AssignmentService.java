package occupan;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory resource assignment rule for Occupan FR-5.
 * Used by the full app (occupan-app) as the single place that decides whether an assignment is allowed.
 * assignResource() and snapshot() are the original unit that the JUnit tests cover; the methods
 * under "Added for the full app" at the bottom are new and have their own tests in occupan-app.
 */
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

    // ---------------------------------------------------------------------------------
    // Added for the full app. assignResource() and snapshot() above are unchanged.
    // ---------------------------------------------------------------------------------

    /** Registers a new resource. Rejects blank ids and duplicates. */
    public synchronized void addResource(String resourceId, Status status) {
        requireText(resourceId, "resourceId");
        if (status == null) throw new IllegalArgumentException("Status is required");
        if (resources.containsKey(resourceId)) throw new IllegalStateException("Resource already exists");
        resources.put(resourceId, new Snapshot(status, null));
    }

    /** Manual status change (FR-4). A resource with an active assignment must be released first. */
    public synchronized void changeStatus(String resourceId, Status newStatus) {
        requireText(resourceId, "resourceId");
        if (newStatus == null) throw new IllegalArgumentException("Status is required");
        Snapshot current = snapshot(resourceId);
        if (current.assignment() != null) {
            throw new IllegalStateException("Resource has an active assignment");
        }
        resources.put(resourceId, new Snapshot(newStatus, null));
    }

    /** Release (FR-6): ends the active assignment and moves the resource to the next status. */
    public synchronized Assignment releaseResource(String resourceId, Status nextStatus) {
        requireText(resourceId, "resourceId");
        if (nextStatus == null || nextStatus == Status.OCCUPIED) {
            throw new IllegalArgumentException("Next status must be something other than Occupied");
        }
        Snapshot current = snapshot(resourceId);
        if (current.assignment() == null) {
            throw new IllegalStateException("Resource has no active assignment");
        }
        resources.put(resourceId, new Snapshot(nextStatus, null));
        return current.assignment();
    }

    /** Removes a resource (retire). A resource with an active assignment cannot be removed. */
    public synchronized void removeResource(String resourceId) {
        requireText(resourceId, "resourceId");
        Snapshot current = snapshot(resourceId);
        if (current.assignment() != null) {
            throw new IllegalStateException("Resource has an active assignment");
        }
        resources.remove(resourceId);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
