package ac.workflow.domain.enums;

public enum TaskStatus {
    STARTING(1L),
    RUNNING(2L),
    SUCCESS(3L),
    FAILURE(4L),
    SKIPPED(5L),
    INTERRUPTED(6L);

    private final Long id;

    TaskStatus(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public static TaskStatus fromId(Long id) {
        for (TaskStatus status : values()) {
            if (status.id.equals(id)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status id: " + id);
    }
}