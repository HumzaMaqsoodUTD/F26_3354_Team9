package occupan.domain;

/** Class diagram: Assignment. One row per assignment, active until released. */
public final class AssignmentRecord {
    private final int assignmentId;
    private final String resourceId;
    private final String patientId;
    private final String operationalNeed;
    private final String assignedBy;
    private final String assignedAt;
    private String releasedAt;
    private String releasedBy;

    public AssignmentRecord(int assignmentId, String resourceId, String patientId, String operationalNeed,
                            String assignedBy, String assignedAt) {
        this.assignmentId = assignmentId;
        this.resourceId = resourceId;
        this.patientId = patientId;
        this.operationalNeed = operationalNeed;
        this.assignedBy = assignedBy;
        this.assignedAt = assignedAt;
    }

    public int assignmentId() { return assignmentId; }
    public String resourceId() { return resourceId; }
    public String patientId() { return patientId; }
    public String operationalNeed() { return operationalNeed; }
    public String assignedBy() { return assignedBy; }
    public String assignedAt() { return assignedAt; }
    public String releasedAt() { return releasedAt; }
    public String releasedBy() { return releasedBy; }
    public boolean active() { return releasedAt == null; }

    /** What the assignment is for: the patient ID or the operational need. */
    public String subject() { return patientId != null ? patientId : operationalNeed; }

    public void release(String userId, String when) {
        this.releasedBy = userId;
        this.releasedAt = when;
    }
}
