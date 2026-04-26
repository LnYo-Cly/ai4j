package io.github.lnyocly.ai4j.rag.ingestion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uses an external OCR extractor to populate text for scanned or non-text documents.
 */
public class OcrTextExtractingDocumentProcessor implements LoadedDocumentProcessor {

    private final OcrTextExtractor extractor;

    public OcrTextExtractingDocumentProcessor(OcrTextExtractor extractor) {
        if (extractor == null) {
            throw new IllegalArgumentException("extractor is required");
        }
        this.extractor = extractor;
    }

    @Override
    public LoadedDocument process(IngestionSource source, LoadedDocument document) throws Exception {
        if (document == null || !isBlank(document.getContent())) {
            return document;
        }
        if (!extractor.supports(source, document)) {
            return document;
        }
        String extracted = extractor.extractText(source, document);
        if (isBlank(extracted)) {
            return document;
        }
        Map<String, Object> metadata = copyMetadata(document.getMetadata());
        metadata.put("ocrApplied", Boolean.TRUE);
        return document.toBuilder()
                .content(extracted)
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        Map<String, Object> copied = new LinkedHashMap<String, Object>();
        if (metadata != null) {
            copied.putAll(metadata);
        }
        return copied;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
