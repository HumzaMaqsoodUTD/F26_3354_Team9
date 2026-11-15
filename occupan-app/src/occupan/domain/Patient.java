package occupan.domain;

import java.util.regex.Pattern;

/**
 * Class diagram: Patient. Only simulated identifiers like SIM-12345 are accepted (never real patient
 * data), so a patient is just that identifier; no other patient details are stored.
 */
public record Patient(String patientId) {
    private static final Pattern PATTERN = Pattern.compile("^SIM-\\d{4,8}$");

    public static String validateIdentifier(String identifier) {
        String cleaned = identifier == null ? "" : identifier.strip().toUpperCase();
        if (!PATTERN.matcher(cleaned).matches()) {
            throw DomainException.validation("Patient identifier must be a simulated ID like SIM-12345.");
        }
        return cleaned;
    }
}
