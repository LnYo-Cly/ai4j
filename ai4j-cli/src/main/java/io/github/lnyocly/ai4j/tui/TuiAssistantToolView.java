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
public class TuiAssistantToolView {

    private String callId;

    private String toolName;

    private String status;

    private String title;

    private String detail;

    @Builder.Default
    private List<String> previewLines = new ArrayList<String>();
}
