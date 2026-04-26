package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultRagContextAssembler implements RagContextAssembler {

    @Override
    public RagContext assemble(RagQuery query, List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return RagContext.builder()
                    .text("")
                    .citations(Collections.<RagCitation>emptyList())
                    .build();
        }

        String delimiter = query == null || query.getDelimiter() == null ? "\n\n" : query.getDelimiter();
        boolean includeCitations = query == null || query.isIncludeCitations();

        List<RagCitation> citations = new ArrayList<RagCitation>();
        StringBuilder context = new StringBuilder();
        int index = 1;
        for (RagHit hit : hits) {
            if (hit == null || hit.getContent() == null || hit.getContent().trim().isEmpty()) {
                continue;
            }
            RagCitation citation = RagCitation.builder()
                    .citationId("S" + index)
                    .sourceName(hit.getSourceName())
                    .sourcePath(hit.getSourcePath())
                    .sourceUri(hit.getSourceUri())
                    .pageNumber(hit.getPageNumber())
                    .sectionTitle(hit.getSectionTitle())
                    .snippet(hit.getContent())
                    .build();
            citations.add(citation);

            if (context.length() > 0) {
                context.append(delimiter);
            }
            if (includeCitations) {
                context.append("[").append(citation.getCitationId()).append("] ");
                appendSourceLabel(context, citation);
                context.append("\n");
            }
            context.append(hit.getContent());
            index++;
        }

        return RagContext.builder()
                .text(context.toString())
                .citations(citations)
                .build();
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
