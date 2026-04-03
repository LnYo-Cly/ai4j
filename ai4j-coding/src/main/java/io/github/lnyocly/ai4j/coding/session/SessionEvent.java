package io.github.lnyocly.ai4j.coding.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SessionEvent {

    private String eventId;

    private String sessionId;

    private SessionEventType type;

    private long timestamp;

    private String turnId;

    private Integer step;

    private String summary;

    private Map<String, Object> payload;
}
