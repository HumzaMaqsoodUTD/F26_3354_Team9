package occupan.application;

import occupan.AssignmentService;
import occupan.AssignmentService.Status;
import occupan.Clock;
import occupan.data.AssignmentRepository;
import occupan.data.Database;
import occupan.data.HistoryRepository;
import occupan.data.ResourceRepository;
import occupan.domain.DomainException;
import occupan.domain.Resource;
import occupan.domain.ResourceType;
import occupan.domain.Rules;
import occupan.domain.Statuses;
import occupan.domain.User;

/** Application layer: resource administration and status changes (FR-3, FR-4). */
public final class ResourceService {
    private final Database db;
    private final AssignmentService core;
    private final ResourceRepository resources;
    private final AssignmentRepository assignments;
    private final HistoryRepository history;

    public ResourceService(Database db, AssignmentService core, ResourceRepository resources,
                           AssignmentRepository assignments, HistoryRepository history) {
        this.db = db;
        this.core = core;
        this.resources = resources;
        this.assignments = assignments;
        this.history = history;
    }

    private Resource getOr404(String resourceId) {
        Resource resource = resources.get(Rules.normalizeId(resourceId));
        if (resource == null) throw DomainException.notFound("Resource " + resourceId + " was not found.");
        return resource;
    }

    private ResourceView view(Resource resource) {
        Status status = resource.retired() ? retiredStatus(resource) : core.snapshot(resource.resourceId()).status();
        return ResourceView.of(resource, status);
    }

    /** Retired resources are removed from the unit; they keep the status they had when retired. */
    private Status retiredStatus(Resource resource) {
        return Statuses.parse(history.statusChangesFor(resource.resourceId()).get(0).newStatus());
    }

    public ResourceView create(User user, String resourceId, String type, String location, String status) {
        user.require("resource.create");
        String[] fields = Rules.validateResourceFields(resourceId, location);
        ResourceType rtype = ResourceType.parse(type);
        Status rstatus = Statuses.parse(status == null ? "Available" : status);
        if (rstatus == Status.OCCUPIED) {
            throw DomainException.validation("New resources can't start as Occupied.");
        }
        synchronized (db.lock) {
            if (resources.get(fields[0]) != null) {
                throw DomainException.conflict("Resource " + fields[0] + " already exists.");
            }
            String now = Clock.nowIso();
            Resource resource = new Resource(fields[0], rtype, fields[1]);
            resources.insert(resource);
            core.addResource(fields[0], rstatus);
            history.recordStatusChange(fields[0], null, Statuses.label(rstatus), user.userId(), now);
            history.recordAudit(user.userId(), "resource.create", fields[0], rtype.label() + " at " + fields[1], now);
            return view(resource);
        }
    }

    public ResourceView updateLocation(User user, String resourceId, String location) {
        user.require("resource.update");
        synchronized (db.lock) {
            Resource resource = getOr404(resourceId);
            if (resource.retired()) throw DomainException.validation("Retired resources can't be edited.");
            String[] fields = Rules.validateResourceFields(resource.resourceId(), location);
            resource.setLocation(fields[1]);
            history.recordAudit(user.userId(), "resource.update", resource.resourceId(),
                    "location -> " + fields[1], Clock.nowIso());
            return view(resource);
        }
    }

    public ResourceView retire(User user, String resourceId) {
        user.require("resource.retire");
        synchronized (db.lock) {
            Resource resource = getOr404(resourceId);
            if (resource.retired()) throw DomainException.validation("Resource is already retired.");
            if (assignments.activeFor(resource.resourceId()) != null) {
                throw DomainException.conflict("Release the active assignment before retiring this resource.");
            }
            String now = Clock.nowIso();
            core.removeResource(resource.resourceId());
            resource.retire(now);
            history.recordAudit(user.userId(), "resource.retire", resource.resourceId(), "retired", now);
            return view(resource);
        }
    }

    public ResourceView changeStatus(User user, String resourceId, String newStatus) {
        user.require("status.change");
        Status target = Statuses.parse(newStatus);
        synchronized (db.lock) {
            Resource resource = getOr404(resourceId);
            if (resource.retired()) Rules.checkManualStatusChange(true, null, target, false);
            AssignmentService.Snapshot current = core.snapshot(resource.resourceId());
            Rules.checkManualStatusChange(false, current.status(), target,
                    assignments.activeFor(resource.resourceId()) != null);
            String now = Clock.nowIso();
            core.changeStatus(resource.resourceId(), target);
            history.recordStatusChange(resource.resourceId(), Statuses.label(current.status()),
                    Statuses.label(target), user.userId(), now);
            history.recordAudit(user.userId(), "status.change", resource.resourceId(),
                    Statuses.label(current.status()) + " -> " + Statuses.label(target), now);
            return view(resource);
        }
    }
}
