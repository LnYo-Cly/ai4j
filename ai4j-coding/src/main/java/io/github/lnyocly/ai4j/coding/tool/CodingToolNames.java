package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.tool.BuiltInTools;

import java.util.Set;

public final class CodingToolNames {

    public static final String BASH = BuiltInTools.BASH;
    public static final String BASH_PROCESS = BuiltInTools.BASH_PROCESS;
    public static final String READ_FILE = BuiltInTools.READ_FILE;
    public static final String WRITE_FILE = BuiltInTools.WRITE_FILE;
    public static final String APPLY_PATCH = BuiltInTools.APPLY_PATCH;
    public static final String GLOB = BuiltInTools.GLOB;
    public static final String GREP = BuiltInTools.GREP;
    public static final String EDIT = BuiltInTools.EDIT;
    public static final String UPDATE_AGENTS_MD = BuiltInTools.UPDATE_AGENTS_MD;

    private CodingToolNames() {
    }

    public static Set<String> allBuiltIn() {
        return BuiltInTools.allCodingToolNames();
    }

    public static Set<String> readOnlyBuiltIn() {
        return BuiltInTools.readOnlyCodingToolNames();
    }
}
