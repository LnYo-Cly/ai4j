package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.util.ResponseUtil;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.service.IResponsesService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ResponsesModelClient implements AgentModelClient {

    private static final ConcurrentMap<Thread, ResponseSseListener> ACTIVE_STREAMS =
            new ConcurrentHashMap<Thread, ResponseSseListener>();

    private final IResponsesService responsesService;
    private final String baseUrl;
    private final String apiKey;

    public ResponsesModelClient(IResponsesService responsesService) {
        this(responsesService, null, null);
    }

    public ResponsesModelClient(IResponsesService responsesService, String baseUrl, String apiKey) {
        this.responsesService = responsesService;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) throws Exception {
        ResponseRequest request = toResponseRequest(prompt, false);
        Response response = responsesService.create(baseUrl, apiKey, request);
        return toModelResult(response);
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception {
        ResponseRequest request = toResponseRequest(prompt, true);
        ResponseSseListener sseListener = new ResponseSseListener() {
            @Override
            protected void onEvent() {
                if (listener != null && !getCurrText().isEmpty()) {
                    listener.onDeltaText(getCurrText());
                }
            }

            @Override
            protected void error(Throwable t, okhttp3.Response response) {
                if (listener != null) {
                    listener.onError(t);
                }
            }

            @Override
            protected void retry(Throwable t, int attempt, int maxAttempts) {
                if (listener == null) {
                    return;
                }
                String message = t == null || t.getMessage() == null || t.getMessage().trim().isEmpty()
                        ? "Retrying model stream"
                        : t.getMessage().trim();
                listener.onRetry(message, attempt, maxAttempts, t);
            }
        };
        Thread currentThread = Thread.currentThread();
        ACTIVE_STREAMS.put(currentThread, sseListener);
        try {
            responsesService.createStream(baseUrl, apiKey, request, sseListener);
            throwIfInterrupted(sseListener);
            sseListener.dispatchFailure();
            Response response = sseListener.getResponse();
            AgentModelResult result = toModelResult(response);
            throwIfInterrupted(sseListener);
            if (listener != null) {
                listener.onComplete(result);
            }
            return result;
        } catch (InterruptedException ex) {
            sseListener.cancelStream();
            Thread.currentThread().interrupt();
            throw ex;
        } catch (Exception ex) {
            if (currentThread.isInterrupted()) {
                sseListener.cancelStream();
            }
            throw ex;
        } finally {
            ACTIVE_STREAMS.remove(currentThread, sseListener);
        }
    }

    public static void cancelActiveStream(Thread thread) {
        if (thread == null) {
            return;
        }
        ResponseSseListener listener = ACTIVE_STREAMS.get(thread);
        if (listener != null) {
            listener.cancelStream();
        }
    }

    private void throwIfInterrupted(ResponseSseListener sseListener) throws InterruptedException {
        if (!Thread.currentThread().isInterrupted()) {
            return;
        }
        sseListener.cancelStream();
        throw new InterruptedException("Model stream interrupted");
    }

    private ResponseRequest toResponseRequest(AgentPrompt prompt, boolean forceStream) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt is required");
        }
        ResponseRequest.ResponseRequestBuilder builder = ResponseRequest.builder();
        builder.model(prompt.getModel());
        builder.input(buildItems(prompt));
        builder.tools(prompt.getTools());
        builder.toolChoice(prompt.getToolChoice());
        builder.parallelToolCalls(prompt.getParallelToolCalls());
        builder.temperature(prompt.getTemperature());
        builder.topP(prompt.getTopP());
        builder.maxOutputTokens(prompt.getMaxOutputTokens());
        builder.reasoning(prompt.getReasoning());
        builder.store(prompt.getStore());
        builder.user(prompt.getUser());

        Boolean stream = prompt.getStream();
        if (stream == null) {
            stream = forceStream;
        }
        builder.stream(stream);
        builder.streamExecution(prompt.getStreamExecution());

        if (prompt.getSystemPrompt() != null && !prompt.getSystemPrompt().trim().isEmpty()) {
            builder.instructions(prompt.getSystemPrompt());
        }

        Map<String, Object> extraBody = prompt.getExtraBody();
        if (extraBody != null) {
            builder.extraBody(extraBody);
        }
        return builder.build();
    }

    private AgentModelResult toModelResult(Response response) {
        List<AgentToolCall> calls = ResponseUtil.extractToolCalls(response);
        List<Object> memoryItems = new ArrayList<>();
        if (response != null && response.getOutput() != null) {
            memoryItems.addAll(response.getOutput());
        }
        return AgentModelResult.builder()
                .outputText(ResponseUtil.extractOutputText(response))
                .toolCalls(calls)
                .memoryItems(memoryItems)
                .rawResponse(response)
                .build();
    }

    private List<Object> buildItems(AgentPrompt prompt) {
        List<Object> items = new ArrayList<>();
        if (prompt.getItems() != null) {
            items.addAll(prompt.getItems());
        }
        if (prompt.getInstructions() != null && !prompt.getInstructions().trim().isEmpty()) {
            items.add(0, io.github.lnyocly.ai4j.agent.util.AgentInputItem.systemMessage(prompt.getInstructions()));
        }
        return items;
    }
}
