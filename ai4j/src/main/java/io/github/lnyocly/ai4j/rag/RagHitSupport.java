package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RagHitSupport {

    private RagHitSupport() {
    }

    static List<RagHit> copyList(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagHit> copies = new ArrayList<RagHit>(hits.size());
        for (RagHit hit : hits) {
            if (hit == null) {
                continue;
            }
            copies.add(copy(hit));
        }
        return copies;
    }

    static RagHit copy(RagHit hit) {
        if (hit == null) {
            return null;
        }
        return RagHit.builder()
                .id(hit.getId())
                .score(hit.getScore())
                .rank(hit.getRank())
                .retrieverSource(hit.getRetrieverSource())
                .retrievalScore(hit.getRetrievalScore())
                .fusionScore(hit.getFusionScore())
                .rerankScore(hit.getRerankScore())
                .content(hit.getContent())
                .metadata(copyMetadata(hit.getMetadata()))
                .documentId(hit.getDocumentId())
                .sourceName(hit.getSourceName())
                .sourcePath(hit.getSourcePath())
                .sourceUri(hit.getSourceUri())
                .pageNumber(hit.getPageNumber())
                .sectionTitle(hit.getSectionTitle())
                .chunkIndex(hit.getChunkIndex())
                .scoreDetails(copyScoreDetails(hit.getScoreDetails()))
                .build();
    }

    static List<RagHit> prepareRetrievedHits(List<RagHit> hits, String retrieverSource) {
        List<RagHit> prepared = copyList(hits);
        assignRanks(prepared);
        for (RagHit hit : prepared) {
            if (hit == null) {
                continue;
            }
            if (isBlank(hit.getRetrieverSource())) {
                hit.setRetrieverSource(retrieverSource);
            }
            if (hit.getRetrievalScore() == null && hit.getScore() != null) {
                hit.setRetrievalScore(hit.getScore());
            }
            normalizeEffectiveScore(hit);
            if ((hit.getScoreDetails() == null || hit.getScoreDetails().isEmpty())
                    && hit.getRetrievalScore() != null
                    && !isBlank(hit.getRetrieverSource())
                    && !"hybrid".equalsIgnoreCase(hit.getRetrieverSource())) {
                hit.setScoreDetails(Collections.singletonList(RagScoreDetail.builder()
                        .source(hit.getRetrieverSource())
                        .rank(hit.getRank())
                        .retrievalScore(hit.getRetrievalScore())
                        .build()));
            }
        }
        return prepared;
    }

    static List<RagHit> prepareRerankedHits(List<RagHit> retrievedHits, List<RagHit> rerankedHits, boolean rerankApplied) {
        List<RagHit> safeReranked = rerankedHits == null ? Collections.<RagHit>emptyList() : rerankedHits;
        Map<String, RagHit> retrievedIndex = new LinkedHashMap<String, RagHit>();
        for (int i = 0; i < retrievedHits.size(); i++) {
            RagHit hit = retrievedHits.get(i);
            if (hit == null) {
                continue;
            }
            retrievedIndex.put(stableKey(hit, i), hit);
        }
        List<RagHit> merged = new ArrayList<RagHit>(safeReranked.size());
        for (int i = 0; i < safeReranked.size(); i++) {
            RagHit current = safeReranked.get(i);
            if (current == null) {
                continue;
            }
            RagHit original = retrievedIndex.get(stableKey(current, i));
            RagHit mergedHit = merge(original, current);
            if (rerankApplied && current.getScore() != null) {
                mergedHit.setRerankScore(current.getScore());
            }
            merged.add(mergedHit);
        }
        assignRanks(merged);
        for (RagHit hit : merged) {
            normalizeEffectiveScore(hit);
        }
        return merged;
    }

    static void assignRanks(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        for (int i = 0; i < hits.size(); i++) {
            RagHit hit = hits.get(i);
            if (hit != null) {
                hit.setRank(i + 1);
            }
        }
    }

    static String stableKey(RagHit hit, int fallbackIndex) {
        String key = stableKey(hit);
        return key == null ? String.valueOf(fallbackIndex) : key;
    }

    static String stableKey(RagHit hit) {
        if (hit == null) {
            return null;
        }
        if (!isBlank(hit.getId())) {
            return hit.getId().trim();
        }
        if (!isBlank(hit.getDocumentId())) {
            return hit.getDocumentId().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        if (!isBlank(hit.getSourcePath())) {
            return hit.getSourcePath().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        if (!isBlank(hit.getSourceUri())) {
            return hit.getSourceUri().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        if (!isBlank(hit.getSourceName()) && !isBlank(hit.getSectionTitle())) {
            return hit.getSourceName().trim() + "#" + hit.getSectionTitle().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        if (!isBlank(hit.getContent())) {
            return hit.getContent().trim();
        }
        return null;
    }

    static void normalizeEffectiveScore(RagHit hit) {
        if (hit == null) {
            return;
        }
        Float effectiveScore = hit.getRerankScore();
        if (effectiveScore == null) {
            effectiveScore = hit.getFusionScore();
        }
        if (effectiveScore == null) {
            effectiveScore = hit.getRetrievalScore();
        }
        if (effectiveScore == null) {
            effectiveScore = hit.getScore();
        }
        hit.setScore(effectiveScore);
    }

    private static RagHit merge(RagHit original, RagHit current) {
        RagHit base = original == null ? copy(current) : copy(original);
        if (base == null) {
            return null;
        }
        if (current == null) {
            return base;
        }
        if (!isBlank(current.getId())) {
            base.setId(current.getId());
        }
        if (current.getScore() != null) {
            base.setScore(current.getScore());
        }
        if (current.getRank() != null) {
            base.setRank(current.getRank());
        }
        if (!isBlank(current.getRetrieverSource())) {
            base.setRetrieverSource(current.getRetrieverSource());
        }
        if (current.getRetrievalScore() != null) {
            base.setRetrievalScore(current.getRetrievalScore());
        }
        if (current.getFusionScore() != null) {
            base.setFusionScore(current.getFusionScore());
        }
        if (current.getRerankScore() != null) {
            base.setRerankScore(current.getRerankScore());
        }
        if (!isBlank(current.getContent())) {
            base.setContent(current.getContent());
        }
        if (current.getMetadata() != null && !current.getMetadata().isEmpty()) {
            base.setMetadata(copyMetadata(current.getMetadata()));
        }
        if (!isBlank(current.getDocumentId())) {
            base.setDocumentId(current.getDocumentId());
        }
        if (!isBlank(current.getSourceName())) {
            base.setSourceName(current.getSourceName());
        }
        if (!isBlank(current.getSourcePath())) {
            base.setSourcePath(current.getSourcePath());
        }
        if (!isBlank(current.getSourceUri())) {
            base.setSourceUri(current.getSourceUri());
        }
        if (current.getPageNumber() != null) {
            base.setPageNumber(current.getPageNumber());
        }
        if (!isBlank(current.getSectionTitle())) {
            base.setSectionTitle(current.getSectionTitle());
        }
        if (current.getChunkIndex() != null) {
            base.setChunkIndex(current.getChunkIndex());
        }
        if (current.getScoreDetails() != null && !current.getScoreDetails().isEmpty()) {
            base.setScoreDetails(copyScoreDetails(current.getScoreDetails()));
        }
        return base;
    }

    private static Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return metadata;
        }
        return new LinkedHashMap<String, Object>(metadata);
    }

    private static List<RagScoreDetail> copyScoreDetails(List<RagScoreDetail> details) {
        if (details == null || details.isEmpty()) {
            return details;
        }
        List<RagScoreDetail> copies = new ArrayList<RagScoreDetail>(details.size());
        for (RagScoreDetail detail : details) {
            if (detail == null) {
                continue;
            }
            copies.add(RagScoreDetail.builder()
                    .source(detail.getSource())
                    .rank(detail.getRank())
                    .retrievalScore(detail.getRetrievalScore())
                    .fusionContribution(detail.getFusionContribution())
                    .build());
        }
        return copies;
    }

    private static String normalizeIndex(Integer chunkIndex) {
        return chunkIndex == null ? "-" : String.valueOf(chunkIndex);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
