package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.coding.tool.ToolExecutorDecorator;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CodingAgentOptions {

    @Builder.Default
    private boolean includeBuiltInTools = true;

    @Builder.Default
    private boolean prependWorkspaceInstructions = true;

    @Builder.Default
    private int defaultFileListMaxDepth = 4;

    @Builder.Default
    private int defaultFileListMaxEntries = 200;

    @Builder.Default
    private int defaultReadMaxChars = 12000;

    @Builder.Default
    private long defaultCommandTimeoutMs = 30000L;

    @Builder.Default
    private int defaultBashLogChars = 12000;

    @Builder.Default
    private int maxProcessOutputChars = 120000;

    @Builder.Default
    private long processStopGraceMs = 1000L;

    @Builder.Default
    private boolean autoCompactEnabled = true;

    @Builder.Default
    private int compactContextWindowTokens = 128000;

    @Builder.Default
    private int compactReserveTokens = 16384;

    @Builder.Default
    private int compactKeepRecentTokens = 20000;

    @Builder.Default
    private int compactSummaryMaxOutputTokens = 400;

    @Builder.Default
    private boolean toolResultMicroCompactEnabled = true;

    @Builder.Default
    private int toolResultMicroCompactKeepRecent = 3;

    @Builder.Default
    private int toolResultMicroCompactMaxTokens = 1200;

    @Builder.Default
    private int autoCompactMaxConsecutiveFailures = 3;

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

    private ToolExecutorDecorator toolExecutorDecorator;
}
