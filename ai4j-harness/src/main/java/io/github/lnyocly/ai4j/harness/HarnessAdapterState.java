package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Durable state owned by a Harness execution adapter.
 *
 * <p>The Harness ledger stores this as an opaque, adapter-owned payload. The
 * ledger can therefore coordinate any Agent runtime without pretending that
 * every runtime has the same session format.</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessAdapterState {

    private String adapterType;

    private String sessionId;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<String, Object>();

    public HarnessAdapterState copy() {
        return HarnessJson.copy(this, HarnessAdapterState.class);
    }
}
