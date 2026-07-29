package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseDeleteResponse;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseStreamEvent;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseUsage;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseUsageDetails;
import io.github.lnyocly.ai4j.service.IResponsesService;
import org.junit.Assert;
import org.junit.Test;

public class ResponsesModelClientTest {

    @Test
    public void test_stream_propagates_stream_execution_options() throws Exception {
        CapturingResponsesService responsesService = new CapturingResponsesService();
        ResponsesModelClient client = new ResponsesModelClient(responsesService);

        client.createStream(AgentPrompt.builder()
                        .model("gpt-5-mini")
                        .streamExecution(StreamExecutionOptions.builder()
                                .firstTokenTimeoutMs(4321L)
                                .idleTimeoutMs(8765L)
                                .maxRetries(3)
                                .retryBackoffMs(120L)
                                .build())
                        .build(),
                new AgentModelStreamListener() {
                });

        Assert.assertNotNull(responsesService.lastRequest);
        Assert.assertNotNull(responsesService.lastRequest.getStreamExecution());
        Assert.assertEquals(4321L, responsesService.lastRequest.getStreamExecution().getFirstTokenTimeoutMs());
        Assert.assertEquals(8765L, responsesService.lastRequest.getStreamExecution().getIdleTimeoutMs());
        Assert.assertEquals(3, responsesService.lastRequest.getStreamExecution().getMaxRetries());
        Assert.assertEquals(120L, responsesService.lastRequest.getStreamExecution().getRetryBackoffMs());
    }

    @Test
    public void createAndStreamShouldExposeCachedUsageAndStableInstructions() throws Exception {
        CapturingResponsesService responsesService = new CapturingResponsesService();
        Response response = responseWithUsage();
        responsesService.response = response;
        responsesService.streamResponse = response;
        ResponsesModelClient client = new ResponsesModelClient(responsesService);
        AgentPrompt prompt = AgentPrompt.builder()
                .model("gpt-5-mini")
                .systemPrompt("fixed system")
                .instructions("fixed developer rules")
                .build();

        assertCachedUsage(client.create(prompt));
        assertCachedUsage(client.createStream(prompt, new AgentModelStreamListener() {
        }));
        Assert.assertEquals("fixed system\n\nfixed developer rules", responsesService.lastRequest.getInstructions());
    }

    private Response responseWithUsage() {
        ResponseUsage usage = new ResponseUsage();
        usage.setInputTokens(Integer.valueOf(100));
        usage.setOutputTokens(Integer.valueOf(20));
        usage.setTotalTokens(Integer.valueOf(120));
        ResponseUsageDetails inputDetails = new ResponseUsageDetails();
        inputDetails.setCachedTokens(Integer.valueOf(60));
        inputDetails.setCacheWriteTokens(Integer.valueOf(30));
        usage.setInputTokensDetails(inputDetails);
        ResponseUsageDetails outputDetails = new ResponseUsageDetails();
        outputDetails.setReasoningTokens(Integer.valueOf(7));
        usage.setOutputTokensDetails(outputDetails);
        Response response = new Response();
        response.setUsage(usage);
        return response;
    }

    private void assertCachedUsage(io.github.lnyocly.ai4j.agent.model.AgentModelResult result) {
        Assert.assertEquals(Long.valueOf(100L), result.getInputTokens());
        Assert.assertEquals(Long.valueOf(40L), result.getUncachedInputTokens());
        Assert.assertEquals(Long.valueOf(60L), result.getCacheReadInputTokens());
        Assert.assertEquals(Long.valueOf(30L), result.getCacheWriteInputTokens());
        Assert.assertEquals(Long.valueOf(20L), result.getOutputTokens());
        Assert.assertEquals(Long.valueOf(120L), result.getTotalTokens());
        Assert.assertEquals(Long.valueOf(7L), result.getReasoningTokens());
    }

    private static final class CapturingResponsesService implements IResponsesService {
        private ResponseRequest lastRequest;
        private Response response;
        private Response streamResponse;

        @Override
        public Response create(String baseUrl, String apiKey, ResponseRequest request) {
            lastRequest = request;
            return response;
        }

        @Override
        public Response create(ResponseRequest request) {
            lastRequest = request;
            return response;
        }

        @Override
        public void createStream(String baseUrl, String apiKey, ResponseRequest request, ResponseSseListener listener) {
            lastRequest = request;
            if (streamResponse != null) {
                ResponseStreamEvent event = new ResponseStreamEvent();
                event.setType("response.completed");
                event.setResponse(streamResponse);
                listener.accept(event);
            }
            listener.complete();
        }

        @Override
        public void createStream(ResponseRequest request, ResponseSseListener listener) {
            createStream(null, null, request, listener);
        }

        @Override
        public Response retrieve(String baseUrl, String apiKey, String responseId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Response retrieve(String responseId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ResponseDeleteResponse delete(String baseUrl, String apiKey, String responseId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public ResponseDeleteResponse delete(String responseId) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
