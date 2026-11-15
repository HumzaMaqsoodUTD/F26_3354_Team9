package occupan.application;

import java.util.List;
import occupan.Config;
import occupan.domain.Role;
import occupan.domain.User;

/** First-run demo data: two fake accounts and a few fake resources. Simulated identifiers only. */
public final class DemoData {
    private DemoData() {}

    private record Demo(String id, String type, String location, String status) {}

    private static final List<Demo> RESOURCES = List.of(
            new Demo("BED-101", "Bed", "Ward A", "Available"),
            new Demo("BED-102", "Bed", "Ward A", "Available"),
            new Demo("BED-103", "Bed", "Ward A", "Cleaning"),
            new Demo("BED-104", "Bed", "Ward B", "Reserved"),
            new Demo("BED-105", "Bed", "Ward B", "Available"),
            new Demo("ROOM-201", "Room", "Floor 2", "Available"),
            new Demo("ROOM-202", "Room", "Floor 2", "Maintenance"),
            new Demo("ROOM-203", "Room", "Floor 2", "Available"),
            new Demo("EQ-301", "Equipment", "Storage", "Available"),
            new Demo("EQ-302", "Equipment", "Storage", "Unavailable"),
            new Demo("EQ-303", "Equipment", "Ward A", "Available"));

    public static void load(AuthService auth, ResourceService resources, AssignmentWorkflow assignments) {
        String password = Config.demoPassword();
        User admin = auth.createUser("admin", "Demo Administrator", Role.ADMINISTRATOR, password);
        auth.createUser("nurse", "Demo Clinical Staff", Role.CLINICAL_STAFF, password);
        for (Demo d : RESOURCES) resources.create(admin, d.id(), d.type(), d.location(), d.status());
        assignments.assign(admin, "BED-102", "SIM-10001", null);
        assignments.assign(admin, "ROOM-201", null, "Staff training session");
    }
}
