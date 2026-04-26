package io.github.lnyocly.ai4j.rag;

import java.util.List;

public interface RagContextAssembler {

    RagContext assemble(RagQuery query, List<RagHit> hits);
}
