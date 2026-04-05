package io.github.lnyocly.ai4j.rag;

import java.util.List;

public interface TextTokenizer {

    List<String> tokenize(String text);
}
