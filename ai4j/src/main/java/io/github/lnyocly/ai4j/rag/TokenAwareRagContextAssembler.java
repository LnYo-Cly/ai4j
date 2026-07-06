package io.github.lnyocly.ai4j.rag;

import com.knuddels.jtokkit.api.EncodingType;
import io.github.lnyocly.ai4j.token.TikTokensUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TokenAwareRagContextAssembler implements RagContextAssembler {

    private static final String DEFAULT_DELIMITER = "\n\n";

    private final String modelName;
    private final int maxContextTokens;
    private final boolean truncateOversizedHit;

    public TokenAwareRagContextAssembler(int maxContextTokens) {
        this(null, maxContextTokens, true);
    }

    public TokenAwareRagContextAssembler(String modelName, int maxContextTokens) {
        this(modelName, maxContextTokens, true);
    }

    public TokenAwareRagContextAssembler(String modelName, int maxContextTokens, boolean truncateOversizedHit) {
        if (maxContextTokens <= 0) {
            throw new IllegalArgumentException("maxContextTokens must be positive");
        }
        this.modelName = trimToNull(modelName);
        this.maxContextTokens = maxContextTokens;
        this.truncateOversizedHit = truncateOversizedHit;
    }

    @Override
    public RagContext assemble(RagQuery query, List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return RagContext.builder()
                    .text("")
                    .citations(Collections.<RagCitation>emptyList())
                    .build();
        }

        String delimiter = query == null || query.getDelimiter() == null ? DEFAULT_DELIMITER : query.getDelimiter();
        boolean includeCitations = query == null || query.isIncludeCitations();
        List<RagCitation> citations = new ArrayList<RagCitation>();
        StringBuilder context = new StringBuilder();

        for (RagHit hit : hits) {
            String content = hit == null ? null : hit.getContent();
            if (content == null || content.trim().isEmpty()) {
                continue;
            }

            RagCitation citation = citation(hit, citations.size() + 1, content);
            String rendered = renderSegment(citation, content, includeCitations);
            String candidate = appendCandidate(context, delimiter, rendered);
            if (countTokens(candidate) <= maxContextTokens) {
                appendSegment(context, delimiter, rendered);
                citations.add(citation);
                continue;
            }

            if (context.length() == 0 && truncateOversizedHit) {
                String clipped = clipContent(citation, content, includeCitations);
                if (clipped != null && !clipped.isEmpty()) {
                    citation.setSnippet(clipped);
                    appendSegment(context, delimiter, renderSegment(citation, clipped, includeCitations));
                    citations.add(citation);
                }
            }
            break;
        }

        return RagContext.builder()
                .text(context.toString())
                .citations(citations)
                .build();
    }

    private RagCitation citation(RagHit hit, int index, String content) {
        return RagCitation.builder()
                .citationId("S" + index)
                .sourceName(hit.getSourceName())
                .sourcePath(hit.getSourcePath())
                .sourceUri(hit.getSourceUri())
                .pageNumber(hit.getPageNumber())
                .sectionTitle(hit.getSectionTitle())
                .snippet(content)
                .build();
    }

    private String renderSegment(RagCitation citation, String content, boolean includeCitations) {
        StringBuilder segment = new StringBuilder();
        if (includeCitations) {
            segment.append("[").append(citation.getCitationId()).append("] ");
            appendSourceLabel(segment, citation);
            segment.append("\n");
        }
        segment.append(content);
        return segment.toString();
    }

    private void appendSourceLabel(StringBuilder builder, RagCitation citation) {
        String sourceName = trimToNull(citation.getSourceName());
        String sourcePath = trimToNull(citation.getSourcePath());
        if (sourceName != null) {
            builder.append(sourceName);
        } else if (sourcePath != null) {
            builder.append(sourcePath);
        } else {
            builder.append("source");
        }
        if (citation.getPageNumber() != null) {
            builder.append(" / p.").append(citation.getPageNumber());
        }
        String sectionTitle = trimToNull(citation.getSectionTitle());
        if (sectionTitle != null) {
            builder.append(" / ").append(sectionTitle);
        }
    }

    private String appendCandidate(StringBuilder context, String delimiter, String rendered) {
        if (context.length() == 0) {
            return rendered;
        }
        return context.toString() + delimiter + rendered;
    }

    private void appendSegment(StringBuilder context, String delimiter, String rendered) {
        if (context.length() > 0) {
            context.append(delimiter);
        }
        context.append(rendered);
    }

    private String clipContent(RagCitation citation, String content, boolean includeCitations) {
        int low = 0;
        int high = content.length();
        int best = 0;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            String clipped = content.substring(0, mid);
            if (countTokens(renderSegment(citation, clipped, includeCitations)) <= maxContextTokens) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best <= 0 ? null : content.substring(0, best);
    }

    private int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (modelName != null) {
            try {
                return TikTokensUtil.tokens(modelName, text);
            } catch (RuntimeException ignored) {
                // ponytail: unknown model names fall back to cl100k_base instead of adding a tokenizer registry API.
            }
        }
        return TikTokensUtil.tokens(EncodingType.CL100K_BASE, text);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
