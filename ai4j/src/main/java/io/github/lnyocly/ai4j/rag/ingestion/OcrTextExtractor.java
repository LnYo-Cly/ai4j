package io.github.lnyocly.ai4j.rag.ingestion;

/**
 * Extension point for OCR engines used during ingestion.
 */
public interface OcrTextExtractor {

    boolean supports(IngestionSource source, LoadedDocument document);

    String extractText(IngestionSource source, LoadedDocument document) throws Exception;
}
