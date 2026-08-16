package io.github.lnyocly.ai4j.agent.control;

import java.util.Collections;
import java.util.Map;

/**
 * 宿主用户输入请求（#262）：工具抛出本异常中断 agent 循环，宿主捕获后渲染问卷、
 * 收集答案，再以新输入恢复回合——适合"暂停-恢复新回合"型宿主。
 *
 * <p>需要"工具结果 = 用户答案"的阻塞式语义（Claude Code AskUserQuestion 同构），
 * 用 {@link HostInputToolExecutor} + {@link AgentHostInputChannel}。
 */
public class AgentHostInputException extends AgentControlFlowException {

    private final Map<String, Object> request;

    public AgentHostInputException(String message, Map<String, Object> request) {
        super(message);
        this.request = request == null ? Collections.emptyMap() : request;
    }

    /** 宿主工具的原生异常作为 cause 携带，宿主边界可无损还原。 */
    public AgentHostInputException(String message, Map<String, Object> request, Throwable cause) {
        super(message, cause);
        this.request = request == null ? Collections.emptyMap() : request;
    }

    /** 结构化问卷请求（questions/choices/multiple 等，由宿主工具定义）。 */
    public Map<String, Object> getRequest() {
        return request;
    }
}
