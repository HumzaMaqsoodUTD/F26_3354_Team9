package occupan.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import occupan.AssignmentService;
import occupan.AssignmentService.Status;
import occupan.data.AssignmentRepository;
import occupan.data.HistoryRepository;
import occupan.data.ResourceRepository;
import occupan.data.UserRepository;
import occupan.data.Database;
import occupan.domain.AssignmentRecord;
import occupan.domain.DomainException;
import occupan.domain.Resource;
import occupan.domain.ResourceType;
import occupan.domain.Rules;
import occupan.domain.StatusChange;
import occupan.domain.Statuses;
import occupan.domain.User;

/** Application layer: search, history and utilization report (FR-2, FR-7, FR-9, FR-10). */
public final class QueryService {
    public static final List<String> SORT_FIELDS = List.of("resource_id", "type", "location", "status");

    private final Database db;
    private final AssignmentService core;
    private final ResourceRepository resources;
    private final AssignmentRepository assignments;
    private final HistoryRepository history;
    private final UserRepository users;

    public QueryService(Database db, AssignmentService core, ResourceRepository resources,
                        AssignmentRepository assignments, HistoryRepository history, UserRepository users) {
        this.db = db;
        this.core = core;
        this.resources = resources;
        this.assignments = assignments;
        this.history = history;
        this.users = users;
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private String nameOf(String userId) {
        return userId == null ? null : users.findById(userId).map(User::name).orElse(userId);
    }

    /** Dashboard list. Empty filters are ignored; bad filter values are rejected. */
    public Map<String, Object> search(User user, String q, String type, String status, String location,
                                      String sort, String order) {
        user.require("search");
        String typeFilter = type == null || type.isEmpty() ? null : ResourceType.parse(type).label();
        String statusFilter = status == null || status.isEmpty() ? null : Statuses.label(Statuses.parse(status));
        String sortBy = sort == null || sort.isEmpty() ? "resource_id" : sort;
        String orderBy = order == null || order.isEmpty() ? "asc" : order;
        if (!SORT_FIELDS.contains(sortBy)) {
            throw DomainException.validation("Sort must be one of: " + String.join(", ", SORT_FIELDS));
        }
        if (!orderBy.equals("asc") && !orderBy.equals("desc")) {
            throw DomainException.validation("Order must be asc or desc.");
        }
        String text = q == null ? "" : q.strip();
        String loc = location == null ? "" : location.strip();

        List<Map<String, Object>> items = new ArrayList<>();
        synchronized (db.lock) {
            for (Resource r : resources.all()) {
                if (r.retired()) continue;
                String statusLabel = Statuses.label(core.snapshot(r.resourceId()).status());
                AssignmentRecord active = assignments.activeFor(r.resourceId());
                String subject = active == null ? null : active.subject();
                if (typeFilter != null && !r.type().label().equals(typeFilter)) continue;
                if (statusFilter != null && !statusLabel.equals(statusFilter)) continue;
                if (!loc.isEmpty() && !contains(r.location(), loc)) continue;
                if (!text.isEmpty() && !(contains(r.resourceId(), text) || contains(r.location(), text)
                        || (active != null && (contains(active.patientId(), text)
                        || contains(active.operationalNeed(), text))))) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("resource_id", r.resourceId());
                item.put("type", r.type().label());
                item.put("location", r.location());
                item.put("status", statusLabel);
                item.put("retired", false);
                item.put("assignment_id", active == null ? null : active.assignmentId());
                item.put("assigned_to", subject);
                items.add(item);
            }
        }
        Comparator<Map<String, Object>> byField = Comparator.comparing(m -> (String) m.get(sortBy));
        if (orderBy.equals("desc")) byField = byField.reversed();
        items.sort(byField.thenComparing(m -> (String) m.get("resource_id")));

        Map<String, Object> counts = new LinkedHashMap<>();
        for (Status s : Status.values()) counts.put(Statuses.label(s), 0);
        for (Map<String, Object> item : items) counts.merge((String) item.get("status"), 1, (a, b) -> (Integer) a + 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        result.put("status_counts", counts);
        return result;
    }

    public Map<String, Object> history(User user, String resourceId) {
        user.require("history");
        String rid = Rules.normalizeId(resourceId);
        synchronized (db.lock) {
            if (resources.get(rid) == null) {
                throw DomainException.notFound("Resource " + resourceId + " was not found.");
            }
            List<Map<String, Object>> assignmentRows = new ArrayList<>();
            for (AssignmentRecord a : assignments.forResource(rid)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("assignment_id", a.assignmentId());
                row.put("patient_id", a.patientId());
                row.put("operational_need", a.operationalNeed());
                row.put("assigned_at", a.assignedAt());
                row.put("released_at", a.releasedAt());
                row.put("assigned_by", nameOf(a.assignedBy()));
                row.put("released_by", nameOf(a.releasedBy()));
                assignmentRows.add(row);
            }
            List<Map<String, Object>> statusRows = new ArrayList<>();
            for (StatusChange c : history.statusChangesFor(rid)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("previous_status", c.previousStatus());
                row.put("new_status", c.newStatus());
                row.put("changed_at", c.changedAt());
                row.put("changed_by", nameOf(c.changedBy()));
                statusRows.add(row);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resource_id", rid);
            result.put("assignments", assignmentRows);
            result.put("status_changes", statusRows);
            return result;
        }
    }

    private static double pct(int occupied, int total) {
        return total == 0 ? 0.0 : Math.round(1000.0 * occupied / total) / 10.0;
    }

    /** FR-10: counts by type and status. Utilization % = Occupied / all active resources of that type. */
    public Map<String, Object> utilization(User user) {
        user.require("report");
        Map<String, Map<String, Object>> byType = new LinkedHashMap<>();
        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("total", 0);
        for (Status s : Status.values()) overall.put(Statuses.label(s), 0);
        synchronized (db.lock) {
            for (ResourceType t : ResourceType.values()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("total", 0);
                entry.put("utilization_pct", 0.0);
                for (Status s : Status.values()) entry.put(Statuses.label(s), 0);
                byType.put(t.label(), entry);
            }
            for (Resource r : resources.all()) {
                if (r.retired()) continue;
                Map<String, Object> entry = byType.get(r.type().label());
                String label = Statuses.label(core.snapshot(r.resourceId()).status());
                entry.merge(label, 1, (a, b) -> (Integer) a + 1);
                entry.merge("total", 1, (a, b) -> (Integer) a + 1);
                overall.merge(label, 1, (a, b) -> (Integer) a + 1);
                overall.merge("total", 1, (a, b) -> (Integer) a + 1);
            }
        }
        String occupied = Statuses.label(Status.OCCUPIED);
        for (Map<String, Object> entry : byType.values()) {
            entry.put("utilization_pct", pct((Integer) entry.get(occupied), (Integer) entry.get("total")));
        }
        overall.put("utilization_pct", pct((Integer) overall.get(occupied), (Integer) overall.get("total")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("by_type", byType);
        result.put("overall", overall);
        return result;
    }
}
