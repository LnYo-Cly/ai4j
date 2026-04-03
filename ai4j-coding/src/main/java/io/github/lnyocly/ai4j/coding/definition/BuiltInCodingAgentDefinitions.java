package io.github.lnyocly.ai4j.coding.definition;

import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;

import java.util.Arrays;
import java.util.List;

public final class BuiltInCodingAgentDefinitions {

    public static final String GENERAL_PURPOSE = "general-purpose";
    public static final String EXPLORE = "explore";
    public static final String PLAN = "plan";
    public static final String VERIFICATION = "verification";

    private static final List<CodingAgentDefinition> DEFINITIONS = Arrays.asList(
            CodingAgentDefinition.builder()
                    .name(GENERAL_PURPOSE)
                    .toolName("delegate_general_purpose")
                    .description("General coding worker with read, write, patch, and shell access.")
                    .instructions("You are the default coding worker. Inspect the workspace, make focused edits when needed, and finish the assigned coding task directly.")
                    .allowedToolNames(CodingToolNames.allBuiltIn())
                    .sessionMode(CodingSessionMode.FORK)
                    .isolationMode(CodingIsolationMode.WRITE_ENABLED)
                    .memoryScope(CodingMemoryScope.FORK)
                    .background(false)
                    .build(),
            CodingAgentDefinition.builder()
                    .name(EXPLORE)
                    .toolName("delegate_explore")
                    .description("Read-only codebase explorer for answering concrete repository questions.")
                    .instructions("You are a read-only exploration worker. Search, read files, and inspect the workspace, but do not modify files.")
                    .allowedToolNames(CodingToolNames.readOnlyBuiltIn())
                    .sessionMode(CodingSessionMode.FORK)
                    .isolationMode(CodingIsolationMode.READ_ONLY)
                    .memoryScope(CodingMemoryScope.FORK)
                    .background(false)
                    .build(),
            CodingAgentDefinition.builder()
                    .name(PLAN)
                    .toolName("delegate_plan")
                    .description("Read-only planner for design, decomposition, and implementation planning.")
                    .instructions("You are a planning worker. Read the codebase, identify constraints, and produce a concrete implementation plan without editing files.")
                    .allowedToolNames(CodingToolNames.readOnlyBuiltIn())
                    .sessionMode(CodingSessionMode.FORK)
                    .isolationMode(CodingIsolationMode.READ_ONLY)
                    .memoryScope(CodingMemoryScope.FORK)
                    .background(false)
                    .build(),
            CodingAgentDefinition.builder()
                    .name(VERIFICATION)
                    .toolName("delegate_verification")
                    .description("Read-only verification worker for checks, builds, and validation.")
                    .instructions("You are a verification worker. Use read-only inspection and shell validation to verify changes, summarize risks, and report findings clearly.")
                    .allowedToolNames(CodingToolNames.readOnlyBuiltIn())
                    .sessionMode(CodingSessionMode.FORK)
                    .isolationMode(CodingIsolationMode.READ_ONLY)
                    .memoryScope(CodingMemoryScope.FORK)
                    .background(true)
                    .build()
    );

    private BuiltInCodingAgentDefinitions() {
    }

    public static List<CodingAgentDefinition> list() {
        return DEFINITIONS;
    }

    public static CodingAgentDefinitionRegistry registry() {
        return new StaticCodingAgentDefinitionRegistry(DEFINITIONS);
    }
}
