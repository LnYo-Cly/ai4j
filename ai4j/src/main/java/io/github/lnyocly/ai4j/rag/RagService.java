package io.github.lnyocly.ai4j.rag;

public interface RagService {

    RagResult search(RagQuery query) throws Exception;
}
