package io.github.lnyocly.ai4j.agent.control;

/**
 * 控制流异常基类（#262）：宿主介入（审批 / 用户输入等）需要中断 agent 循环并
 * 原样抛给调用方，禁止被 {@code BaseAgentRuntime#executeTool} 降级为 TOOL_ERROR
 * 喂回模型（那会让模型"看到工具失败"后自行继续，破坏宿主暂停语义）。
 *
 * <p>受检异常：宿主调用方必须显式处理。已知子类：
 * {@code AgentApprovalRequiredException}（宿主审批暂停；deny 反馈仍走 TOOL_ERROR 让模型自适应）与
 * {@link AgentHostInputException}（宿主用户输入）。
 */
public class AgentControlFlowException extends Exception {

    public AgentControlFlowException(String message) {
        super(message);
    }

    public AgentControlFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
