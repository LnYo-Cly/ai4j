package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DefaultTextTokenizer implements TextTokenizer {

    @Override
    public List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<String>();
        StringBuilder latin = new StringBuilder();
        char[] chars = text.toLowerCase(Locale.ROOT).toCharArray();
        for (char ch : chars) {
            if (Character.isLetterOrDigit(ch) && !isCjk(ch)) {
                latin.append(ch);
                continue;
            }
            flushLatin(tokens, latin);
            if (isCjk(ch)) {
                tokens.add(String.valueOf(ch));
            }
        }
        flushLatin(tokens, latin);
        return tokens;
    }

    private void flushLatin(List<String> tokens, StringBuilder latin) {
        if (latin.length() == 0) {
            return;
        }
        tokens.add(latin.toString());
        latin.setLength(0);
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(block)
                || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A.equals(block)
                || Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B.equals(block)
                || Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS.equals(block)
                || Character.UnicodeBlock.HIRAGANA.equals(block)
                || Character.UnicodeBlock.KATAKANA.equals(block)
                || Character.UnicodeBlock.HANGUL_SYLLABLES.equals(block);
    }
}
