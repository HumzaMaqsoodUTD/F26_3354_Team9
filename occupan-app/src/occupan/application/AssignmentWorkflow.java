package occupan.application;

import occupan.AssignmentService;
import occupan.AssignmentService.Snapshot;
import occupan.AssignmentService.Status;
import occupan.Clock;
import occupan.data.AssignmentRepository;
import occupan.data.Database;
import occupan.data.HistoryRepository;
import occupan.data.ResourceRepository;
import occupan.domain.AssignmentRecord;
import occupan.domain.DomainException;
import occupan.domain.Resource;
import occupan.domain.Rules;
import occupan.domain.Statuses;
import occupan.domain.User;

/**
 * Application layer: assign and release resources (FR-5, FR-6, FR-8).
 * The decision to allow an assignment is made by AssignmentService.assignResource(), the unit covered
 * by the JUnit tests. This class checks permissions and input, calls the unit, and records the result.
 */
public final class AssignmentWorkflow {
    private final Database db;
    private final AssignmentService core;
    private final ResourceRepository resources;
    private final AssignmentRepository assignments;
    private final HistoryRepository history;

    public AssignmentWorkflow(Database db, AssignmentService core, ResourceRepository resources,
                              AssignmentRepository assignments, HistoryRepository history) {
        this.db = db;
        this.core = core;
        this.resources = resources;
        this.assignments = assignments;
        this.history = history;
    }

    public AssignmentRecord assign(User user, String resourceId, String patientIdentifier, String operationalNeed) {
        user.require("assign");
        boolean hasPatient = patientIdentifier != null && !patientIdentifier.isBlank();
        boolean hasNeed = operationalNeed != null && !operationalNeed.isBlank();
        if (hasPatient == hasNeed) {
            throw DomainException.validation("Give either a simulated patient ID or an operational need (not both).");
        }
        String patientId = hasPatient ? Rules.validatePatientId(patientIdentifier) : null;
        String need = hasNeed ? Rules.validateOperationalNeed(operationalNeed) : null;

        synchronized (db.lock) {
            Resource resource = resources.get(Rules.normalizeId(resourceId));
            if (resource == null) throw DomainException.notFound("Resource " + resourceId + " was not found.");
            if (resource.retired()) throw DomainException.conflict("Resource is retired.");
            Snapshot before = core.snapshot(resource.resourceId());
            try {
                // The unit takes one identifier for "who/what it is for": a patient ID or the need text.
                core.assignResource(resource.resourceId(), patientId != null ? patientId : need, user.userId());
            } catch (IllegalStateException notAvailable) {
                throw DomainException.conflict(Rules.explainAssignRejection(before));
            }
            String now = Clock.nowIso();
            AssignmentRecord record = assignments.insert(resource.resourceId(), patientId, need, user.userId(), now);
            history.recordStatusChange(resource.resourceId(), Statuses.label(before.status()),
                    Statuses.label(Status.OCCUPIED), user.userId(), now);
            history.recordAudit(user.userId(), "assign", resource.resourceId(),
                    "to " + record.subject(), now);
            return record;
        }
    }

    public AssignmentRecord release(User user, int assignmentId, String nextStatus) {
        user.require("release");
        Status target = Statuses.parse(nextStatus);
        Rules.checkReleaseStatus(target);
        synchronized (db.lock) {
            AssignmentRecord record = assignments.get(assignmentId);
            if (record == null) throw DomainException.notFound("Assignment " + assignmentId + " was not found.");
            if (!record.active()) throw DomainException.conflict("This assignment was already released.");
            core.releaseResource(record.resourceId(), target);
            String now = Clock.nowIso();
            record.release(user.userId(), now);
            history.recordStatusChange(record.resourceId(), Statuses.label(Status.OCCUPIED),
                    Statuses.label(target), user.userId(), now);
            history.recordAudit(user.userId(), "release", record.resourceId(),
                    "next status " + Statuses.label(target), now);
            return record;
        }
    }
}
