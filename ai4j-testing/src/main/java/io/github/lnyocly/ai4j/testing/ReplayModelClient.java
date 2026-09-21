package io.github.lnyocly.ai4j.testing;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Plays a {@link ModelFixture} back through the real agent runtime: each model invocation
 * returns the fixture's next recorded response, and each invocation's prompt is checked against
 * the exchange's optional {@code expectedPromptContains} pins before the response is served.
 *
 * <pre>{@code
 * ReplayModelClient model = ReplayModelClient.load(
 *         ReplayModelClientTest.class.getResourceAsStream("/fixtures/weather.json"));
 *
 * Agent agent = Agents.react()
 *         .modelClient(model)
 *         .toolRegistry(registry)
 *         .toolExecutor(tools)
 *         .build();
 * agent.newSession().run("weather in shanghai?");
 * }</pre>
 *
 * <p>A pin mismatch fails the invocation with {@link IllegalStateException} naming the exchange
 * index and the missing substring — the golden contract is "the runtime sent what the fixture
 * pinned", so a drifted prompt fails where the model call happens, not in a later assertion.</p>
 *
 * <p>Inherits script exhaustion semantics from {@link ScriptedModelClient}: invocations past the
 * last exchange return {@link ModelResults#empty()} unless {@code failWhenExhausted(true)} was set.</p>
 */
public class ReplayModelClient extends ScriptedModelClient {

    private final ModelFixture fixture;
    private int position;

    public ReplayModelClient(ModelFixture fixture) {
        if (fixture == null) {
            throw new IllegalArgumentException("fixture is required");
        }
        this.fixture = fixture;
        for (ModelFixture.Exchange exchange : fixture.getExchanges()) {
            enqueue(exchange.getResponse());
        }
    }

    /** Loads a fixture file and returns a client primed with its exchanges. */
    public static ReplayModelClient load(Path file) throws IOException {
        return new ReplayModelClient(ModelFixture.load(file));
    }

    /** Loads a fixture from a stream (e.g. classpath resource); the stream is consumed but not closed. */
    public static ReplayModelClient load(InputStream in) throws IOException {
        return new ReplayModelClient(ModelFixture.load(in));
    }

    /** Parses fixture JSON text and returns a primed client. */
    public static ReplayModelClient parse(String json) {
        return new ReplayModelClient(ModelFixture.parse(json));
    }

    public ModelFixture getFixture() {
        return fixture;
    }

    @Override
    public AgentModelResult create(AgentPrompt prompt) {
        assertPrompt(prompt);
        return super.create(prompt);
    }

    @Override
    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
        assertPrompt(prompt);
        return super.createStream(prompt, listener);
    }

    private void assertPrompt(AgentPrompt prompt) {
        int index = position++;
        if (index >= fixture.getExchanges().size()) {
            return;
        }
        ModelFixture.Exchange exchange = fixture.getExchanges().get(index);
        if (exchange.getExpectedPromptContains().isEmpty()) {
            return;
        }
        String rendered = JSON.toJSONString(prompt);
        for (String expected : exchange.getExpectedPromptContains()) {
            if (!rendered.contains(expected)) {
                throw new IllegalStateException("Replay fixture '" + fixture.getName()
                        + "' exchange[" + index + "] expected prompt to contain '" + expected
                        + "' but it was absent");
            }
        }
    }
}
