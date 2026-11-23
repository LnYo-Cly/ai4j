package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned declarative Agent blueprint persisted in the Harness ledger.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentBlueprintRecord {

    private String blueprintId;
    private String scopeKey;
    private String name;
    private long version;
    private String model;
    private String systemPrompt;
    private String description;
    private boolean active;
    private Long rollbackFromVersion;
    private String rollbackReason;
    private HarnessActor createdBy;
    private long createdAtEpochMs;

    @Builder.Default
    private List<String> allowedTools = new ArrayList<String>();

    @Builder.Default
    private List<String> capabilities = new ArrayList<String>();

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();

    public AgentBlueprintRecord copy() {
        return HarnessJson.copy(this, AgentBlueprintRecord.class);
    }
}
