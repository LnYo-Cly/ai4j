package io.github.lnyocly.ai4j.agent.skill;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.CompositeToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.RoutingToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.ToolUtilRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;
import io.github.lnyocly.ai4j.skill.Skills;
import io.github.lnyocly.ai4j.tool.BuiltInToolContext;
import io.github.lnyocly.ai4j.tool.BuiltInTools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a stable Skill snapshot once per Agent run and keeps its read_file capability scoped.
 */
public final class AgentSkillRuntimeSupport {

    private AgentSkillRuntimeSupport() {
    }

    public static AgentContext apply(AgentContext context, AgentRequest request) {
        return apply(context, request, true);
    }

    /**
     * Applies Skills to a runtime. Runtimes without function-tool support can only receive
     * host-selected Skills, because they cannot perform progressive read_file activation.
     */
    public static AgentContext apply(AgentContext context, AgentRequest request, boolean supportsSkillReadFile) {
        if (context == null || context.getSkillResolver() == null) {
            return context;
        }
        AgentSkillScope scope = context.getSkillResolver().resolve(request);
        if (scope == null) {
            return context;
        }
        Skills.DiscoveryResult discovery = discover(scope);
        List<SkillDescriptor> enabled = filterEnabled(discovery.getSkills(), scope.getEnabledSkillNames());
        List<SkillDescriptor> selected = resolveSelected(enabled, request == null ? null : request.getSelectedSkills());
        List<SkillDescriptor> automatic = supportsSkillReadFile
                ? modelInvocable(enabled) : Collections.<SkillDescriptor>emptyList();
        List<SkillDescriptor> exposed = merge(automatic, selected);
        if (exposed.isEmpty()) {
            return context;
        }

        String prompt = Skills.appendAvailableSkillsPrompt(context.getSystemPrompt(), automatic);
        prompt = appendSelectedSkillsPrompt(prompt, selected);
        if (!supportsSkillReadFile) {
            return context.toBuilder().systemPrompt(prompt).build();
        }

        List<String> skillRoots = skillRoots(exposed);
        BuiltInToolContext readContext = Skills.createSkillToolContext(skillRoots);
        AgentToolRegistry registry = addReadFileTool(context.getToolRegistry());
        ToolExecutor executor = addReadFileExecutor(context, readContext);
        return context.toBuilder()
                .systemPrompt(prompt)
                .toolRegistry(registry)
                .toolExecutor(executor)
                .build();
    }

    private static List<SkillDescriptor> filterEnabled(List<SkillDescriptor> discovered, List<String> enabledNames) {
        List<SkillDescriptor> skills = safeList(discovered);
        Set<String> enabled = normalizedNames(enabledNames);
        if (enabled.isEmpty()) {
            return skills;
        }
        List<SkillDescriptor> result = new ArrayList<SkillDescriptor>();
        for (SkillDescriptor skill : skills) {
            if (skill != null && enabled.contains(normalize(skill.getName()))) {
                result.add(skill);
            }
        }
        return result;
    }

    private static Skills.DiscoveryResult discover(AgentSkillScope scope) {
        if (scope.isIncludeDefaultSkillRoots()) {
            return Skills.discoverDefault(scope.getWorkspaceRoot(), safeList(scope.getSkillDirectories()));
        }
        Path workspaceRoot = scope.getWorkspaceRoot() == null
                ? Paths.get(".").toAbsolutePath().normalize()
                : scope.getWorkspaceRoot().toAbsolutePath().normalize();
        List<Path> roots = new ArrayList<Path>();
        for (String directory : safeList(scope.getSkillDirectories())) {
            if (!isBlank(directory)) {
                Path root = Paths.get(directory);
                if (!root.isAbsolute()) {
                    root = workspaceRoot.resolve(root);
                }
                roots.add(root.toAbsolutePath().normalize());
            }
        }
        return Skills.discover(workspaceRoot, roots);
    }

    private static List<SkillDescriptor> resolveSelected(List<SkillDescriptor> enabled, List<String> selectedNames) {
        if (selectedNames == null || selectedNames.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, SkillDescriptor> byName = new LinkedHashMap<String, SkillDescriptor>();
        for (SkillDescriptor skill : safeList(enabled)) {
            if (skill != null) {
                byName.put(normalize(skill.getName()), skill);
            }
        }
        List<SkillDescriptor> result = new ArrayList<SkillDescriptor>();
        Set<String> seen = new LinkedHashSet<String>();
        for (String selectedName : selectedNames) {
            String key = normalize(selectedName);
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            SkillDescriptor skill = byName.get(key);
            if (skill == null) {
                throw new IllegalArgumentException("Selected Skill is not enabled or installed: " + selectedName);
            }
            result.add(skill);
        }
        return result;
    }

    private static List<SkillDescriptor> modelInvocable(List<SkillDescriptor> skills) {
        List<SkillDescriptor> result = new ArrayList<SkillDescriptor>();
        for (SkillDescriptor skill : safeList(skills)) {
            if (skill != null && !skill.isDisableModelInvocation()) {
                result.add(skill);
            }
        }
        return result;
    }

    private static List<SkillDescriptor> merge(List<SkillDescriptor> automatic, List<SkillDescriptor> selected) {
        Map<String, SkillDescriptor> result = new LinkedHashMap<String, SkillDescriptor>();
        append(result, automatic);
        append(result, selected);
        return new ArrayList<SkillDescriptor>(result.values());
    }

    private static void append(Map<String, SkillDescriptor> target, List<SkillDescriptor> skills) {
        for (SkillDescriptor skill : safeList(skills)) {
            if (skill != null) {
                target.put(normalize(skill.getName()) + "\u0000" + skill.getSkillFilePath(), skill);
            }
        }
    }

    private static List<String> skillRoots(List<SkillDescriptor> skills) {
        Set<String> roots = new LinkedHashSet<String>();
        for (SkillDescriptor skill : safeList(skills)) {
            if (skill == null || isBlank(skill.getSkillFilePath())) {
                continue;
            }
            Path skillFile = Paths.get(skill.getSkillFilePath()).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(skillFile) || !Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Skill file must be a regular non-symlink file: " + skill.getSkillFilePath());
            }
            Path parent = skillFile.getParent();
            if (parent != null) {
                roots.add(parent.toString());
            }
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Resolved Skills have no readable roots");
        }
        return new ArrayList<String>(roots);
    }

    private static AgentToolRegistry addReadFileTool(AgentToolRegistry existing) {
        if (hasToolNamed(existing, BuiltInTools.READ_FILE)) {
            throw new IllegalStateException("Skill integration reserves read_file; remove the conflicting custom tool before enabling Skills");
        }
        AgentToolRegistry skillRegistry = new ToolUtilRegistry(
                Collections.singletonList(BuiltInTools.READ_FILE), Collections.<String>emptyList());
        return new CompositeToolRegistry(existing, skillRegistry);
    }

    private static ToolExecutor addReadFileExecutor(AgentContext context, BuiltInToolContext readContext) {
        ToolExecutor readExecutor = new SkillReadFileToolExecutor(readContext);
        if (context.getPermissionPolicy() != null) {
            readExecutor = new AgentPermissionToolExecutor(
                    readExecutor, context.getPermissionPolicy(), context.getExecutionEnvironment());
        }
        return new RoutingToolExecutor(
                Collections.singletonList(RoutingToolExecutor.route(
                        Collections.singleton(BuiltInTools.READ_FILE), readExecutor)),
                context.getToolExecutor());
    }

    private static boolean hasToolNamed(AgentToolRegistry registry, String name) {
        if (registry == null || registry.getTools() == null) {
            return false;
        }
        for (Object candidate : registry.getTools()) {
            if (candidate instanceof Tool) {
                Tool tool = (Tool) candidate;
                if (tool.getFunction() != null && name.equals(tool.getFunction().getName())) {
                    return true;
                }
            } else if (candidate instanceof Map) {
                Object function = ((Map<?, ?>) candidate).get("function");
                if (function instanceof Map && name.equals(((Map<?, ?>) function).get("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String appendSelectedSkillsPrompt(String basePrompt, List<SkillDescriptor> selected) {
        if (selected == null || selected.isEmpty()) {
            return basePrompt;
        }
        StringBuilder builder = new StringBuilder(isBlank(basePrompt) ? "" : basePrompt.trim());
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("The host explicitly activated these skills for this request. Follow their instructions.\n");
        builder.append("<selected_skills>\n");
        for (SkillDescriptor skill : selected) {
            builder.append("<selected_skill name=\"").append(escapeXml(valueOrDefault(skill.getName(), "skill"))).append("\">\n");
            builder.append(readSelectedSkillContent(skill));
            builder.append("\nSkill directory: ").append(escapeXml(skillDirectory(skill))).append("\n");
            builder.append("</selected_skill>\n");
        }
        builder.append("</selected_skills>\n");
        builder.append("Do not treat a Skill as permission to invoke additional tools or access data outside its Skill directory.");
        return builder.toString();
    }

    private static String readSelectedSkillContent(SkillDescriptor skill) {
        if (skill == null || isBlank(skill.getSkillFilePath())) {
            throw new IllegalArgumentException("Selected Skill has no readable SKILL.md");
        }
        Path skillFile = Paths.get(skill.getSkillFilePath()).toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(skillFile) || !Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Selected Skill file must be a regular non-symlink file: " + skill.getSkillFilePath());
            }
            return new String(Files.readAllBytes(skillFile), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read selected Skill: " + valueOrDefault(skill.getName(), skill.getSkillFilePath()), ex);
        }
    }

    private static String skillDirectory(SkillDescriptor skill) {
        if (skill == null || isBlank(skill.getSkillFilePath())) {
            return "(missing)";
        }
        Path parent = Paths.get(skill.getSkillFilePath()).toAbsolutePath().normalize().getParent();
        return parent == null ? "(missing)" : parent.toString();
    }

    private static Set<String> normalizedNames(List<String> values) {
        Set<String> result = new LinkedHashSet<String>();
        for (String value : safeList(values)) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.<T>emptyList() : values;
    }

    private static String normalize(String value) {
        return isBlank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String escapeXml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
