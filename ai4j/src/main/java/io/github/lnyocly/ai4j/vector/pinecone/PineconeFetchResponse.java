package io.github.lnyocly.ai4j.vector.pinecone;

import lombok.Data;

import java.util.Map;

@Data
public class PineconeFetchResponse {
    private Map<String, PineconeVectors> vectors;
}
