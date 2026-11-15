package occupan.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import occupan.App;
import occupan.domain.DomainException;
import occupan.domain.Role;
import occupan.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Application-layer tests on the demo data: assignment, release, status changes and permissions. */
public class WorkflowTest {
    private App.Services app;
    private User admin;
    private User nurse;

    @BeforeEach void setUp() {
        app = App.build();
        admin = app.auth().userForToken(app.auth().login("admin", "occupan-demo").token());
        nurse = app.auth().userForToken(app.auth().login("nurse", "occupan-demo").token());
    }

    private static int status(Runnable action) {
        return assertThrows(DomainException.class, action::run).statusCode();
    }

    private String statusOf(String resourceId) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>)
                app.queries().search(admin, resourceId, null, null, null, "resource_id", "asc").get("items");
        return (String) items.get(0).get("status");
    }

    @Test void W01_assignThenSecondAssignmentConflicts() {
        var first = app.assignments().assign(nurse, "BED-101", "SIM-55555", null);
        assertEquals("SIM-55555", first.patientId());
        assertEquals("Occupied", statusOf("BED-101"));
        assertEquals(409, status(() -> app.assignments().assign(nurse, "BED-101", "SIM-66666", null)));
    }

    @Test void W02_assignRejectsNonAvailableResources() {
        assertEquals(409, status(() -> app.assignments().assign(nurse, "BED-103", "SIM-12345", null))); // Cleaning
        assertEquals(409, status(() -> app.assignments().assign(nurse, "BED-104", "SIM-12345", null))); // Reserved
        assertEquals(409, status(() -> app.assignments().assign(nurse, "ROOM-202", "SIM-12345", null))); // Maintenance
        assertEquals(409, status(() -> app.assignments().assign(nurse, "EQ-302", "SIM-12345", null))); // Unavailable
    }

    @Test void W03_assignNeedsExactlyOneOfPatientOrNeed() {
        assertEquals(400, status(() -> app.assignments().assign(nurse, "BED-101", null, null)));
        assertEquals(400, status(() -> app.assignments().assign(nurse, "BED-101", "SIM-12345", "Training")));
        assertEquals(400, status(() -> app.assignments().assign(nurse, "BED-101", "Jane Doe", null)));
        assertEquals("Available", statusOf("BED-101"));
    }

    @Test void W04_unknownResourceIsNotFound() {
        assertEquals(404, status(() -> app.assignments().assign(nurse, "BED-999", "SIM-12345", null)));
    }

    @Test void W05_releaseMovesToChosenStatusAndCanBeAssignedAgain() {
        var a = app.assignments().assign(nurse, "BED-101", "SIM-55555", null);
        app.assignments().release(nurse, a.assignmentId(), "Cleaning");
        assertEquals("Cleaning", statusOf("BED-101"));
        assertEquals(409, status(() -> app.assignments().release(nurse, a.assignmentId(), "Available")));
        app.resources().changeStatus(nurse, "BED-101", "Available");
        assertDoesNotThrow(() -> app.assignments().assign(nurse, "BED-101", "SIM-77777", null));
    }

    @Test void W06_releaseRejectsOccupiedAndUnknownIds() {
        var a = app.assignments().assign(nurse, "BED-101", "SIM-55555", null);
        assertEquals(400, status(() -> app.assignments().release(nurse, a.assignmentId(), "Occupied")));
        assertEquals(400, status(() -> app.assignments().release(nurse, a.assignmentId(), "Nonsense")));
        assertEquals(404, status(() -> app.assignments().release(nurse, 9999, "Available")));
        assertEquals("Occupied", statusOf("BED-101"));
    }

    @Test void W07_manualStatusChangeRules() {
        app.resources().changeStatus(nurse, "BED-101", "Maintenance");
        assertEquals("Maintenance", statusOf("BED-101"));
        assertEquals(400, status(() -> app.resources().changeStatus(nurse, "BED-101", "Occupied")));
        assertEquals(400, status(() -> app.resources().changeStatus(nurse, "BED-101", "Maintenance")));
        assertEquals(400, status(() -> app.resources().changeStatus(nurse, "BED-102", "Cleaning"))); // assigned
    }

    @Test void W08_onlyAdministratorsManageResources() {
        assertEquals(403, status(() -> app.resources().create(nurse, "BED-900", "Bed", "Ward C", "Available")));
        assertEquals(403, status(() -> app.resources().updateLocation(nurse, "BED-101", "Ward Z")));
        assertEquals(403, status(() -> app.resources().retire(nurse, "BED-101")));
        var created = app.resources().create(admin, "bed-900", "Bed", "Ward C", "Available");
        assertEquals("BED-900", created.resourceId());
        assertEquals(409, status(() -> app.resources().create(admin, "BED-900", "Bed", "Ward C", "Available")));
        assertEquals("Ward D", app.resources().updateLocation(admin, "BED-900", "Ward D").location());
    }

    @Test void W09_retireHidesResourceButKeepsHistory() {
        app.resources().retire(admin, "BED-101");
        assertEquals(409, status(() -> app.assignments().assign(nurse, "BED-101", "SIM-12345", null))); // retired
        var items = (List<?>) app.queries().search(admin, "BED-101", null, null, null, "resource_id", "asc").get("items");
        assertTrue(items.isEmpty());
        assertNotNull(app.queries().history(admin, "BED-101"));
        assertEquals(409, status(() -> app.resources().retire(admin, "BED-102"))); // still assigned
        assertEquals(400, status(() -> app.resources().changeStatus(nurse, "BED-101", "Cleaning")));
    }

    @Test void W10_searchFiltersAndSorting() {
        var all = app.queries().search(nurse, null, null, null, null, "resource_id", "asc");
        assertEquals(11, all.get("total"));
        var rooms = app.queries().search(nurse, null, "Room", null, null, "resource_id", "asc");
        assertEquals(3, rooms.get("total"));
        var available = app.queries().search(nurse, null, null, "Available", null, "resource_id", "asc");
        assertEquals(5, available.get("total"));
        var wardB = app.queries().search(nurse, null, null, null, "ward b", "resource_id", "asc");
        assertEquals(2, wardB.get("total"));
        var byPatient = app.queries().search(nurse, "SIM-10001", null, null, null, "resource_id", "asc");
        assertEquals(1, byPatient.get("total"));
        assertEquals(400, status(() -> app.queries().search(nurse, null, "Spaceship", null, null, "resource_id", "asc")));
        assertEquals(400, status(() -> app.queries().search(nurse, null, null, null, null, "bogus", "asc")));
    }

    @Test void W11_historyAndUtilization() {
        var a = app.assignments().assign(nurse, "BED-101", "SIM-55555", null);
        app.assignments().release(nurse, a.assignmentId(), "Cleaning");
        var history = app.queries().history(nurse, "BED-101");
        assertEquals(1, ((List<?>) history.get("assignments")).size());
        assertEquals(3, ((List<?>) history.get("status_changes")).size()); // created, occupied, cleaning
        var report = app.queries().utilization(nurse);
        @SuppressWarnings("unchecked")
        var overall = (Map<String, Object>) report.get("overall");
        assertEquals(11, overall.get("total"));
        assertEquals(2, overall.get("Occupied"));
        assertEquals(18.2, overall.get("utilization_pct"));
        assertEquals(404, status(() -> app.queries().history(nurse, "NOPE-1")));
    }

    @Test void W12_loginAndSessions() {
        assertEquals(401, status(() -> app.auth().login("nurse", "wrong")));
        assertEquals(401, status(() -> app.auth().login("nobody", "occupan-demo")));
        var login = app.auth().login("nurse", "occupan-demo");
        assertEquals(Role.CLINICAL_STAFF, login.user().role());
        app.auth().logout(login.token());
        assertEquals(401, status(() -> app.auth().userForToken(login.token())));
    }

    @Test void W13_competingAssignmentsHaveOneWinner() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                final String patient = "SIM-2000" + i;
                tasks.add(pool.submit(() -> {
                    try {
                        app.assignments().assign(nurse, "EQ-301", patient, null);
                        return true;
                    } catch (DomainException conflict) {
                        return false;
                    }
                }));
            }
            int wins = 0;
            for (var t : tasks) if (t.get(10, java.util.concurrent.TimeUnit.SECONDS)) wins++;
            assertEquals(1, wins);
        } finally {
            pool.shutdownNow();
        }
    }
}
