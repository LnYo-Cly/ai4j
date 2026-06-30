package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

/**
 * Wraps an {@link A2AClient} as a {@link ToolExecutor} so an ai4j agent can call an external A2A
 * agent as a tool. The tool takes a {@code message} argument, sends it to the configured A2A
 * endpoint, and returns the response text. Register alongside the agent's other tools.
 *
 * <pre>
 * A2ATool a2a = new A2ATool("https://other-agent.example.com");
 * Agent agent = Agents.react()
 *         .modelClient(model).model("m")
 *         .toolExecutor(a2a)
 *         .toolRegistry(a2a.asToolRegistry())
 *         .build();
 * </pre>
 */
public class A2ATool implements ToolExecutor {

    private final String agentUrl;
    private final A2AClient client;

    public A2ATool(String agentUrl) {
        this(agentUrl, new A2AClient());
    }

    public A2ATool(String agentUrl, A2AClient client) {
        this.agentUrl = agentUrl;
        this.client = client;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        String message = extractMessage(call);
        return client.sendTask(agentUrl, message);
    }

    private static String extractMessage(AgentToolCall call) {
        if (call == null || call.getArguments() == null) {
            return "";
        }
        try {
            JSONObject args = JSON.parseObject(call.getArguments());
            String msg = args == null ? null : args.getString("message");
            return msg == null ? call.getArguments() : msg;
        } catch (Exception e) {
            return call.getArguments(); // treat raw arguments as the message
        }
    }
}
