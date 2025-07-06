package ac.workflow.domain.enums;

public enum WorkflowStatus {
    STARTING(1L),
    RUNNING(2L),
    SUCCESS(3L),
    FAILURE(4L),
    INTERRUPTED(5L),
    PAUSED(6L);

    private final Long id;

    WorkflowStatus(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public static WorkflowStatus fromId(Long id) {
        for (WorkflowStatus status : values()) {
            if (status.id.equals(id)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status id: " + id);
    }
}