package io.github.lnyocly.ai4j.harness;

import org.junit.Assert;
import org.junit.Test;
import java.util.Collections;

public class HarnessAcceptanceProtocolTest {
    @Test public void distinguishesClaimFromStructuredAcceptanceAndFindings() {
        HarnessAcceptanceResult result = HarnessAcceptanceResult.builder()
                .checkId("files")
                .status(HarnessAcceptanceStatus.FAIL)
                .summary("required artifact is missing")
                .findings(Collections.singletonList(HarnessAcceptanceFinding.builder().requirementId("final").message("missing").repairHint("create it").build()))
                .build();
        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.isTerminalFailure());
        Assert.assertEquals("final", result.getFindings().get(0).getRequirementId());
    }
}
