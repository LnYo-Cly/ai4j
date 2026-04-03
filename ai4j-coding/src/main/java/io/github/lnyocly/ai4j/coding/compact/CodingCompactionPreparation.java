package io.github.lnyocly.ai4j.coding.compact;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CodingCompactionPreparation {

    private List<Object> rawItems;

    private List<Object> itemsToSummarize;

    private List<Object> turnPrefixItems;

    private List<Object> keptItems;

    private String previousSummary;

    private boolean splitTurn;

    private int firstKeptItemIndex;

    private int estimatedTokensBefore;
}
