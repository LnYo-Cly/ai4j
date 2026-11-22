package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.CallExecutionOptions;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Credentials;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.ProviderProfile;
import io.github.lnyocly.ai4j.transport.ModelTransport;
import io.github.lnyocly.ai4j.transport.TransportRequest;
import io.github.lnyocly.ai4j.protocol.WireResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared deterministic doubles for the kernel contract tests. */
final class TestFixtures {

    private TestFixtures() {
    }

    static Endpoint endpoint(Protocol protocol, String baseUrl) {
        return Endpoint.builder(protocol)
                .name("test-endpoint")
                .baseUrl(baseUrl)
                .credentials(Credentials.bearer("test-key"))
                .profile(ProviderProfile.CUSTOM)
                .build();
    }

    static CallExecutionOptions fastTimeouts() {
        return CallExecutionOptions.builder()
                .firstTokenTimeoutMs(2_000)
                .idleTimeoutMs(2_000)
                .maxRetries(0)
                .build();
    }

    /** Transport returning a fixed body; records the last request for wire assertions. */
    static final class StaticTransport implements ModelTransport {

        final int statusCode;
        final String body;
        final Map<String, String> headers;
        final AtomicInteger executions = new AtomicInteger();
        volatile TransportRequest lastRequest;

        StaticTransport(int statusCode, String body) {
            this(statusCode, body, new LinkedHashMap<String, String>());
        }

        StaticTransport(int statusCode, String body, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        @Override
        public WireResponse execute(TransportRequest request) {
            executions.incrementAndGet();
            lastRequest = request;
            return WireResponse.of(request.getWireRequest().getProtocol(), statusCode, body, headers);
        }

        @Override
        public io.github.lnyocly.ai4j.transport.CallHandle stream(TransportRequest request,
                                                                  io.github.lnyocly.ai4j.transport.SseChannel channel) {
            lastRequest = request;
            throw new UnsupportedOperationException("streaming not used in this test");
        }
    }
}
