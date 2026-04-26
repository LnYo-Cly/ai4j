package io.github.lnyocly.ai4j.coding.loop;

import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingLoopPolicy {

    @Builder.Default
    private boolean autoContinueEnabled = true;

    @Builder.Default
    private int maxAutoFollowUps = 2;

    @Builder.Default
    private int maxTotalTurns = 6;

    @Builder.Default
    private boolean continueAfterCompact = true;

    @Builder.Default
    private boolean stopOnApprovalBlock = true;

    @Builder.Default
    private boolean stopOnExplicitQuestion = true;

    public static CodingLoopPolicy from(CodingAgentOptions options) {
        if (options == null) {
            return CodingLoopPolicy.builder().build();
        }
        return CodingLoopPolicy.builder()
                .autoContinueEnabled(options.isAutoContinueEnabled())
                .maxAutoFollowUps(options.getMaxAutoFollowUps())
                .maxTotalTurns(options.getMaxTotalTurns())
                .continueAfterCompact(options.isContinueAfterCompact())
                .stopOnApprovalBlock(options.isStopOnApprovalBlock())
                .stopOnExplicitQuestion(options.isStopOnExplicitQuestion())
                .build();
    }
}
