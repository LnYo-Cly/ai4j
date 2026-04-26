package io.github.lnyocly.ai4j.rag.ingestion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Normalizes line endings and trims noisy whitespace without changing semantic content.
 */
public class WhitespaceNormalizingDocumentProcessor implements LoadedDocumentProcessor {

    @Override
    public LoadedDocument process(IngestionSource source, LoadedDocument document) {
        if (document == null || document.getContent() == null) {
            return document;
        }
        String normalized = normalize(document.getContent());
        if (normalized.equals(document.getContent())) {
            return document;
        }
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }
        metadata.put("whitespaceNormalized", Boolean.TRUE);
        return document.toBuilder()
                .content(normalized)
                .metadata(metadata)
                .build();
    }

    String normalize(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\\n", -1);
        StringBuilder builder = new StringBuilder();
        int blankCount = 0;
        for (String line : lines) {
            String cleaned = line == null ? "" : line.replaceAll("[\\t\\x0B\\f ]+", " ").trim();
            if (cleaned.isEmpty()) {
                blankCount++;
                if (blankCount > 1) {
                    continue;
                }
            } else {
                blankCount = 0;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(cleaned);
        }
        return builder.toString().trim();
    }
}
