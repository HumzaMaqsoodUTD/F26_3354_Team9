package occupan;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static occupan.AssignmentService.Status.*;

public class AssignmentServiceTest {
    private AssignmentService service() {
        return new AssignmentService(Map.of("BED-101", AVAILABLE));
    }

    @Test void TC05_01_availableResourceIsAssigned() {
        var service = service();
        var assignment = service.assignResource("BED-101", "SIM-001", "STAFF-01");
        assertEquals("BED-101", assignment.resourceId());
        assertEquals("SIM-001", assignment.patientId());
        assertEquals("STAFF-01", assignment.staffId());
        assertEquals(OCCUPIED, service.snapshot("BED-101").status());
        assertEquals(assignment, service.snapshot("BED-101").assignment());
    }

    @Test void TC05_02_nonAvailableStatusesAreRejected() {
        for (var status : new AssignmentService.Status[]{
                OCCUPIED, RESERVED, CLEANING, MAINTENANCE, UNAVAILABLE}) {
            var service = new AssignmentService(Map.of("BED-101", status));
            var before = service.snapshot("BED-101");
            assertThrows(IllegalStateException.class,
                () -> service.assignResource("BED-101", "SIM-001", "STAFF-01"));
            assertEquals(before, service.snapshot("BED-101"));
        }
    }

    @Test void TC05_03_secondAssignmentPreservesOriginal() {
        var service = service();
        var first = service.assignResource("BED-101", "SIM-001", "STAFF-01");
        assertThrows(IllegalStateException.class,
            () -> service.assignResource("BED-101", "SIM-002", "STAFF-02"));
        assertEquals(first, service.snapshot("BED-101").assignment());
        assertEquals(OCCUPIED, service.snapshot("BED-101").status());
    }

    @Test void TC05_04_missingPatientIdentifiersAreRejected() {
        var service = service();
        var before = service.snapshot("BED-101");
        for (String id : new String[]{null, "", "   "}) {
            assertThrows(IllegalArgumentException.class,
                () -> service.assignResource("BED-101", id, "STAFF-01"));
            assertEquals(before, service.snapshot("BED-101"));
        }
    }

    @Test void TC05_05_missingStaffIdentifiersAreRejected() {
        var service = service();
        var before = service.snapshot("BED-101");
        for (String id : new String[]{null, "", "   "}) {
            assertThrows(IllegalArgumentException.class,
                () -> service.assignResource("BED-101", "SIM-001", id));
            assertEquals(before, service.snapshot("BED-101"));
        }
    }

    @Test void TC05_06_unknownResourceIsRejected() {
        var service = service();
        var before = service.snapshot("BED-101");
        assertThrows(IllegalArgumentException.class,
            () -> service.assignResource("BED-999", "SIM-001", "STAFF-01"));
        assertEquals(before, service.snapshot("BED-101"));
    }

    @Test void TC05_07_competingRequestsHaveOneWinner() throws Exception {
        var service = service();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> attempt(service, "SIM-001", ready, start));
            var second = pool.submit(() -> attempt(service, "SIM-002", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                          + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(OCCUPIED, service.snapshot("BED-101").status());
            assertNotNull(service.snapshot("BED-101").assignment());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private boolean attempt(AssignmentService service, String patient,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
        try {
            service.assignResource("BED-101", patient, "STAFF-01");
            return true;
        } catch (IllegalStateException conflict) {
            return false;
        }
    }

    @Test void TC05_08_otherResourcesRemainUnchanged() {
        var service = new AssignmentService(Map.of("BED-101", AVAILABLE,
                                                   "PUMP-201", AVAILABLE));
        var before = service.snapshot("PUMP-201");
        service.assignResource("BED-101", "SIM-001", "STAFF-01");
        assertEquals(before, service.snapshot("PUMP-201"));
    }
}
