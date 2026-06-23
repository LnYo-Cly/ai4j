package io.github.lnyocly.ai4j.platform.anthropic.stream;

/**
 * Anthropic Messages 原生流式事件回调。
 * <p>
 * 由 {@code AnthropicMessagesService.messagesStream(...)} 在解析 Anthropic SSE 事件后调用，
 * 把原生语义（text / thinking / tool_use / stop_reason / usage）以类型化回调暴露，调用方无需再解析 SSE。
 * 所有方法默认空实现，按需覆盖。
 */
public interface AnthropicStreamHandler {

    /** message_start：消息 id 与模型名。 */
    default void onStart(String messageId, String model) {
    }

    /** content_block_delta(text_delta)：正文文本增量。 */
    default void onDeltaText(String text) {
    }

    /** content_block_delta(thinking_delta)：思考内容增量（开 thinking 时）。 */
    default void onThinkingDelta(String thinking) {
    }

    /** content_block_start(tool_use)：开始一个工具调用块。 */
    default void onToolUseStart(int index, String toolUseId, String name) {
    }

    /** content_block_delta(input_json_delta)：工具入参 JSON 片段。 */
    default void onToolUseDelta(int index, String partialJson) {
    }

    /** content_block_stop(tool_use)：工具调用块完成，给出完整入参 JSON。 */
    default void onToolUseComplete(int index, String toolUseId, String name, String inputJson) {
    }

    /** message_delta：停止原因与本段用量（output_tokens 为本段增量）。 */
    default void onStopReason(String stopReason, long inputTokens, long outputTokens) {
    }

    /** message_stop：流结束。 */
    default void onComplete() {
    }

    /** 任意异常。 */
    default void onError(Throwable t) {
    }
}
