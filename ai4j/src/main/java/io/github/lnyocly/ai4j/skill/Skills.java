package io.github.lnyocly.ai4j.skill;

import io.github.lnyocly.ai4j.tool.BuiltInToolContext;
import io.github.lnyocly.ai4j.tool.BuiltInTools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
                if (root == null || Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
                    continue;
                }
                allowedReadRoots.add(root.toAbsolutePath().normalize().toString());
                for (SkillDescriptor descriptor : discoverFromRoot(root, resolvedWorkspaceRoot)) {
                    String normalizedName = normalizeKey(descriptor.getName());
                    SkillDescriptor existing = byName.get(normalizedName);
                    if (existing == null) {
                        byName.put(normalizedName, descriptor);
                    } else if (!sameSkillFile(existing, descriptor)) {
                        throw new IllegalArgumentException("Duplicate Skill name '" + descriptor.getName()
                                + "' discovered at " + existing.getSkillFilePath()
                                + " and " + descriptor.getSkillFilePath());
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

    /**
     * Creates a read_file context that exposes only the supplied Skill roots, never the workspace.
     */
    public static BuiltInToolContext createSkillToolContext(List<String> skillRoots) {
        List<String> allowedReadRoots = new ArrayList<String>();
        if (skillRoots != null) {
            for (String skillRoot : skillRoots) {
                if (!isBlank(skillRoot)) {
                    allowedReadRoots.add(Paths.get(skillRoot).toAbsolutePath().normalize().toString());
                }
            }
        }
        Path restrictedRoot = allowedReadRoots.isEmpty()
                ? Paths.get(".").toAbsolutePath().normalize().resolve(".ai4j-skill-read-denied")
                : Paths.get(allowedReadRoots.get(0)).toAbsolutePath().normalize();
        return BuiltInToolContext.builder()
                .workspaceRoot(restrictedRoot.toString())
                .allowedReadRoots(allowedReadRoots)
                .restrictReadToAllowedRoots(true)
                .build();
    }

    public static String appendAvailableSkillsPrompt(String basePrompt,
                                                     List<? extends SkillDescriptor> availableSkills) {
        return appendAvailableSkillsPrompt(basePrompt, availableSkills, BuiltInTools.READ_FILE);
    }

    /**
     * Appends the Skill catalog using the actual reader exposed to the model.
     */
    public static String appendAvailableSkillsPrompt(String basePrompt,
                                                     List<? extends SkillDescriptor> availableSkills,
                                                     String readToolName) {
        String skillPrompt = buildAvailableSkillsPrompt(availableSkills, readToolName);
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
        appendAvailableSkillsPrompt(builder, availableSkills, BuiltInTools.READ_FILE);
    }

    /**
     * Appends the Skill catalog using the actual reader exposed to the model.
     */
    public static void appendAvailableSkillsPrompt(StringBuilder builder,
                                                   List<? extends SkillDescriptor> availableSkills,
                                                   String readToolName) {
        if (builder == null) {
            return;
        }
        String skillPrompt = buildAvailableSkillsPrompt(availableSkills, readToolName);
        if (isBlank(skillPrompt)) {
            return;
        }
        if (builder.length() > 0 && !endsWithBlankLine(builder)) {
            builder.append("\n\n");
        }
        builder.append(skillPrompt);
    }

    public static String buildAvailableSkillsPrompt(List<? extends SkillDescriptor> availableSkills) {
        return buildAvailableSkillsPrompt(availableSkills, BuiltInTools.READ_FILE);
    }

    /**
     * Builds a cache-stable Skill catalog and names the reader available for progressive loading.
     */
    public static String buildAvailableSkillsPrompt(List<? extends SkillDescriptor> availableSkills,
                                                    String readToolName) {
        if (availableSkills == null || availableSkills.isEmpty()) {
            return null;
        }
        String resolvedReadToolName = firstNonBlank(readToolName, BuiltInTools.READ_FILE);
        StringBuilder entries = new StringBuilder();
        for (SkillDescriptor skill : availableSkills) {
            if (skill == null || skill.isDisableModelInvocation()) {
                continue;
            }
            entries.append("  <skill>\n");
            entries.append("    <name>").append(escapeXml(firstNonBlank(skill.getName(), "skill"))).append("</name>\n");
            entries.append("    <description>").append(escapeXml(firstNonBlank(skill.getDescription(), "No description available."))).append("</description>\n");
            entries.append("    <location>").append(escapeXml(firstNonBlank(skill.getSkillFilePath(), "(missing)"))).append("</location>\n");
            entries.append("  </skill>\n");
        }
        if (entries.length() == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("The following skills provide specialized instructions for specific tasks. ")
                .append("Do not read every skill file up front. When a task matches a skill's description, ")
                .append("read its SKILL.md with ").append(resolvedReadToolName)
                .append(" before proceeding. Resolve relative paths against the Skill directory.\n");
        builder.append("<available_skills>\n");
        builder.append(entries);
        builder.append("</available_skills>\n");
        builder.append("Only use a skill after reading its SKILL.md. Prefer the smallest relevant skill set and reuse ")
                .append(resolvedReadToolName)
                .append(" instead of asking for a dedicated skill tool.");
        return builder.toString().trim();
    }

    private static boolean sameSkillFile(SkillDescriptor first, SkillDescriptor second) {
        if (first == null || second == null || isBlank(first.getSkillFilePath()) || isBlank(second.getSkillFilePath())) {
            return false;
        }
        Path firstPath = Paths.get(first.getSkillFilePath()).toAbsolutePath().normalize();
        Path secondPath = Paths.get(second.getSkillFilePath()).toAbsolutePath().normalize();
        if (firstPath.equals(secondPath)) {
            return true;
        }
        try {
            return Files.isSameFile(firstPath, secondPath);
        } catch (IOException ex) {
            return false;
        }
    }

    private static List<Path> resolveSkillRoots(Path workspaceRoot, List<String> skillDirectories) {
        Set<Path> roots = new LinkedHashSet<Path>();
        roots.add(workspaceRoot.resolve(".ai4j").resolve("skills").toAbsolutePath().normalize());
        roots.add(workspaceRoot.resolve(".agents").resolve("skills").toAbsolutePath().normalize());
        String userHome = System.getProperty("user.home");
        if (!isBlank(userHome)) {
            roots.add(Paths.get(userHome).resolve(".ai4j").resolve("skills").toAbsolutePath().normalize());
            roots.add(Paths.get(userHome).resolve(".agents").resolve("skills").toAbsolutePath().normalize());
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

        final List<Path> skillFiles = new ArrayList<Path>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (Files.isSymbolicLink(directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (directory.equals(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path skillFile = resolveSkillFile(directory);
                    if (skillFile == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    skillFiles.add(skillFile);
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
        } catch (IOException ignored) {
        }
        Collections.sort(skillFiles, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                String leftPath = left.toAbsolutePath().normalize().toString();
                String rightPath = right.toAbsolutePath().normalize().toString();
                int result = leftPath.compareToIgnoreCase(rightPath);
                return result != 0 ? result : leftPath.compareTo(rightPath);
            }
        });
        List<SkillDescriptor> descriptors = new ArrayList<SkillDescriptor>();
        for (Path skillFile : skillFiles) {
            descriptors.add(buildDescriptor(skillFile, root, workspaceRoot));
        }
        return descriptors;
    }

    private static Path resolveSkillFile(Path directory) {
        if (directory == null || Files.isSymbolicLink(directory)) {
            return null;
        }
        Path upper = directory.resolve(SKILL_FILE_NAME);
        if (!Files.isSymbolicLink(upper) && Files.isRegularFile(upper, LinkOption.NOFOLLOW_LINKS)) {
            return upper;
        }
        Path lower = directory.resolve("skill.md");
        return !Files.isSymbolicLink(lower) && Files.isRegularFile(lower, LinkOption.NOFOLLOW_LINKS) ? lower : null;
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
                .disableModelInvocation(parseBoolean(parseFrontMatterValue(content, "disable-model-invocation")))
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

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value == null ? null : value.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String escapeXml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
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
