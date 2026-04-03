package io.github.lnyocly.ai4j.rag.ingestion;

/**
 * Converts a raw ingestion source into normalized text plus source metadata.
 */
public interface DocumentLoader {

    boolean supports(IngestionSource source);

    LoadedDocument load(IngestionSource source) throws Exception;
}
