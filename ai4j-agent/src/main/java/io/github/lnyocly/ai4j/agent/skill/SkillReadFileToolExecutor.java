package io.github.lnyocly.ai4j.agent.skill;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.tool.BuiltInToolContext;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import io.github.lnyocly.ai4j.tool.ToolUtil;

/**
 * Executes only the Skill-owned read_file capability under a per-run restricted context.
 */
final class SkillReadFileToolExecutor implements ToolExecutor {

    private final BuiltInToolContext context;

    SkillReadFileToolExecutor(BuiltInToolContext context) {
        this.context = context;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null || !BuiltInTools.READ_FILE.equals(call.getName())) {
            throw new IllegalArgumentException("Unsupported Skill tool: " + (call == null ? null : call.getName()));
        }
        ToolUtil.pushBuiltInToolContext(context);
        try {
            return ToolUtil.invoke(call.getName(), call.getArguments());
        } finally {
            ToolUtil.popBuiltInToolContext();
        }
    }
}
