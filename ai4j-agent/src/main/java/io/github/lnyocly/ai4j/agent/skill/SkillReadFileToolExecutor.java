package io.github.lnyocly.ai4j.agent.skill;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.tool.BuiltInToolContext;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import io.github.lnyocly.ai4j.tool.ToolUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes only the Skill-owned read_file capability under a per-run restricted context.
 */
final class SkillReadFileToolExecutor implements ToolExecutor {

    private final BuiltInToolContext context;
    private final Map<String, String> providedContents;

    SkillReadFileToolExecutor(BuiltInToolContext context) {
        this(context, null);
    }

    SkillReadFileToolExecutor(BuiltInToolContext context, Map<String, String> providedContents) {
        this.context = context;
        this.providedContents = providedContents == null
                ? Collections.<String, String>emptyMap()
                : new LinkedHashMap<String, String>(providedContents);
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null || !BuiltInTools.READ_FILE.equals(call.getName())) {
            throw new IllegalArgumentException("Unsupported Skill tool: " + (call == null ? null : call.getName()));
        }
        JSONObject arguments = JSON.parseObject(call.getArguments());
        String path = arguments == null ? null : arguments.getString("path");
        String providedContent = providedContents.get(path);
        if (providedContent != null) {
            return JSON.toJSONString(readProvidedSkill(path, providedContent, arguments));
        }
        ToolUtil.pushBuiltInToolContext(context);
        try {
            return ToolUtil.invoke(call.getName(), call.getArguments());
        } finally {
            ToolUtil.popBuiltInToolContext();
        }
    }

    private Map<String, Object> readProvidedSkill(String path, String content, JSONObject arguments) {
        List<String> lines = new ArrayList<String>();
        Collections.addAll(lines, content.split("\\r?\\n", -1));
        int startLine = arguments.getInteger("startLine") == null || arguments.getInteger("startLine").intValue() < 1
                ? 1 : arguments.getInteger("startLine").intValue();
        int endLine = arguments.getInteger("endLine") == null || arguments.getInteger("endLine").intValue() > lines.size()
                ? lines.size() : arguments.getInteger("endLine").intValue();
        if (endLine < startLine) {
            endLine = startLine - 1;
        }

        StringBuilder selected = new StringBuilder();
        for (int index = startLine; index <= endLine && index <= lines.size(); index++) {
            if (selected.length() > 0) {
                selected.append('\n');
            }
            selected.append(lines.get(index - 1));
        }
        int maxChars = arguments.getInteger("maxChars") == null || arguments.getInteger("maxChars").intValue() <= 0
                ? context.getDefaultReadMaxChars() : arguments.getInteger("maxChars").intValue();
        String selectedContent = selected.toString();
        boolean truncated = selectedContent.length() > maxChars;
        if (truncated) {
            selectedContent = selectedContent.substring(0, maxChars);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("path", path);
        result.put("content", selectedContent);
        result.put("startLine", startLine);
        result.put("endLine", endLine);
        result.put("truncated", truncated);
        return result;
    }
}
