package occupan.data;

import java.util.ArrayList;
import java.util.List;
import occupan.data.Database.AuditEntry;
import occupan.domain.StatusChange;

/** Data access layer: status history (FR-8, FR-9) and the audit log. */
public final class HistoryRepository {
    private final Database db;

    public HistoryRepository(Database db) { this.db = db; }

    public void recordStatusChange(String resourceId, String previous, String next, String userId, String at) {
        synchronized (db.lock) { db.statusChanges.add(new StatusChange(resourceId, previous, next, userId, at)); }
    }

    public void recordAudit(String userId, String action, String resourceId, String details, String at) {
        synchronized (db.lock) { db.auditLog.add(new AuditEntry(userId, action, resourceId, details, at)); }
    }

    /** Newest first. */
    public List<StatusChange> statusChangesFor(String resourceId) {
        synchronized (db.lock) {
            List<StatusChange> out = new ArrayList<>();
            for (StatusChange c : db.statusChanges) if (c.resourceId().equals(resourceId)) out.add(0, c);
            return out;
        }
    }

    public List<AuditEntry> auditLog() {
        synchronized (db.lock) { return new ArrayList<>(db.auditLog); }
    }
}
