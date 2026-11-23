package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkProgress {

    private String workId;
    private WorkStatus status;
    private int totalTasks;
    private int doneTasks;
    private int activeTasks;
    private int blockedTasks;
    private int inReviewTasks;
    private int cancelledTasks;
    private boolean allDone;
}
