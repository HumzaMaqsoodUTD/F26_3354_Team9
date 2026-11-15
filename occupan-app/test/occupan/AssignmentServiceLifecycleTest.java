package occupan;

import static occupan.AssignmentService.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests for the methods added to AssignmentService for the full app (release, status change, add, remove). */
public class AssignmentServiceLifecycleTest {
    private AssignmentService service() {
        return new AssignmentService(Map.of("BED-101", AVAILABLE));
    }

    @Test void L01_releaseReturnsAssignmentAndMovesToNextStatus() {
        var service = service();
        var assignment = service.assignResource("BED-101", "SIM-0001", "STAFF-01");
        var released = service.releaseResource("BED-101", CLEANING);
        assertEquals(assignment, released);
        assertEquals(CLEANING, service.snapshot("BED-101").status());
        assertNull(service.snapshot("BED-101").assignment());
    }

    @Test void L02_releaseWithoutAssignmentIsRejected() {
        var service = service();
        assertThrows(IllegalStateException.class, () -> service.releaseResource("BED-101", CLEANING));
        assertEquals(AVAILABLE, service.snapshot("BED-101").status());
    }

    @Test void L03_releaseCannotLeaveResourceOccupied() {
        var service = service();
        service.assignResource("BED-101", "SIM-0001", "STAFF-01");
        assertThrows(IllegalArgumentException.class, () -> service.releaseResource("BED-101", OCCUPIED));
        assertThrows(IllegalArgumentException.class, () -> service.releaseResource("BED-101", null));
        assertEquals(OCCUPIED, service.snapshot("BED-101").status());
    }

    @Test void L04_releasedResourceCanBeAssignedAgain() {
        var service = service();
        service.assignResource("BED-101", "SIM-0001", "STAFF-01");
        service.releaseResource("BED-101", AVAILABLE);
        var second = service.assignResource("BED-101", "SIM-0002", "STAFF-02");
        assertEquals("SIM-0002", second.patientId());
    }

    @Test void L05_changeStatusUpdatesStatus() {
        var service = service();
        service.changeStatus("BED-101", MAINTENANCE);
        assertEquals(MAINTENANCE, service.snapshot("BED-101").status());
        assertThrows(IllegalStateException.class, () -> service.assignResource("BED-101", "SIM-0001", "STAFF-01"));
    }

    @Test void L06_changeStatusIsRejectedWhileAssigned() {
        var service = service();
        var assignment = service.assignResource("BED-101", "SIM-0001", "STAFF-01");
        assertThrows(IllegalStateException.class, () -> service.changeStatus("BED-101", CLEANING));
        assertEquals(assignment, service.snapshot("BED-101").assignment());
    }

    @Test void L07_unknownResourceIsRejected() {
        var service = service();
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus("BED-999", CLEANING));
        assertThrows(IllegalArgumentException.class, () -> service.releaseResource("BED-999", CLEANING));
        assertThrows(IllegalArgumentException.class, () -> service.removeResource("BED-999"));
    }

    @Test void L08_addResourceRejectsDuplicatesAndBlankIds() {
        var service = service();
        service.addResource("BED-102", RESERVED);
        assertEquals(RESERVED, service.snapshot("BED-102").status());
        assertThrows(IllegalStateException.class, () -> service.addResource("BED-102", AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> service.addResource("  ", AVAILABLE));
        assertThrows(IllegalArgumentException.class, () -> service.addResource("BED-103", null));
    }

    @Test void L09_removeResourceWorksOnlyWhenNotAssigned() {
        var service = service();
        service.assignResource("BED-101", "SIM-0001", "STAFF-01");
        assertThrows(IllegalStateException.class, () -> service.removeResource("BED-101"));
        service.releaseResource("BED-101", AVAILABLE);
        service.removeResource("BED-101");
        assertThrows(IllegalArgumentException.class, () -> service.snapshot("BED-101"));
    }
}
