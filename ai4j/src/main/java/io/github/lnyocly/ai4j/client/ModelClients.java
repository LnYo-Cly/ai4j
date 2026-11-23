package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.protocol.Endpoint;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptor;
import io.github.lnyocly.ai4j.protocol.ProtocolDescriptors;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.transport.ModelTransport;
import io.github.lnyocly.ai4j.transport.OkHttpModelTransport;

import java.util.Objects;

/**
 * Assembly entry point for the new chain (design 4.1).
 *
 * <p>{@code ModelClients.of} only wires pieces together: endpoint → matching
 * {@link ProtocolDescriptor} (codec + capability baseline) → {@link ModelTransport}.
 * There is no vendor enum and no factory switch; a standard-shape vendor
 * onboards by declaring an endpoint (design A1).</p>
 */
public final class ModelClients {

    private ModelClients() {
    }

    /**
     * Assemble a client over the shared {@link Configuration} OkHttp stack.
     *
     * @param endpoint      protocol + base URL + credential + profile
     * @param configuration shared configuration; its {@code OkHttpClient} and
     *                      {@code EventSource.Factory} are reused (design C4)
     */
    public static ModelClient of(Endpoint endpoint, Configuration configuration) {
        Objects.requireNonNull(endpoint, "endpoint is required");
        Objects.requireNonNull(configuration, "configuration is required");
        ModelTransport transport = new OkHttpModelTransport(
                configuration.getOkHttpClient(), configuration.createRequestFactory());
        return of(endpoint, transport);
    }

    /** Assemble a client over an explicit transport (used by tests). */
    public static ModelClient of(Endpoint endpoint, ModelTransport transport) {
        Objects.requireNonNull(endpoint, "endpoint is required");
        ProtocolDescriptor descriptor = ProtocolDescriptors.of(endpoint.getProtocol());
        return new DefaultModelClient(endpoint, descriptor, transport);
    }
}
