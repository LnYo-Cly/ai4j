package io.github.lnyocly.ai4j.listener;

import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseStreamEvent;
import lombok.Getter;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;


public abstract class ResponseSseListener extends EventSourceListener {

    
    protected void error(Throwable t, okhttp3.Response response) {
    }

    
    protected abstract void onEvent();

    @Getter
    private final List<ResponseStreamEvent> events = new ArrayList<>();

    @Getter
    private ResponseStreamEvent currEvent;

    @Getter
    private final Response response = new Response();

    @Getter
    private final StringBuilder outputText = new StringBuilder();

    @Getter
    private final StringBuilder reasoningSummary = new StringBuilder();

    @Getter
    private final StringBuilder functionArguments = new StringBuilder();

    @Getter
    private String currText = "";

    @Getter
    private String currFunctionArguments = "";

    @Getter
    private CountDownLatch countDownLatch = new CountDownLatch(1);

    public void accept(ResponseStreamEvent event) {
        this.currEvent = event;
        this.events.add(event);
        this.currText = "";
        this.currFunctionArguments = "";
        applyEvent(event);
        this.onEvent();
    }

    private void applyEvent(ResponseStreamEvent event) {
        if (event == null) {
            return;
        }
        if (event.getResponse() != null) {
            mergeResponse(event.getResponse());
        }
        String type = event.getType();
        if (type == null) {
            return;
        }

        switch (type) {
            case "response.output_text.delta":
                if (event.getDelta() != null) {
                    outputText.append(event.getDelta());
                    currText = event.getDelta();
                }
                break;
            case "response.output_text.done":
                if (outputText.length() == 0 && event.getText() != null) {
                    outputText.append(event.getText());
                    currText = event.getText();
                }
                break;
            case "response.reasoning_summary_text.delta":
                if (event.getDelta() != null) {
                    reasoningSummary.append(event.getDelta());
                }
                break;
            case "response.reasoning_summary_text.done":
                if (reasoningSummary.length() == 0 && event.getText() != null) {
                    reasoningSummary.append(event.getText());
                }
                break;
            case "response.function_call_arguments.delta":
                if (event.getDelta() != null) {
                    functionArguments.append(event.getDelta());
                    currFunctionArguments = event.getDelta();
                }
                break;
            case "response.function_call_arguments.done":
                if (functionArguments.length() == 0 && event.getArguments() != null) {
                    functionArguments.append(event.getArguments());
                    currFunctionArguments = event.getArguments();
                }
                break;
            default:
                break;
        }
    }

    private void mergeResponse(Response source) {
        if (source == null) {
            return;
        }
        if (source.getId() != null) {
            response.setId(source.getId());
        }
        if (source.getObject() != null) {
            response.setObject(source.getObject());
        }
        if (source.getCreatedAt() != null) {
            response.setCreatedAt(source.getCreatedAt());
        }
        if (source.getModel() != null) {
            response.setModel(source.getModel());
        }
        if (source.getStatus() != null) {
            response.setStatus(source.getStatus());
        }
        if (source.getOutput() != null) {
            response.setOutput(source.getOutput());
        }
        if (source.getError() != null) {
            response.setError(source.getError());
        }
        if (source.getIncompleteDetails() != null) {
            response.setIncompleteDetails(source.getIncompleteDetails());
        }
        if (source.getInstructions() != null) {
            response.setInstructions(source.getInstructions());
        }
        if (source.getMaxOutputTokens() != null) {
            response.setMaxOutputTokens(source.getMaxOutputTokens());
        }
        if (source.getPreviousResponseId() != null) {
            response.setPreviousResponseId(source.getPreviousResponseId());
        }
        if (source.getUsage() != null) {
            response.setUsage(source.getUsage());
        }
        if (source.getMetadata() != null) {
            response.setMetadata(source.getMetadata());
        }
        if (source.getContextManagement() != null) {
            response.setContextManagement(source.getContextManagement());
        }
    }

    public void complete() {
        countDownLatch.countDown();
        countDownLatch = new CountDownLatch(1);
    }

    public void onError(Throwable t, okhttp3.Response response) {
        this.error(t, response);
    }

    @Override
    public void onFailure(@NotNull okhttp3.sse.EventSource eventSource, @Nullable Throwable t, @Nullable okhttp3.Response response) {
        this.error(t, response);
        complete();
    }
}

