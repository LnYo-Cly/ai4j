package io.github.lnyocly.ai4j.harness.fleet;

import io.github.lnyocly.ai4j.harness.ContentPinnedConsentSpec;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ConsentRequest {

    private ContentPinnedConsentSpec spec;
    private HarnessActor actor;
}
