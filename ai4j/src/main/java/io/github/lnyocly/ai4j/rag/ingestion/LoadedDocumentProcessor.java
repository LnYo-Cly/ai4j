package io.github.lnyocly.ai4j.rag.ingestion;

/**
 * Post-processes loaded documents before chunking.
 * Useful for OCR fallback, scanned-document cleanup, and complex text normalization.
 */
public interface LoadedDocumentProcessor {

    LoadedDocument process(IngestionSource source, LoadedDocument document) throws Exception;
}
