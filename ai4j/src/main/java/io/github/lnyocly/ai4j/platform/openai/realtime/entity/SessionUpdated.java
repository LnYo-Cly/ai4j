package io.github.lnyocly.ai4j.platform.openai.realtime.entity;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/10/12 17:58
 */
public class SessionUpdated {
    private String event_id;
    private String type = "session.updated";
    private Session session;
}
