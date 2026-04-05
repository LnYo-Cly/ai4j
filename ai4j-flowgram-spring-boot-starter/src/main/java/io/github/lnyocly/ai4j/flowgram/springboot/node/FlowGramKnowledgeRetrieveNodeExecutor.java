package io.github.lnyocly.ai4j.flowgram.springboot.node;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutor;
import io.github.lnyocly.ai4j.rag.DefaultRagService;
import io.github.lnyocly.ai4j.rag.DenseRetriever;
import io.github.lnyocly.ai4j.rag.RagCitation;
import io.github.lnyocly.ai4j.rag.RagContextAssembler;
import io.github.lnyocly.ai4j.rag.RagHit;
import io.github.lnyocly.ai4j.rag.RagQuery;
import io.github.lnyocly.ai4j.rag.RagResult;
import io.github.lnyocly.ai4j.rag.RagService;
import io.github.lnyocly.ai4j.rag.RagScoreDetail;
import io.github.lnyocly.ai4j.rag.RagTrace;
import io.github.lnyocly.ai4j.rag.Reranker;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import io.github.lnyocly.ai4j.vector.store.VectorStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FlowGramKnowledgeRetrieveNodeExecutor implements FlowGramNodeExecutor {

    private final AiServiceRegistry aiServiceRegistry;
    private final VectorStore vectorStore;
    private final Reranker reranker;
    private final RagContextAssembler contextAssembler;

    public FlowGramKnowledgeRetrieveNodeExecutor(AiServiceRegistry aiServiceRegistry,
                                                 VectorStore vectorStore,
                                                 Reranker reranker,
                                                 RagContextAssembler contextAssembler) {
        this.aiServiceRegistry = aiServiceRegistry;
        this.vectorStore = vectorStore;
        this.reranker = reranker;
        this.contextAssembler = contextAssembler;
    }

    @Override
    public String getType() {
        return "KNOWLEDGE";
    }

    @Override
    public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception {
        Map<String, Object> inputs = context == null || context.getInputs() == null
                ? new LinkedHashMap<String, Object>()
                : context.getInputs();

        String serviceId = requiredString(inputs, "serviceId");
        String embeddingModel = requiredString(inputs, "embeddingModel");
        String dataset = firstNonBlank(valueAsString(inputs.get("dataset")), valueAsString(inputs.get("namespace")));
        if (dataset == null || dataset.trim().isEmpty()) {
            throw new IllegalArgumentException("Knowledge node requires dataset or namespace");
        }
        String query = requiredString(inputs, "query");
        int topK = intValue(inputs.get("topK"), 5);
        int finalTopK = intValue(inputs.get("finalTopK"), topK);
        String delimiter = valueAsString(inputs.get("delimiter"));
        if (delimiter == null) {
            delimiter = "\n\n";
        }
        Map<String, Object> filter = mapValue(inputs.get("filter"));

        IEmbeddingService embeddingService = aiServiceRegistry.getEmbeddingService(serviceId);
        RagService ragService = new DefaultRagService(
                new DenseRetriever(embeddingService, vectorStore),
                reranker,
                contextAssembler
        );
        RagResult ragResult = ragService.search(RagQuery.builder()
                .query(query)
                .embeddingModel(embeddingModel)
                .dataset(dataset)
                .topK(topK)
                .finalTopK(finalTopK)
                .filter(filter)
                .delimiter(delimiter)
                .build());
        List<Map<String, Object>> matches = mapHits(ragResult == null ? null : ragResult.getHits());
        List<Map<String, Object>> citations = mapCitations(ragResult == null ? null : ragResult.getCitations());
        Map<String, Object> trace = mapTrace(ragResult == null ? null : ragResult.getTrace());

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("matches", matches);
        outputs.put("hits", matches);
        outputs.put("context", ragResult == null ? "" : ragResult.getContext());
        outputs.put("citations", citations);
        outputs.put("sources", citations);
        outputs.put("trace", trace);
        outputs.put("retrievedHits", trace.get("retrievedHits"));
        outputs.put("rerankedHits", trace.get("rerankedHits"));
        outputs.put("count", matches.size());
        return FlowGramNodeExecutionResult.builder()
                .outputs(outputs)
                .build();
    }

    private String requiredString(Map<String, Object> inputs, String key) {
        String value = valueAsString(inputs.get(key));
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Knowledge node requires " + key);
        }
        return value.trim();
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            return new LinkedHashMap<String, Object>((Map<String, Object>) value);
        }
        String text = valueAsString(value);
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(text, LinkedHashMap.class);
        } catch (Exception ignore) {
            return null;
        }
    }

    private List<Map<String, Object>> mapHits(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (RagHit hit : hits) {
            if (hit == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", hit.getId());
            item.put("score", hit.getScore());
            item.put("rank", hit.getRank());
            item.put("retrieverSource", hit.getRetrieverSource());
            item.put("retrievalScore", hit.getRetrievalScore());
            item.put("fusionScore", hit.getFusionScore());
            item.put("rerankScore", hit.getRerankScore());
            item.put("content", hit.getContent());
            item.put("metadata", hit.getMetadata());
            item.put("documentId", hit.getDocumentId());
            item.put("sourceName", hit.getSourceName());
            item.put("sourcePath", hit.getSourcePath());
            item.put("sourceUri", hit.getSourceUri());
            item.put("pageNumber", hit.getPageNumber());
            item.put("sectionTitle", hit.getSectionTitle());
            item.put("chunkIndex", hit.getChunkIndex());
            item.put("scoreDetails", mapScoreDetails(hit.getScoreDetails()));
            results.add(item);
        }
        return results;
    }

    private List<Map<String, Object>> mapCitations(List<RagCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (RagCitation citation : citations) {
            if (citation == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("citationId", citation.getCitationId());
            item.put("sourceName", citation.getSourceName());
            item.put("sourcePath", citation.getSourcePath());
            item.put("sourceUri", citation.getSourceUri());
            item.put("pageNumber", citation.getPageNumber());
            item.put("sectionTitle", citation.getSectionTitle());
            item.put("snippet", citation.getSnippet());
            results.add(item);
        }
        return results;
    }

    private Map<String, Object> mapTrace(RagTrace trace) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        if (trace == null) {
            result.put("retrievedHits", Collections.emptyList());
            result.put("rerankedHits", Collections.emptyList());
            return result;
        }
        result.put("retrievedHits", mapHits(trace.getRetrievedHits()));
        result.put("rerankedHits", mapHits(trace.getRerankedHits()));
        return result;
    }

    private List<Map<String, Object>> mapScoreDetails(List<RagScoreDetail> details) {
        if (details == null || details.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        for (RagScoreDetail detail : details) {
            if (detail == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("source", detail.getSource());
            item.put("rank", detail.getRank());
            item.put("retrievalScore", detail.getRetrievalScore());
            item.put("fusionContribution", detail.getFusionContribution());
            results.add(item);
        }
        return results;
    }
}

