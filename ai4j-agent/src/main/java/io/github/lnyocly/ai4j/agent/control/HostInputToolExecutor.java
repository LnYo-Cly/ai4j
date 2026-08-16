package io.github.lnyocly.ai4j.agent.control;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * 宿主输入型工具执行器（#262）：名单内工具阻塞等待 {@link AgentHostInputChannel}
 * 回填用户答案并作为工具结果返回；名单外工具委托原执行器。
 *
 * <p>组装示例：{@code builder.toolExecutor(new HostInputToolExecutor(baseExecutor,
 * channel, Set.of("ask_user")))}。
 */
public class HostInputToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final AgentHostInputChannel channel;
    private final Set<String> hostInputTools;

    public HostInputToolExecutor(ToolExecutor delegate, AgentHostInputChannel channel, Set<String> hostInputTools) {
        this.delegate = delegate;
        this.channel = channel;
        this.hostInputTools = hostInputTools == null ? Collections.emptySet() : hostInputTools;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null || call.getName() == null || !hostInputTools.contains(call.getName())) {
            return delegate.execute(call);
        }
        try {
            return channel.awaitUserInput(call, parseRequest(call.getArguments()));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw interruptedException;
        } catch (TimeoutException timeoutException) {
            // 可读工具结果（非 TOOL_ERROR）：模型能理解"用户未作答"并自行决定下一步。
            return "HOST_INPUT_TIMEOUT: " + (timeoutException.getMessage() == null
                    ? "user did not answer in time" : timeoutException.getMessage());
        }
    }

    private static Map<String, Object> parseRequest(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) return Collections.emptyMap();
        try {
            return JSON.parseObject(arguments);
        } catch (Exception exception) {
            return Collections.singletonMap("question", arguments);
        }
    }
}
