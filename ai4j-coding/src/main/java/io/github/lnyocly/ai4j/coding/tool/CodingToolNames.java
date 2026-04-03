package io.github.lnyocly.ai4j.coding.tool;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class CodingToolNames {

    public static final String BASH = "bash";
    public static final String READ_FILE = "read_file";
    public static final String WRITE_FILE = "write_file";
    public static final String APPLY_PATCH = "apply_patch";

    private CodingToolNames() {
    }

    public static Set<String> allBuiltIn() {
        return unmodifiableSet(BASH, READ_FILE, WRITE_FILE, APPLY_PATCH);
    }

    public static Set<String> readOnlyBuiltIn() {
        return unmodifiableSet(BASH, READ_FILE);
    }

    private static Set<String> unmodifiableSet(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }
}
