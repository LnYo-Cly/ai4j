package io.github.lnyocly.ai4j.skill;

import io.github.lnyocly.ai4j.tool.BuiltInToolContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
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

public final class Skills {

    private static final String SKILL_FILE_NAME = "SKILL.md";

    private Skills() {
    }

    public static DiscoveryResult discoverDefault(Path workspaceRoot) {
        return discoverDefault(workspaceRoot, null);
    }

    public static DiscoveryResult discoverDefault(String workspaceRoot, List<String> skillDirectories) {
        return discoverDefault(isBlank(workspaceRoot) ? null : Paths.get(workspaceRoot), skillDirectories);
    }

    public static DiscoveryResult discoverDefault(Path workspaceRoot, List<String> skillDirectories) {
        Path resolvedWorkspaceRoot = normalizeWorkspaceRoot(workspaceRoot);
        List<Path> roots = resolveSkillRoots(resolvedWorkspaceRoot, skillDirectories);
        return discover(resolvedWorkspaceRoot, roots);
    }

    public static DiscoveryResult discover(Path workspaceRoot, List<Path> roots) {
        Path resolvedWorkspaceRoot = normalizeWorkspaceRoot(workspaceRoot);
        Map<String, SkillDescriptor> byName = new LinkedHashMap<String, SkillDescriptor>();
        Set<String> allowedReadRoots = new LinkedHashSet<String>();
        if (roots != null) {
            for (Path root : roots) {
                if (root == null || !Files.isDirectory(root)) {
                    continue;
                }
                allowedReadRoots.add(root.toAbsolutePath().normalize().toString());
                for (SkillDescriptor descriptor : discoverFromRoot(root, resolvedWorkspaceRoot)) {
                    String normalizedName = normalizeKey(descriptor.getName());
                    if (!byName.containsKey(normalizedName)) {
                        byName.put(normalizedName, descriptor);
                    }
                }
            }
        }
        return new DiscoveryResult(
                new ArrayList<SkillDescriptor>(byName.values()),
                new ArrayList<String>(allowedReadRoots)
        );
    }

    public static BuiltInToolContext createToolContext(Path workspaceRoot) {
        DiscoveryResult discovery = discoverDefault(workspaceRoot);
        return createToolContext(workspaceRoot, discovery);
    }

    public static BuiltInToolContext createToolContext(Path workspaceRoot, DiscoveryResult discovery) {
        Path resolvedWorkspaceRoot = normalizeWorkspaceRoot(workspaceRoot);
        List<String> allowedReadRoots = discovery == null
                ? Collections.<String>emptyList()
                : discovery.getAllowedReadRoots();
        return BuiltInToolContext.builder()
                .workspaceRoot(resolvedWorkspaceRoot.toString())
                .allowedReadRoots(new ArrayList<String>(allowedReadRoots))
                .build();
    }

    public static String appendAvailableSkillsPrompt(String basePrompt,
                                                     List<? extends SkillDescriptor> availableSkills) {
        String skillPrompt = buildAvailableSkillsPrompt(availableSkills);
        if (isBlank(basePrompt)) {
            return skillPrompt;
        }
        if (isBlank(skillPrompt)) {
            return basePrompt;
        }
        return basePrompt + "\n\n" + skillPrompt;
    }

    public static void appendAvailableSkillsPrompt(StringBuilder builder,
                                                   List<? extends SkillDescriptor> availableSkills) {
        if (builder == null) {
            return;
        }
        String skillPrompt = buildAvailableSkillsPrompt(availableSkills);
        if (isBlank(skillPrompt)) {
            return;
        }
        if (builder.length() > 0 && !endsWithBlankLine(builder)) {
            builder.append("\n\n");
        }
        builder.append(skillPrompt);
    }

    public static String buildAvailableSkillsPrompt(List<? extends SkillDescriptor> availableSkills) {
        if (availableSkills == null || availableSkills.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Some reusable skills are installed. Do not read every skill file up front. ")
                .append("When the task clearly matches a skill, read that SKILL.md with read_file first and then follow it.\n");
        builder.append("<available_skills>\n");
        for (SkillDescriptor skill : availableSkills) {
            if (skill == null) {
                continue;
            }
            builder.append("- name: ").append(firstNonBlank(skill.getName(), "skill")).append("\n");
            builder.append("  path: ").append(firstNonBlank(skill.getSkillFilePath(), "(missing)")).append("\n");
            builder.append("  description: ").append(firstNonBlank(skill.getDescription(), "No description available.")).append("\n");
        }
        builder.append("</available_skills>\n");
        builder.append("Only use a skill after reading its SKILL.md. Prefer the smallest relevant skill set and reuse read_file instead of asking for a dedicated skill tool.");
        return builder.toString().trim();
    }

    private static List<Path> resolveSkillRoots(Path workspaceRoot, List<String> skillDirectories) {
        Set<Path> roots = new LinkedHashSet<Path>();
        roots.add(workspaceRoot.resolve(".ai4j").resolve("skills").toAbsolutePath().normalize());
        String userHome = System.getProperty("user.home");
        if (!isBlank(userHome)) {
            roots.add(Paths.get(userHome).resolve(".ai4j").resolve("skills").toAbsolutePath().normalize());
        }
        if (skillDirectories != null) {
            for (String configuredRoot : skillDirectories) {
                if (isBlank(configuredRoot)) {
                    continue;
                }
                Path root = Paths.get(configuredRoot);
                if (!root.isAbsolute()) {
                    root = workspaceRoot.resolve(configuredRoot);
                }
                roots.add(root.toAbsolutePath().normalize());
            }
        }
        return new ArrayList<Path>(roots);
    }

    private static List<SkillDescriptor> discoverFromRoot(Path root, Path workspaceRoot) {
        Path directSkillFile = resolveSkillFile(root);
        if (directSkillFile != null) {
            return Collections.singletonList(buildDescriptor(directSkillFile, root, workspaceRoot));
        }

        List<SkillDescriptor> descriptors = new ArrayList<SkillDescriptor>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }
                Path skillFile = resolveSkillFile(child);
                if (skillFile == null) {
                    continue;
                }
                descriptors.add(buildDescriptor(skillFile, root, workspaceRoot));
            }
        } catch (IOException ignored) {
        }
        return descriptors;
    }

    private static Path resolveSkillFile(Path directory) {
        Path upper = directory.resolve(SKILL_FILE_NAME);
        if (Files.isRegularFile(upper)) {
            return upper;
        }
        Path lower = directory.resolve("skill.md");
        return Files.isRegularFile(lower) ? lower : null;
    }

    private static SkillDescriptor buildDescriptor(Path skillFile, Path skillRoot, Path workspaceRoot) {
        String content = readQuietly(skillFile);
        String name = firstNonBlank(
                parseFrontMatterValue(content, "name"),
                parseHeading(content),
                inferName(skillFile)
        );
        String description = firstNonBlank(
                parseFrontMatterValue(content, "description"),
                parseFirstParagraph(content),
                "No description available."
        );
        return SkillDescriptor.builder()
                .name(name)
                .description(description)
                .skillFilePath(skillFile.toAbsolutePath().normalize().toString())
                .source(resolveSource(skillRoot, workspaceRoot))
                .build();
    }

    private static String resolveSource(Path skillRoot, Path workspaceRoot) {
        if (skillRoot != null && workspaceRoot != null && skillRoot.startsWith(workspaceRoot)) {
            return "workspace";
        }
        return "global";
    }

    private static Path normalizeWorkspaceRoot(Path workspaceRoot) {
        if (workspaceRoot == null) {
            return Paths.get(".").toAbsolutePath().normalize();
        }
        return workspaceRoot.toAbsolutePath().normalize();
    }

    private static String readQuietly(Path skillFile) {
        try {
            return new String(Files.readAllBytes(skillFile), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static String parseFrontMatterValue(String content, String key) {
        if (isBlank(content) || isBlank(key)) {
            return null;
        }
        boolean inFrontMatter = false;
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if ("---".equals(trimmed)) {
                if (!inFrontMatter) {
                    inFrontMatter = true;
                    continue;
                }
                return null;
            }
            if (!inFrontMatter) {
                break;
            }
            String prefix = key + ":";
            if (trimmed.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return stripQuotes(trimmed.substring(prefix.length()).trim());
            }
        }
        return null;
    }

    private static String parseHeading(String content) {
        if (isBlank(content)) {
            return null;
        }
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "").trim();
            }
        }
        return null;
    }

    private static String parseFirstParagraph(String content) {
        if (isBlank(content)) {
            return null;
        }
        String[] lines = content.split("\\r?\\n");
        StringBuilder paragraph = new StringBuilder();
        boolean inFrontMatter = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if ("---".equals(trimmed) && paragraph.length() == 0) {
                inFrontMatter = !inFrontMatter;
                continue;
            }
            if (inFrontMatter || trimmed.isEmpty() || trimmed.startsWith("#")) {
                if (paragraph.length() > 0) {
                    break;
                }
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(trimmed);
        }
        return paragraph.length() == 0 ? null : paragraph.toString().trim();
    }

    private static String inferName(Path skillFile) {
        Path parent = skillFile == null ? null : skillFile.getParent();
        if (parent == null) {
            return "skill";
        }
        return parent.getFileName().toString();
    }

    private static boolean endsWithBlankLine(StringBuilder builder) {
        int length = builder.length();
        return length >= 2
                && builder.charAt(length - 1) == '\n'
                && builder.charAt(length - 2) == '\n';
    }

    private static String normalizeKey(String value) {
        return isBlank(value) ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String stripQuotes(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class DiscoveryResult {

        private final List<SkillDescriptor> skills;
        private final List<String> allowedReadRoots;

        public DiscoveryResult(List<SkillDescriptor> skills, List<String> allowedReadRoots) {
            this.skills = skills == null ? Collections.<SkillDescriptor>emptyList() : skills;
            this.allowedReadRoots = allowedReadRoots == null ? Collections.<String>emptyList() : allowedReadRoots;
        }

        public List<SkillDescriptor> getSkills() {
            return skills;
        }

        public List<String> getAllowedReadRoots() {
            return allowedReadRoots;
        }
    }
}
