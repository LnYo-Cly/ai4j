package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class GateResult {

    private String name;
    private GateStatus status;
    private String reason;

    public static GateResult pass(String name) {
        return GateResult.builder().name(name).status(GateStatus.PASS).reason("passed").build();
    }

    public static GateResult fail(String name, String reason) {
        return GateResult.builder().name(name).status(GateStatus.FAIL).reason(reason).build();
    }

    public boolean isPassed() {
        return GateStatus.PASS.equals(status);
    }
}
