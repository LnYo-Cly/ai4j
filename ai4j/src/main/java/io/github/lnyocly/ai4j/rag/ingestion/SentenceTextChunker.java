package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sentence-aware chunker: splits content on sentence boundaries
 * (Chinese 。！？；and English .!?; plus line breaks), then greedily packs
 * whole sentences into chunks of at most {@code chunkSize} characters.
 *
 * <p>Unlike {@link RecursiveTextChunker}, which may cut mid-sentence to meet
 * the size budget, this chunker keeps sentences intact so each chunk reads
 * naturally for retrieval and citation display. A single sentence longer
 * than {@code chunkSize} is hard-cut by characters as a fallback.
 * {@code chunkOverlap} characters from the tail of the previous chunk are
 * prepended to the next chunk for context continuity.</p>
 */
public class SentenceTextChunker implements Chunker {

    private final int chunkSize;
    private final int chunkOverlap;

    public SentenceTextChunker(int chunkSize, int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be >= 0 and < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    public List<RagChunk> chunk(RagDocument document, String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> pieces = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (String sentence : splitSentences(content)) {
            if (sentence == null || sentence.isEmpty()) {
                continue;
            }
            while (sentence.length() > chunkSize) {
                // Oversized sentence: flush the buffer, hard-cut with overlap.
                flush(pieces, current);
                pieces.add(sentence.substring(0, chunkSize));
                sentence = sentence.substring(chunkSize - chunkOverlap);
            }
            if (sentence.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + sentence.length() > chunkSize) {
                String tail = overlapTail(current.toString());
                flush(pieces, current);
                current.append(tail);
            }
            if (current.length() == 0) {
                sentence = trimLeading(sentence);
                if (sentence.isEmpty()) {
                    continue;
                }
            }
            current.append(sentence);
        }
        flush(pieces, current);

        List<RagChunk> chunks = new ArrayList<RagChunk>(pieces.size());
        int index = 0;
        for (String piece : pieces) {
            if (piece == null || piece.trim().isEmpty()) {
                continue;
            }
            chunks.add(RagChunk.builder()
                    .documentId(document == null ? null : document.getDocumentId())
                    .content(piece)
                    .chunkIndex(index++)
                    .build());
        }
        return chunks;
    }

    private void flush(List<String> pieces, StringBuilder current) {
        if (current.length() > 0) {
            pieces.add(current.toString());
            current.setLength(0);
        }
    }

    private String trimLeading(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return text.substring(i);
    }

    private String overlapTail(String text) {
        if (chunkOverlap <= 0 || text.length() <= chunkOverlap) {
            return "";
        }
        return text.substring(text.length() - chunkOverlap);
    }

    /**
     * Split on sentence-ending punctuation (kept attached to the sentence)
     * and on line breaks (dropped as boundaries).
     */
    private List<String> splitSentences(String content) {
        List<String> sentences = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n' || c == '\r') {
                if (current.length() > 0) {
                    sentences.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
            if (isSentenceEnd(c)) {
                // Closing quotes/brackets right after terminal punctuation stay with the sentence.
                while (i + 1 < content.length() && isClosingMark(content.charAt(i + 1))) {
                    current.append(content.charAt(++i));
                }
                sentences.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            sentences.add(current.toString());
        }
        return sentences;
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；'
                || c == '!' || c == '?' || c == ';' || c == '.';
    }

    private boolean isClosingMark(char c) {
        return c == '"' || c == '\'' || c == ')' || c == ']' || c == '}'
                || c == '”' || c == '’' || c == '）' || c == '】' || c == '》';
    }
}
