package io.github.lnyocly.ai4j.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ServiceKey;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.memory.InMemoryVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Cordis extension entry point for {@code ai4j-rag}.
 *
 * <p>Provides {@link RagService}, {@link VectorStore}, and {@link RagContextProjector} services,
 * registers the {@code rag_search} extension tool, and binds a {@link Disposable} effect so
 * disabling or closing the {@link io.github.lnyocly.ai4j.extension.ExtensionRegistry} shuts down
 * any {@link AutoCloseable} RAG resources cleanly.</p>
 */
public final class RagExtension implements Ai4jExtension {

    private static final Logger log = LoggerFactory.getLogger(RagExtension.class);

    public static final String EXTENSION_ID = "rag";
    public static final String SEARCH_TOOL_NAME = "rag_search";
    public static final String NAMESPACED_SEARCH_TOOL_NAME = "plugin__" + EXTENSION_ID + "__" + SEARCH_TOOL_NAME;

    public static final ServiceKey<RagService> RAG_SERVICE_KEY =
            ServiceKey.of("ai4j.rag.service", RagService.class);
    public static final ServiceKey<VectorStore> VECTOR_STORE_KEY =
            ServiceKey.of("ai4j.rag.vectorStore", VectorStore.class);
    public static final ServiceKey<RagContextProjector> CONTEXT_PROJECTOR_KEY =
            ServiceKey.of("ai4j.rag.contextProjector", RagContextProjector.class);

    private static final String VERSION = "2.8.2";
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private final Supplier<RagService> ragServiceFactory;
    private final Supplier<VectorStore> vectorStoreFactory;
    private final Supplier<RagContextProjector> contextProjectorFactory;

    public RagExtension() {
        this(
                new Supplier<RagService>() {
                    @Override
                    public RagService get() {
                        return new DefaultRagService(new EmptyRetriever());
                    }
                },
                new Supplier<VectorStore>() {
                    @Override
                    public VectorStore get() {
                        return new InMemoryVectorStore();
                    }
                },
                new Supplier<RagContextProjector>() {
                    @Override
                    public RagContextProjector get() {
                        return new RagContextProjector();
                    }
                }
        );
    }

    public RagExtension(final RagService ragService, final VectorStore vectorStore) {
        this(ragService, vectorStore, new RagContextProjector());
    }

    public RagExtension(
            final RagService ragService,
            final VectorStore vectorStore,
            final RagContextProjector contextProjector) {
        this(
                fixedSupplier("ragService", ragService),
                fixedSupplier("vectorStore", vectorStore),
                fixedSupplier("contextProjector", contextProjector)
        );
    }

    public RagExtension(
            Supplier<RagService> ragServiceFactory,
            Supplier<VectorStore> vectorStoreFactory,
            Supplier<RagContextProjector> contextProjectorFactory) {
        if (ragServiceFactory == null) {
            throw new IllegalArgumentException("ragServiceFactory must not be null");
        }
        if (vectorStoreFactory == null) {
            throw new IllegalArgumentException("vectorStoreFactory must not be null");
        }
        if (contextProjectorFactory == null) {
            throw new IllegalArgumentException("contextProjectorFactory must not be null");
        }
        this.ragServiceFactory = ragServiceFactory;
        this.vectorStoreFactory = vectorStoreFactory;
        this.contextProjectorFactory = contextProjectorFactory;
    }

    @Override
    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id(EXTENSION_ID)
                .name("AI4J RAG")
                .version(VERSION)
                .vendor("ai4j")
                .capability(ExtensionCapability.TOOL)
                .permission("rag.search")
                .configPrefix("ai4j.extensions.rag")
                .build();
    }

    @Override
    public void apply(ExtensionContext context) {
        final RagService ragService = ragServiceFactory.get();
        if (ragService == null) {
            throw new IllegalStateException("ragServiceFactory returned null");
        }
        final VectorStore vectorStore = vectorStoreFactory.get();
        if (vectorStore == null) {
            throw new IllegalStateException("vectorStoreFactory returned null");
        }
        final RagContextProjector projector = contextProjectorFactory.get();
        if (projector == null) {
            throw new IllegalStateException("contextProjectorFactory returned null");
        }

        final Object serviceRef = ragService;
        final Object storeRef = vectorStore;
        final AtomicBoolean disposed = new AtomicBoolean(false);
        context.effect(new Disposable() {
            @Override
            public void dispose() {
                if (disposed.compareAndSet(false, true)) {
                    closeQuietly(ragService, "RagService");
                    if (storeRef != serviceRef) {
                        closeQuietly(vectorStore, "VectorStore");
                    }
                }
            }
        });

        context.provide(RAG_SERVICE_KEY, ragService);
        context.provide(VECTOR_STORE_KEY, vectorStore);
        context.provide(CONTEXT_PROJECTOR_KEY, projector);

        context.tools().register(searchToolSpec(), new ExtensionToolExecutor() {
            @Override
            public String execute(ExtensionToolCall call) throws Exception {
                Map<String, Object> args = parseArguments(call);
                String queryText = stringValue(args.get("query"));
                if (queryText == null) {
                    throw new IllegalArgumentException("query is required");
                }
                RagQuery.RagQueryBuilder queryBuilder = RagQuery.builder().query(queryText);
                String dataset = stringValue(args.get("dataset"));
                if (dataset != null) {
                    queryBuilder.dataset(dataset);
                }
                Integer topK = intValue(args.get("topK"));
                if (topK != null && topK.intValue() > 0) {
                    queryBuilder.topK(topK);
                }
                Object includeCitationsArg = args.get("includeCitations");
                if (includeCitationsArg != null) {
                    queryBuilder.includeCitations(parseBoolean(includeCitationsArg, true));
                }

                RagResult result = ragService.search(queryBuilder.build());
                Map<String, Object> response = new LinkedHashMap<String, Object>();
                response.put("query", result == null ? queryText : result.getQuery());
                response.put("context", result == null ? "" : result.getContext());
                response.put("hits", result == null || result.getHits() == null
                        ? Collections.emptyList()
                        : result.getHits());
                response.put("citations", result == null || result.getCitations() == null
                        ? Collections.emptyList()
                        : result.getCitations());
                return JsonCodec.toJson(response);
            }
        });
    }

    public static ExtensionToolSpec searchToolSpec() {
        return ExtensionToolSpec.builder()
                .name(SEARCH_TOOL_NAME)
                .description("Search the configured RAG knowledge base and return grounded context and citations.")
                .inputSchema("{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"Search query\"},"
                        + "\"dataset\":{\"type\":\"string\",\"description\":\"Optional target dataset name\"},"
                        + "\"topK\":{\"type\":\"integer\",\"description\":\"Maximum number of retrieved hits\"},"
                        + "\"includeCitations\":{\"type\":\"boolean\",\"description\":\"Whether to include structured citations\"}"
                        + "},\"required\":[\"query\"]}")
                .build();
    }

    private static <T> Supplier<T> fixedSupplier(final String name, final T instance) {
        if (instance == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return new Supplier<T>() {
            @Override
            public T get() {
                return instance;
            }
        };
    }

    private static Map<String, Object> parseArguments(ExtensionToolCall call) {
        String raw = call == null ? null : call.getArguments();
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> parsed = JsonCodec.fromJsonLenient(raw, MAP_TYPE);
        return parsed == null ? Collections.<String, Object>emptyMap() : parsed;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        if (value instanceof String) {
            try {
                return Integer.valueOf(Integer.parseInt(((String) value).trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof String) {
            String trimmed = ((String) value).trim();
            if ("true".equalsIgnoreCase(trimmed)) {
                return true;
            }
            if ("false".equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        return defaultValue;
    }

    private static void closeQuietly(Object target, String label) {
        if (!(target instanceof AutoCloseable)) {
            return;
        }
        try {
            ((AutoCloseable) target).close();
        } catch (Exception e) {
            log.warn("Failed to close {} during RagExtension disposal", label, e);
        }
    }

    private static final class EmptyRetriever implements Retriever {
        @Override
        public List<RagHit> retrieve(RagQuery query) {
            return Collections.emptyList();
        }
    }
}
