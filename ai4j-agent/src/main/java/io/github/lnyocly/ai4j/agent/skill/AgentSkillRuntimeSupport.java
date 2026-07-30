package io.github.lnyocly.ai4j.agent.skill;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.extension.ExtensionGuardrailToolExecutor;
import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.interceptor.ToolInterceptor;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.CompositeToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.RoutingToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
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

    private static final String SKILL_READ_FILE = "read_skill_file";
    private static final long MAX_SELECTED_SKILL_BYTES = 48_000L;
    private static final int MAX_SELECTED_SKILL_CHARS = 12_000;

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

        String readToolName = supportsSkillReadFile ? resolveReadToolName(context.getToolRegistry()) : BuiltInTools.READ_FILE;
        String prompt = Skills.appendAvailableSkillsPrompt(context.getSystemPrompt(), automatic, readToolName);
        String selectedPrompt = buildSelectedSkillsPrompt(selected);
        AgentContext.AgentContextBuilder contextBuilder = context.toBuilder().systemPrompt(prompt);
        if (!BuiltInTools.READ_FILE.equals(readToolName)) {
            contextBuilder.toolInterceptor(aliasSkillReaderInterceptor(context.getToolInterceptor(), readToolName));
        }
        if (!isBlank(selectedPrompt)) {
            if (context.getMemory() == null) {
                throw new IllegalStateException("memory is required to apply explicitly selected Skills");
            }
            contextBuilder.memory(new SkillPromptOverlayMemory(context.getMemory(), selectedPrompt));
        }
        if (!supportsSkillReadFile) {
            return contextBuilder.build();
        }

        List<String> skillRoots = skillRoots(exposed);
        BuiltInToolContext readContext = Skills.createSkillToolContext(skillRoots);
        AgentToolRegistry registry = addReadFileTool(context.getToolRegistry(), readToolName);
        ToolExecutor executor = addReadFileExecutor(context, readContext, providedContents(exposed), readToolName);
        return contextBuilder
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
        Skills.DiscoveryResult discovered;
        if (scope.isIncludeDefaultSkillRoots()) {
            discovered = Skills.discoverDefault(scope.getWorkspaceRoot(), safeList(scope.getSkillDirectories()));
        } else {
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
            discovered = Skills.discover(workspaceRoot, roots);
        }
        return mergeProvidedSkills(discovered, scope.getProvidedSkills());
    }

    private static Skills.DiscoveryResult mergeProvidedSkills(Skills.DiscoveryResult discovered,
                                                               List<SkillDescriptor> provided) {
        List<SkillDescriptor> merged = new ArrayList<SkillDescriptor>();
        Map<String, SkillDescriptor> byName = new LinkedHashMap<String, SkillDescriptor>();
        for (SkillDescriptor skill : safeList(discovered == null ? null : discovered.getSkills())) {
            addUniqueSkill(merged, byName, skill);
        }
        for (SkillDescriptor skill : safeList(provided)) {
            validateProvidedSkill(skill, discovered == null
                    ? Collections.<String>emptyList() : discovered.getAllowedReadRoots());
            addUniqueSkill(merged, byName, skill);
        }
        return new Skills.DiscoveryResult(merged,
                discovered == null ? Collections.<String>emptyList() : discovered.getAllowedReadRoots());
    }

    private static void addUniqueSkill(List<SkillDescriptor> target, Map<String, SkillDescriptor> byName,
                                       SkillDescriptor skill) {
        if (skill == null) {
            return;
        }
        String key = normalize(skill.getName());
        SkillDescriptor existing = byName.get(key);
        if (existing != null && !sameSkill(existing, skill)) {
            throw new IllegalArgumentException("Duplicate Skill name '" + skill.getName()
                    + "' resolved from " + existing.getSkillFilePath() + " and " + skill.getSkillFilePath());
        }
        if (existing == null) {
            byName.put(key, skill);
            target.add(skill);
        }
    }

    private static boolean sameSkill(SkillDescriptor first, SkillDescriptor second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || isBlank(first.getSkillFilePath()) || isBlank(second.getSkillFilePath())) {
            return false;
        }
        return first.getSkillFilePath().equals(second.getSkillFilePath())
                && equalsNullable(first.getContent(), second.getContent());
    }

    private static void validateProvidedSkill(SkillDescriptor skill, List<String> allowedReadRoots) {
        if (skill == null || isBlank(skill.getName()) || isBlank(skill.getDescription()) || isBlank(skill.getSkillFilePath())) {
            throw new IllegalArgumentException("Host-provided Skill requires name, description, and skillFilePath");
        }
        if (skill.getContent() != null) {
            validateSelectedSkillContent(skill, skill.getContent());
            return;
        }
        Path skillFile;
        try {
            skillFile = Paths.get(skill.getSkillFilePath()).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Host-provided Skill path is invalid: " + skill.getSkillFilePath(), ex);
        }
        if (Files.isSymbolicLink(skillFile) || !Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Host-provided Skill file must be a regular non-symlink file: "
                    + skill.getSkillFilePath());
        }
        if (!isWithinAllowedReadRoots(skillFile, allowedReadRoots)) {
            throw new IllegalArgumentException("Host-provided Skill file must be under a declared Skill root: "
                    + skill.getSkillFilePath());
        }
    }

    private static boolean isWithinAllowedReadRoots(Path skillFile, List<String> allowedReadRoots) {
        for (String configuredRoot : safeList(allowedReadRoots)) {
            if (isBlank(configuredRoot)) {
                continue;
            }
            Path root = Paths.get(configuredRoot).toAbsolutePath().normalize();
            if (skillFile.startsWith(root) && resolvesWithin(skillFile, root)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resolvesWithin(Path candidate, Path root) {
        try {
            return candidate.toRealPath().startsWith(root.toRealPath());
        } catch (IOException ex) {
            return false;
        }
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
            if (skill == null || skill.getContent() != null || isBlank(skill.getSkillFilePath())) {
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
        return new ArrayList<String>(roots);
    }

    private static Map<String, String> providedContents(List<SkillDescriptor> skills) {
        Map<String, String> contents = new LinkedHashMap<String, String>();
        for (SkillDescriptor skill : safeList(skills)) {
            if (skill == null || skill.getContent() == null) {
                continue;
            }
            String existing = contents.put(skill.getSkillFilePath(), skill.getContent());
            if (existing != null && !existing.equals(skill.getContent())) {
                throw new IllegalArgumentException("Multiple host-provided Skills share virtual location: "
                        + skill.getSkillFilePath());
            }
        }
        return contents;
    }

    private static String resolveReadToolName(AgentToolRegistry existing) {
        if (!hasToolNamed(existing, BuiltInTools.READ_FILE)) {
            return BuiltInTools.READ_FILE;
        }
        if (hasToolNamed(existing, SKILL_READ_FILE)) {
            throw new IllegalStateException("Skill integration cannot add " + SKILL_READ_FILE
                    + " because the host already owns both read_file and " + SKILL_READ_FILE);
        }
        return SKILL_READ_FILE;
    }

    private static AgentToolRegistry addReadFileTool(AgentToolRegistry existing, String readToolName) {
        Tool readTool = BuiltInTools.readFileTool();
        if (readTool.getFunction() == null) {
            throw new IllegalStateException("Built-in read_file tool is missing a function definition");
        }
        readTool.getFunction().setName(readToolName);
        AgentToolRegistry skillRegistry = new StaticToolRegistry(Collections.<Object>singletonList(readTool));
        return new CompositeToolRegistry(existing, skillRegistry);
    }

    private static ToolExecutor addReadFileExecutor(AgentContext context,
                                                     BuiltInToolContext readContext,
                                                     Map<String, String> providedContents,
                                                     String readToolName) {
        ToolExecutor readExecutor = new SkillReadFileToolExecutor(readContext, providedContents);
        if (context.getExtensionGuardrails() != null && !context.getExtensionGuardrails().isEmpty()) {
            readExecutor = new ExtensionGuardrailToolExecutor(readExecutor, context.getExtensionGuardrails());
        }
        if (context.getPermissionPolicy() != null) {
            readExecutor = new AgentPermissionToolExecutor(
                    readExecutor, context.getPermissionPolicy(), context.getExecutionEnvironment());
        }
        if (!BuiltInTools.READ_FILE.equals(readToolName)) {
            readExecutor = new SkillToolAliasExecutor(readToolName, readExecutor);
        }
        return new RoutingToolExecutor(
                Collections.singletonList(RoutingToolExecutor.route(
                        Collections.singleton(readToolName), readExecutor)),
                context.getToolExecutor());
    }

    private static ToolInterceptor aliasSkillReaderInterceptor(final ToolInterceptor interceptor,
                                                                 final String alias) {
        if (interceptor == null || BuiltInTools.READ_FILE.equals(alias)) {
            return interceptor;
        }
        return new ToolInterceptor() {
            @Override
            public ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context) {
                ToolCallDecision decision = interceptor.beforeToolCall(canonicalSkillReaderCall(call, alias), context);
                return restoreAliasDecision(decision, alias);
            }

            @Override
            public ToolCallDecision afterToolCall(AgentToolCall call, String output, AgentContext context) {
                return interceptor.afterToolCall(canonicalSkillReaderCall(call, alias), output, context);
            }
        };
    }

    private static ToolCallDecision restoreAliasDecision(ToolCallDecision decision, String alias) {
        if (decision == null || decision.getType() != ToolCallDecision.Type.MODIFY
                || decision.getModifiedCall() == null
                || !BuiltInTools.READ_FILE.equals(decision.getModifiedCall().getName())) {
            return decision;
        }
        return ToolCallDecision.modify(aliasSkillReadCall(decision.getModifiedCall(), alias));
    }

    private static AgentToolCall canonicalSkillReaderCall(AgentToolCall call, String alias) {
        return renameSkillReaderCall(call, alias, BuiltInTools.READ_FILE);
    }

    private static AgentToolCall aliasSkillReadCall(AgentToolCall call, String alias) {
        return renameSkillReaderCall(call, BuiltInTools.READ_FILE, alias);
    }

    private static AgentToolCall renameSkillReaderCall(AgentToolCall call, String expectedName, String replacementName) {
        if (call == null || !expectedName.equals(call.getName())) {
            return call;
        }
        return AgentToolCall.builder()
                .name(replacementName)
                .arguments(call.getArguments())
                .callId(call.getCallId())
                .type(call.getType())
                .build();
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

    private static String buildSelectedSkillsPrompt(List<SkillDescriptor> selected) {
        if (selected == null || selected.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
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
        if (skill.getContent() != null) {
            validateSelectedSkillContent(skill, skill.getContent());
            return skill.getContent().trim();
        }
        Path skillFile = Paths.get(skill.getSkillFilePath()).toAbsolutePath().normalize();
        try {
            if (Files.isSymbolicLink(skillFile) || !Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Selected Skill file must be a regular non-symlink file: " + skill.getSkillFilePath());
            }
            String content = new String(Files.readAllBytes(skillFile), StandardCharsets.UTF_8).trim();
            validateSelectedSkillContent(skill, content);
            return content;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read selected Skill: " + valueOrDefault(skill.getName(), skill.getSkillFilePath()), ex);
        }
    }

    private static String skillDirectory(SkillDescriptor skill) {
        if (skill == null || isBlank(skill.getSkillFilePath())) {
            return "(missing)";
        }
        if (skill.getContent() != null) {
            return "(virtual: " + skill.getSkillFilePath() + ")";
        }
        Path parent = Paths.get(skill.getSkillFilePath()).toAbsolutePath().normalize().getParent();
        return parent == null ? "(missing)" : parent.toString();
    }

    private static void validateSelectedSkillContent(SkillDescriptor skill, String content) {
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SELECTED_SKILL_BYTES) {
            throw new IllegalArgumentException("Selected Skill exceeds the " + MAX_SELECTED_SKILL_BYTES
                    + " byte limit: " + valueOrDefault(skill.getName(), skill.getSkillFilePath()));
        }
        if (content != null && content.length() > MAX_SELECTED_SKILL_CHARS) {
            throw new IllegalArgumentException("Selected Skill exceeds the " + MAX_SELECTED_SKILL_CHARS
                    + " character limit: " + valueOrDefault(skill.getName(), skill.getSkillFilePath()));
        }
    }

    /** Maps the public fallback name back before guardrail and permission evaluation. */
    private static final class SkillToolAliasExecutor implements ToolExecutor {

        private final String alias;
        private final ToolExecutor delegate;

        private SkillToolAliasExecutor(String alias, ToolExecutor delegate) {
            this.alias = alias;
            this.delegate = delegate;
        }

        @Override
        public String execute(AgentToolCall call) throws Exception {
            if (call == null || !alias.equals(call.getName())) {
                throw new IllegalArgumentException("Unsupported Skill tool: " + (call == null ? null : call.getName()));
            }
            return delegate.execute(canonicalSkillReaderCall(call, alias));
        }
    }

    /**
     * Keeps explicit Skill contents request-scoped so they do not mutate long-lived Agent memory
     * or the cacheable system prefix.
     */
    private static final class SkillPromptOverlayMemory implements AgentMemory {

        private final AgentMemory delegate;
        private final Object promptItem;

        private SkillPromptOverlayMemory(AgentMemory delegate, String prompt) {
            this.delegate = delegate;
            this.promptItem = AgentInputItem.userMessage(prompt);
        }

        @Override
        public void addUserInput(Object input) {
            delegate.addUserInput(input);
        }

        @Override
        public void addOutputItems(List<Object> items) {
            delegate.addOutputItems(items);
        }

        @Override
        public void addToolOutput(String callId, String output) {
            delegate.addToolOutput(callId, output);
        }

        @Override
        public List<Object> getItems() {
            List<Object> items = new ArrayList<Object>();
            items.add(promptItem);
            items.addAll(delegate.getItems());
            return items;
        }

        @Override
        public String getSummary() {
            return delegate.getSummary();
        }

        @Override
        public MemorySnapshot snapshot() {
            return delegate.snapshot();
        }

        @Override
        public void restore(MemorySnapshot snapshot) {
            delegate.restore(snapshot);
        }

        @Override
        public void clear() {
            delegate.clear();
        }
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

    private static boolean equalsNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
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
