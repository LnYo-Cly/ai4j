package io.github.lnyocly.ai4j.rag.ingestion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cleans common OCR artifacts such as hyphenated line breaks and letter-by-letter spacing.
 */
public class OcrNoiseCleaningDocumentProcessor implements LoadedDocumentProcessor {

    @Override
    public LoadedDocument process(IngestionSource source, LoadedDocument document) {
        if (document == null || isBlank(document.getContent())) {
            return document;
        }
        String cleaned = clean(document.getContent());
        if (cleaned.equals(document.getContent())) {
            return document;
        }
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }
        metadata.put("ocrNoiseCleaned", Boolean.TRUE);
        return document.toBuilder()
                .content(cleaned)
                .metadata(metadata)
                .build();
    }

    String clean(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String hyphenJoined = normalized.replaceAll("([\\p{L}\\p{N}])\\s*-\\s*\\n\\s*([\\p{L}\\p{N}])", "$1$2");
        String[] lines = hyphenJoined.split("\\n", -1);
        StringBuilder cleaned = new StringBuilder();
        int blankCount = 0;
        for (String line : lines) {
            String normalizedLine = collapseInnerWhitespace(line);
            if (looksLikeSpacedWord(normalizedLine)) {
                normalizedLine = normalizedLine.replace(" ", "");
            }
            normalizedLine = normalizedLine.trim();
            if (normalizedLine.isEmpty()) {
                blankCount++;
                if (blankCount > 1) {
                    continue;
                }
            } else {
                blankCount = 0;
            }
            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(normalizedLine);
        }
        return cleaned.toString().trim();
    }

    private String collapseInnerWhitespace(String value) {
        return value == null ? "" : value.replaceAll("[\\t\\x0B\\f ]+", " ");
    }

    private boolean looksLikeSpacedWord(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String[] parts = trimmed.split(" ");
        if (parts.length < 3) {
            return false;
        }
        for (String part : parts) {
            if (part.length() != 1 || !Character.isLetterOrDigit(part.charAt(0))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
