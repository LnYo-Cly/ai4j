package io.github.lnyocly.ai4j.rag.ingestion;

/**
 * Loader for already extracted text content.
 */
public class TextDocumentLoader implements DocumentLoader {

    @Override
    public boolean supports(IngestionSource source) {
        return source != null && source.getContent() != null && !source.getContent().trim().isEmpty();
    }

    @Override
    public LoadedDocument load(IngestionSource source) {
        return LoadedDocument.builder()
                .content(source.getContent())
                .sourceName(source.getName())
                .sourcePath(source.getPath())
                .sourceUri(source.getUri())
                .metadata(source.getMetadata())
                .build();
    }
}
