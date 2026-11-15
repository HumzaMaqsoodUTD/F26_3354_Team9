package occupan.data;

import java.util.ArrayList;
import java.util.List;
import occupan.domain.Resource;

/** Data access layer: resource details (type, location, retired). */
public final class ResourceRepository {
    private final Database db;

    public ResourceRepository(Database db) { this.db = db; }

    public Resource get(String resourceId) {
        synchronized (db.lock) { return db.resources.get(resourceId); }
    }

    public void insert(Resource resource) {
        synchronized (db.lock) { db.resources.put(resource.resourceId(), resource); }
    }

    public List<Resource> all() {
        synchronized (db.lock) { return new ArrayList<>(db.resources.values()); }
    }
}
