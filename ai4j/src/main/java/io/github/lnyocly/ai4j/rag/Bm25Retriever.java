package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Bm25Retriever implements Retriever {

    private final List<RagHit> corpus;
    private final TextTokenizer tokenizer;
    private final double k1;
    private final double b;
    private final Map<Integer, Map<String, Integer>> termFrequencies;
    private final Map<String, Integer> documentFrequencies;
    private final double averageDocumentLength;

    public Bm25Retriever(List<RagHit> corpus) {
        this(corpus, new DefaultTextTokenizer(), 1.5d, 0.75d);
    }

    public Bm25Retriever(List<RagHit> corpus, TextTokenizer tokenizer, double k1, double b) {
        this.corpus = corpus == null ? Collections.<RagHit>emptyList() : new ArrayList<RagHit>(corpus);
        this.tokenizer = tokenizer == null ? new DefaultTextTokenizer() : tokenizer;
        this.k1 = k1;
        this.b = b;
        this.termFrequencies = new HashMap<Integer, Map<String, Integer>>();
        this.documentFrequencies = new LinkedHashMap<String, Integer>();
        this.averageDocumentLength = buildIndex();
    }

    @Override
    public List<RagHit> retrieve(RagQuery query) {
        if (query == null || query.getQuery() == null || query.getQuery().trim().isEmpty() || corpus.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> terms = tokenizer.tokenize(query.getQuery());
        if (terms.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagHit> hits = new ArrayList<RagHit>();
        for (int i = 0; i < corpus.size(); i++) {
            RagHit hit = corpus.get(i);
            double score = scoreDocument(i, terms);
            if (score <= 0.0d) {
                continue;
            }
            hits.add(copyWithScore(hit, (float) score));
        }
        Collections.sort(hits, new Comparator<RagHit>() {
            @Override
            public int compare(RagHit left, RagHit right) {
                float l = left == null || left.getScore() == null ? 0.0f : left.getScore();
                float r = right == null || right.getScore() == null ? 0.0f : right.getScore();
                return Float.compare(r, l);
            }
        });
        int limit = query.getTopK() == null || query.getTopK() <= 0 ? hits.size() : Math.min(query.getTopK(), hits.size());
        return RagHitSupport.prepareRetrievedHits(new ArrayList<RagHit>(hits.subList(0, limit)), retrieverSource());
    }

    @Override
    public String retrieverSource() {
        return "bm25";
    }

    private double buildIndex() {
        if (corpus.isEmpty()) {
            return 0.0d;
        }
        int totalLength = 0;
        for (int i = 0; i < corpus.size(); i++) {
            RagHit hit = corpus.get(i);
            List<String> tokens = tokenizer.tokenize(hit == null ? null : hit.getContent());
            totalLength += tokens.size();
            Map<String, Integer> frequencies = new HashMap<String, Integer>();
            for (String token : tokens) {
                Integer count = frequencies.get(token);
                frequencies.put(token, count == null ? 1 : count + 1);
            }
            termFrequencies.put(i, frequencies);
            for (String token : frequencies.keySet()) {
                Integer count = documentFrequencies.get(token);
                documentFrequencies.put(token, count == null ? 1 : count + 1);
            }
        }
        return totalLength == 0 ? 0.0d : (double) totalLength / (double) corpus.size();
    }

    private double scoreDocument(int index, List<String> terms) {
        Map<String, Integer> frequencies = termFrequencies.get(index);
        if (frequencies == null || frequencies.isEmpty()) {
            return 0.0d;
        }
        int documentLength = 0;
        for (Integer value : frequencies.values()) {
            documentLength += value == null ? 0 : value;
        }
        double score = 0.0d;
        for (String term : terms) {
            Integer tf = frequencies.get(term);
            Integer df = documentFrequencies.get(term);
            if (tf == null || tf <= 0 || df == null || df <= 0) {
                continue;
            }
            double idf = Math.log(1.0d + (corpus.size() - df + 0.5d) / (df + 0.5d));
            double numerator = tf * (k1 + 1.0d);
            double denominator = tf + k1 * (1.0d - b + b * documentLength / Math.max(1.0d, averageDocumentLength));
            score += idf * (numerator / denominator);
        }
        return score;
    }

    private RagHit copyWithScore(RagHit hit, float score) {
        if (hit == null) {
            return null;
        }
        return RagHit.builder()
                .id(hit.getId())
                .score(score)
                .retrievalScore(score)
                .content(hit.getContent())
                .metadata(hit.getMetadata())
                .documentId(hit.getDocumentId())
                .sourceName(hit.getSourceName())
                .sourcePath(hit.getSourcePath())
                .sourceUri(hit.getSourceUri())
                .pageNumber(hit.getPageNumber())
                .sectionTitle(hit.getSectionTitle())
                .chunkIndex(hit.getChunkIndex())
                .build();
    }
}
