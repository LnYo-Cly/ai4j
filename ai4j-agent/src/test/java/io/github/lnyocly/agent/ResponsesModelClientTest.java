package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.listener.ResponseSseListener;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.platform.openai.response.entity.Response;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseDeleteResponse;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseRequest;
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

    private static final class CapturingResponsesService implements IResponsesService {
        private ResponseRequest lastRequest;

        @Override
        public Response create(String baseUrl, String apiKey, ResponseRequest request) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Response create(ResponseRequest request) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void createStream(String baseUrl, String apiKey, ResponseRequest request, ResponseSseListener listener) {
            lastRequest = request;
        }

        @Override
        public void createStream(ResponseRequest request, ResponseSseListener listener) {
            lastRequest = request;
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
