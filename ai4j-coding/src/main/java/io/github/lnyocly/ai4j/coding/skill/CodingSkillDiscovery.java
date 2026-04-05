package io.github.lnyocly.ai4j.coding.skill;

import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

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

public final class CodingSkillDiscovery {

    private static final String SKILL_FILE_NAME = "SKILL.md";

    private CodingSkillDiscovery() {
    }

    public static WorkspaceContext enrich(WorkspaceContext workspaceContext) {
        WorkspaceContext source = workspaceContext == null
                ? WorkspaceContext.builder().build()
                : workspaceContext;
        DiscoveryResult result = discover(source);
        return source.toBuilder()
                .allowedReadRoots(result.allowedReadRoots)
                .availableSkills(result.skills)
                .build();
    }

    public static DiscoveryResult discover(WorkspaceContext workspaceContext) {
        WorkspaceContext source = workspaceContext == null
                ? WorkspaceContext.builder().build()
                : workspaceContext;
        List<Path> roots = resolveSkillRoots(source);
        Map<String, CodingSkillDescriptor> byName = new LinkedHashMap<String, CodingSkillDescriptor>();
        Set<String> allowedReadRoots = new LinkedHashSet<String>();
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            allowedReadRoots.add(root.toAbsolutePath().normalize().toString());
            for (CodingSkillDescriptor descriptor : discoverFromRoot(root, source.getRoot())) {
                String normalizedName = normalizeKey(descriptor.getName());
                if (!byName.containsKey(normalizedName)) {
                    byName.put(normalizedName, descriptor);
                }
            }
        }
        return new DiscoveryResult(
                new ArrayList<CodingSkillDescriptor>(byName.values()),
                new ArrayList<String>(allowedReadRoots)
        );
    }

    private static List<Path> resolveSkillRoots(WorkspaceContext workspaceContext) {
        Set<Path> roots = new LinkedHashSet<Path>();
        Path workspaceRoot = workspaceContext.getRoot();
        roots.add(workspaceRoot.resolve(".ai4j").resolve("skills").toAbsolutePath().normalize());
        String userHome = System.getProperty("user.home");
        if (!isBlank(userHome)) {
            roots.add(Paths.get(userHome).resolve(".ai4j").resolve("skills").toAbsolutePath().normalize());
        }
        if (workspaceContext.getSkillDirectories() != null) {
            for (String configuredRoot : workspaceContext.getSkillDirectories()) {
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

    private static List<CodingSkillDescriptor> discoverFromRoot(Path root, Path workspaceRoot) {
        Path directSkillFile = resolveSkillFile(root);
        if (directSkillFile != null) {
            return Collections.singletonList(buildDescriptor(directSkillFile, root, workspaceRoot));
        }

        List<CodingSkillDescriptor> descriptors = new ArrayList<CodingSkillDescriptor>();
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

    private static CodingSkillDescriptor buildDescriptor(Path skillFile, Path skillRoot, Path workspaceRoot) {
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
        return CodingSkillDescriptor.builder()
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

        private final List<CodingSkillDescriptor> skills;
        private final List<String> allowedReadRoots;

        public DiscoveryResult(List<CodingSkillDescriptor> skills, List<String> allowedReadRoots) {
            this.skills = skills == null ? Collections.<CodingSkillDescriptor>emptyList() : skills;
            this.allowedReadRoots = allowedReadRoots == null ? Collections.<String>emptyList() : allowedReadRoots;
        }

        public List<CodingSkillDescriptor> getSkills() {
            return skills;
        }

        public List<String> getAllowedReadRoots() {
            return allowedReadRoots;
        }
    }
}
