package occupan.data;

import java.util.ArrayList;
import java.util.List;
import occupan.domain.AssignmentRecord;

/** Data access layer: assignment records (who, what for, when). */
public final class AssignmentRepository {
    private final Database db;

    public AssignmentRepository(Database db) { this.db = db; }

    public AssignmentRecord insert(String resourceId, String patientId, String operationalNeed,
                                   String userId, String at) {
        synchronized (db.lock) {
            AssignmentRecord record = new AssignmentRecord(db.nextAssignmentId++, resourceId, patientId,
                    operationalNeed, userId, at);
            db.assignments.add(record);
            return record;
        }
    }

    public AssignmentRecord get(int assignmentId) {
        synchronized (db.lock) {
            for (AssignmentRecord a : db.assignments) if (a.assignmentId() == assignmentId) return a;
            return null;
        }
    }

    public AssignmentRecord activeFor(String resourceId) {
        synchronized (db.lock) {
            for (AssignmentRecord a : db.assignments) {
                if (a.resourceId().equals(resourceId) && a.active()) return a;
            }
            return null;
        }
    }

    /** Newest first. */
    public List<AssignmentRecord> forResource(String resourceId) {
        synchronized (db.lock) {
            List<AssignmentRecord> out = new ArrayList<>();
            for (AssignmentRecord a : db.assignments) if (a.resourceId().equals(resourceId)) out.add(0, a);
            return out;
        }
    }
}
