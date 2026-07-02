package io.github.lnyocly.ai4j.agent.rag;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.rag.RagQuery;
import io.github.lnyocly.ai4j.rag.RagResult;
import io.github.lnyocly.ai4j.rag.RagService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 把 {@link RagService} 包成一个 agent tool：agent 调用此 tool 时检索知识库，
 * 返回组装好的上下文（带 [S1]/[S2] 引用）给模型。
 *
 * <p>关键价值：一旦 RAG 作为 agent tool，检索/重排就是 agent 的一个 TOOL 节点，
 * 会被 {@code IoCaptureAgentListener} 自动捕获 → 进入 agent 的统一可观测链路
 * （重放/恢复/审计），不再与 agent 可观测割裂。
 *
 * <p>用法：
 * <pre>{@code
 *   RagTool ragTool = RagTool.builder(ragService)
 *           .dataset("ecommerce-kb")
 *           .embeddingModel("hf.co/Qwen/Qwen3-Embedding-0.6B-GGUF:latest")
 *           .topK(5)
 *           .build();
 *   Agent agent = Agents.react().anthropicMessages(key, baseUrl)
 *           .model("glm-4.6")
 *           .toolRegistry(new StaticToolRegistry(Collections.singletonList(ragTool.tool())))
 *           .toolExecutor(ragTool.executor())
 *           .capture(new InMemoryIoCaptureSink())   // 自动捕获 MODEL + TOOL(含 RAG 检索) 节点
 *           .build();
 * }</pre>
 *
 * <p>filter（多租户权限过滤）可在 RagService 构造时通过 Retriever 注入，
 * 也可由调用方在 executor 里包一层（从 AgentContext 取租户）。
 */
public class RagTool {

    private final RagService ragService;
    private final String dataset;
    private final String embeddingModel;
    private final int topK;
    private final String name;
    private final String description;

    public RagTool(RagService ragService, String dataset, String embeddingModel, int topK) {
        this(ragService, dataset, embeddingModel, topK,
                "knowledge_search",
                "Search the knowledge base for relevant context to answer the user's question. "
                        + "Input: {\"query\": \"the question\"}. Returns assembled context with citations.");
    }

    public RagTool(RagService ragService, String dataset, String embeddingModel, int topK,
                   String name, String description) {
        if (ragService == null) {
            throw new IllegalArgumentException("ragService is required");
        }
        if (dataset == null || dataset.trim().isEmpty()) {
            throw new IllegalArgumentException("dataset is required");
        }
        if (embeddingModel == null || embeddingModel.trim().isEmpty()) {
            throw new IllegalArgumentException("embeddingModel is required");
        }
        this.ragService = ragService;
        this.dataset = dataset;
        this.embeddingModel = embeddingModel;
        this.topK = topK <= 0 ? 5 : topK;
        this.name = name;
        this.description = description;
    }

    /** OpenAI function schema（agent 调用契约）。 */
    public Tool tool() {
        Tool.Function fn = new Tool.Function();
        fn.setName(name);
        fn.setDescription(description);
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        param.setType("object");
        Map<String, Tool.Function.Property> props = new HashMap<String, Tool.Function.Property>();
        Tool.Function.Property queryProp = new Tool.Function.Property();
        queryProp.setType("string");
        queryProp.setDescription("The user's question to search in the knowledge base");
        props.put("query", queryProp);
        param.setProperties(props);
        param.setRequired(Collections.singletonList("query"));
        fn.setParameters(param);
        return new Tool("function", fn);
    }

    /**
     * ToolExecutor：从 arguments 取 query → {@code RagService.search} → 返回组装好的上下文。
     * 检索过程作为 TOOL 节点被 IoCaptureAgentListener 捕获。
     */
    public ToolExecutor executor() {
        return call -> {
            String arguments = call.getArguments();
            String query = arguments == null || arguments.trim().isEmpty()
                    ? null
                    : JSON.parseObject(arguments).getString("query");
            if (query == null || query.trim().isEmpty()) {
                return "";
            }
            RagResult result = ragService.search(RagQuery.builder()
                    .query(query)
                    .dataset(dataset)
                    .embeddingModel(embeddingModel)
                    .topK(topK)
                    .build());
            return result == null || result.getContext() == null ? "" : result.getContext();
        };
    }

    public static Builder builder(RagService ragService) {
        return new Builder(ragService);
    }

    public static class Builder {
        private final RagService ragService;
        private String dataset;
        private String embeddingModel;
        private int topK = 5;
        private String name = "knowledge_search";
        private String description = "Search the knowledge base for relevant context.";

        public Builder(RagService ragService) {
            this.ragService = ragService;
        }

        public Builder dataset(String dataset) {
            this.dataset = dataset;
            return this;
        }

        public Builder embeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder topK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public RagTool build() {
            return new RagTool(ragService, dataset, embeddingModel, topK, name, description);
        }
    }
}
