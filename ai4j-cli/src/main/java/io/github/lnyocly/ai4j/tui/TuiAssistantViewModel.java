package io.github.lnyocly.ai4j.tui;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TuiAssistantViewModel {

    @Builder.Default
    private TuiAssistantPhase phase = TuiAssistantPhase.IDLE;

    private Integer step;

    private String phaseDetail;

    private String reasoningText;

    private String text;

    private long updatedAtEpochMs;

    @Builder.Default
    private int animationTick = 0;

    @Builder.Default
    private List<TuiAssistantToolView> tools = new ArrayList<TuiAssistantToolView>();
}
