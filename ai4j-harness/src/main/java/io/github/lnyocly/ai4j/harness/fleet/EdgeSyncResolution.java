package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.HarnessEntityRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EdgeSyncResolution {

    public enum Action {
        UNCHANGED,
        PULLED_FROM_CENTER,
        PUSHED_TO_CENTER,
        THREE_WAY_MERGED,
        CONFLICT
    }

    private String entityType;
    private String entityId;
    private Action action;
    private HarnessEntityRecord resultingEntity;
}
