package occupan.domain;

import static occupan.AssignmentService.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import occupan.AssignmentService.Snapshot;
import org.junit.jupiter.api.Test;

public class RulesTest {
    private static void assertValidation(Runnable action, String messagePart) {
        var e = assertThrows(DomainException.class, action::run);
        assertEquals(400, e.statusCode());
        assertTrue(e.getMessage().toLowerCase().contains(messagePart.toLowerCase()), e.getMessage());
    }

    @Test void R01_patientIdMustBeSimulated() {
        assertEquals("SIM-12345", Rules.validatePatientId(" sim-12345 "));
        assertValidation(() -> Rules.validatePatientId("Jane Doe"), "simulated");
        assertValidation(() -> Rules.validatePatientId("SIM-12"), "simulated");
        assertValidation(() -> Rules.validatePatientId(null), "simulated");
    }

    @Test void R02_operationalNeedIsTrimmedAndLimited() {
        assertEquals("Staff training", Rules.validateOperationalNeed("  Staff training "));
        assertValidation(() -> Rules.validateOperationalNeed("   "), "empty");
        assertValidation(() -> Rules.validateOperationalNeed("x".repeat(121)), "120");
    }

    @Test void R03_resourceFieldsAreNormalized() {
        assertArrayEquals(new String[]{"BED-900", "Ward C"}, Rules.validateResourceFields(" bed-900 ", " Ward C "));
        assertValidation(() -> Rules.validateResourceFields("BED 900", "Ward"), "Resource ID");
        assertValidation(() -> Rules.validateResourceFields("", "Ward"), "Resource ID");
        assertValidation(() -> Rules.validateResourceFields("BED-1", " "), "Location");
    }

    @Test void R04_manualStatusChangeRules() {
        assertDoesNotThrow(() -> Rules.checkManualStatusChange(false, AVAILABLE, CLEANING, false));
        assertValidation(() -> Rules.checkManualStatusChange(false, AVAILABLE, OCCUPIED, false), "assigning");
        assertValidation(() -> Rules.checkManualStatusChange(false, OCCUPIED, CLEANING, true), "Release");
        assertValidation(() -> Rules.checkManualStatusChange(false, CLEANING, CLEANING, false), "already");
        assertValidation(() -> Rules.checkManualStatusChange(true, AVAILABLE, CLEANING, false), "Retired");
    }

    @Test void R05_releaseCannotChooseOccupied() {
        assertDoesNotThrow(() -> Rules.checkReleaseStatus(CLEANING));
        assertValidation(() -> Rules.checkReleaseStatus(OCCUPIED), "other than Occupied");
    }

    @Test void R06_rejectionReasonsAreReadable() {
        var assigned = new Snapshot(OCCUPIED, new occupan.AssignmentService.Assignment("BED-1", "SIM-0001", "u"));
        assertEquals("Resource already has an active assignment.", Rules.explainAssignRejection(assigned));
        assertEquals("Resource is Cleaning, not Available.",
                Rules.explainAssignRejection(new Snapshot(CLEANING, null)));
    }

    @Test void R07_statusLabelsRoundTrip() {
        for (var s : occupan.AssignmentService.Status.values()) {
            assertEquals(s, Statuses.parse(Statuses.label(s)));
        }
        assertValidation(() -> Statuses.parse("available"), "Status must be one of");
    }

    @Test void R08_rolesAndPermissions() {
        var admin = new User("1", "admin", "Admin", Role.ADMINISTRATOR);
        var nurse = new User("2", "nurse", "Nurse", Role.CLINICAL_STAFF);
        assertTrue(admin.can("resource.create"));
        assertFalse(nurse.can("resource.create"));
        assertTrue(nurse.can("assign"));
        assertEquals(403, assertThrows(DomainException.class, () -> nurse.require("resource.retire")).statusCode());
    }
}
