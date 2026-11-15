package occupan.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import occupan.domain.AssignmentRecord;
import occupan.domain.Resource;
import occupan.domain.StatusChange;
import occupan.domain.User;

/**
 * Persistence layer: the in-memory tables. Data lives as long as the server runs and is re-created
 * with the demo data on every start. Status and active-assignment state live inside AssignmentService.
 * Multi-step operations take {@link #lock} so a check and its update happen together.
 */
public final class Database {
    public final Object lock = new Object();

    public record Account(User user, String salt, String passwordHash) {}
    public record Session(String token, String userId, Instant expiresAt) {}
    public record AuditEntry(String userId, String action, String resourceId, String details, String at) {}

    final Map<String, Account> accountsByUsername = new LinkedHashMap<>();
    final Map<String, User> usersById = new LinkedHashMap<>();
    final Map<String, Session> sessions = new ConcurrentHashMap<>();
    final Map<String, Resource> resources = new LinkedHashMap<>();
    final List<AssignmentRecord> assignments = new ArrayList<>();
    final List<StatusChange> statusChanges = new ArrayList<>();
    final List<AuditEntry> auditLog = new ArrayList<>();
    int nextAssignmentId = 1;
}
