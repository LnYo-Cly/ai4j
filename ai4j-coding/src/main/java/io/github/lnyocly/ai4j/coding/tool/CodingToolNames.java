package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.tool.BuiltInTools;

import java.util.Set;

public final class CodingToolNames {

    public static final String BASH = BuiltInTools.BASH;
    public static final String READ_FILE = BuiltInTools.READ_FILE;
    public static final String WRITE_FILE = BuiltInTools.WRITE_FILE;
    public static final String APPLY_PATCH = BuiltInTools.APPLY_PATCH;

    private CodingToolNames() {
    }

    public static Set<String> allBuiltIn() {
        return BuiltInTools.allCodingToolNames();
    }

    public static Set<String> readOnlyBuiltIn() {
        return BuiltInTools.readOnlyCodingToolNames();
    }
}
