package occupan.domain;

import occupan.AssignmentService.Snapshot;
import occupan.AssignmentService.Status;

/**
 * Domain rules around the tested unit. Whether an assignment is allowed is decided by
 * AssignmentService.assignResource(); this class only validates input and words the error messages.
 */
public final class Rules {
    private Rules() {}

    public static String validatePatientId(String identifier) {
        return Patient.validateIdentifier(identifier);
    }

    public static String validateOperationalNeed(String text) {
        String cleaned = text == null ? "" : text.strip();
        if (cleaned.isEmpty()) throw DomainException.validation("Operational need can't be empty.");
        if (cleaned.length() > 120) {
            throw DomainException.validation("Operational need must be 120 characters or fewer.");
        }
        return cleaned;
    }

    public static String normalizeId(String resourceId) {
        return resourceId == null ? "" : resourceId.strip().toUpperCase();
    }

    /** Returns {id, location} after trimming and checking length and characters. */
    public static String[] validateResourceFields(String resourceId, String location) {
        String rid = normalizeId(resourceId);
        String loc = location == null ? "" : location.strip();
        boolean idOk = !rid.isEmpty() && rid.length() <= 20
                && rid.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_');
        if (!idOk) {
            throw DomainException.validation("Resource ID must be 1-20 letters, numbers, dashes or underscores.");
        }
        if (loc.isEmpty() || loc.length() > 60) {
            throw DomainException.validation("Location is required (60 characters max).");
        }
        return new String[]{rid, loc};
    }

    /** FR-4: Occupied only happens through an assignment, and an assigned resource must be released first. */
    public static void checkManualStatusChange(boolean retired, Status current, Status target,
                                               boolean hasActiveAssignment) {
        if (retired) throw DomainException.validation("Retired resources cannot change status.");
        if (hasActiveAssignment) {
            throw DomainException.validation("Release the active assignment before changing status.");
        }
        if (target == Status.OCCUPIED) {
            throw DomainException.validation("A resource becomes Occupied by assigning it, not by a manual change.");
        }
        if (target == current) {
            throw DomainException.validation("Resource is already " + Statuses.label(target) + ".");
        }
    }

    /** FR-6: when releasing, staff pick the next status; it can't still be Occupied. */
    public static void checkReleaseStatus(Status next) {
        if (next == Status.OCCUPIED) throw DomainException.validation("Choose a next status other than Occupied.");
    }

    /** Words the reason after the unit has rejected an assignment. */
    public static String explainAssignRejection(Snapshot snapshot) {
        if (snapshot.assignment() != null) return "Resource already has an active assignment.";
        return "Resource is " + Statuses.label(snapshot.status()) + ", not Available.";
    }
}
