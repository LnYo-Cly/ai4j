package io.github.lnyocly.ai4j.agent.control;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.Map;

/**
 * 阻塞式宿主输入通道（#262，Claude Code AskUserQuestion 语义）：工具执行阻塞等待
 * 宿主收集用户答案，{@link #awaitUserInput} 的返回值即工具结果，模型在同一回合内
 * 拿到真实答案继续。
 *
 * <p>实现方约定：
 * <ul>
 *   <li>阻塞直至答案就绪；返回值即工具输出（建议为可读文本）；</li>
 *   <li>超时抛 {@link java.util.concurrent.TimeoutException} →
 *       {@link HostInputToolExecutor} 转为可读工具结果（模型可自行处理）；</li>
 *   <li>取消/中断抛 {@link InterruptedException} → 中断整个 run；</li>
 *   <li>其余异常原样抛出；{@link AgentControlFlowException} 会按控制流穿透。</li>
 * </ul>
 */
public interface AgentHostInputChannel {

    /**
     * @param call    触发输入的宿主工具调用（含 callId，宿主可用作问卷关联键）
     * @param request 工具参数解析出的结构化问卷请求
     * @return 用户答案（即工具结果）
     */
    String awaitUserInput(AgentToolCall call, Map<String, Object> request) throws Exception;
}
