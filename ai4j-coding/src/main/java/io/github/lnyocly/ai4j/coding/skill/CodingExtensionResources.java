package io.github.lnyocly.ai4j.coding.skill;

import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.coding.prompt.CodingPromptDescriptor;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.resource.ExtensionResourceResolver;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CodingExtensionResources {

    private CodingExtensionResources() {
    }

    public static WorkspaceContext enrich(WorkspaceContext workspaceContext, ExtensionRegistry registry) {
        WorkspaceContext source = workspaceContext == null
                ? WorkspaceContext.builder().build()
                : workspaceContext;
        if (registry == null) {
            return source;
        }
        ExtensionRuntimeSnapshot snapshot = registry.snapshot();
        if (isEmpty(snapshot.getSkills()) && isEmpty(snapshot.getPrompts())) {
            return source;
        }

        Path resourceRoot;
        try {
            resourceRoot = Files.createTempDirectory("ai4j-extension-resources-").toAbsolutePath().normalize();
        } catch (IOException ex) {
            throw new ExtensionException("failed to create extension resource directory", ex);
        }

        List<CodingSkillDescriptor> skills = new ArrayList<CodingSkillDescriptor>();
        if (source.getAvailableSkills() != null) {
            skills.addAll(source.getAvailableSkills());
        }
        for (ExtensionSkillResource resource : snapshot.getSkills()) {
            skills.add(toCodingSkillDescriptor(resource, registry, resourceRoot));
        }

        List<CodingPromptDescriptor> prompts = new ArrayList<CodingPromptDescriptor>();
        if (source.getAvailablePrompts() != null) {
            prompts.addAll(source.getAvailablePrompts());
        }
        prompts.addAll(materializePrompts(snapshot.getPrompts(), registry, resourceRoot));

        List<String> allowedReadRoots = new ArrayList<String>();
        if (source.getAllowedReadRoots() != null) {
            allowedReadRoots.addAll(source.getAllowedReadRoots());
        }
        allowedReadRoots.add(resourceRoot.toString());

        return source.toBuilder()
                .availableSkills(skills)
                .availablePrompts(prompts)
                .allowedReadRoots(allowedReadRoots)
                .build();
    }

    private static CodingSkillDescriptor toCodingSkillDescriptor(ExtensionSkillResource resource,
                                                                 ExtensionRegistry registry,
                                                                 Path resourceRoot) {
        String extensionId = requireExtensionId(resource == null ? null : resource.getExtensionId(), "skill");
        String content = ExtensionResourceResolver.readTextStrict(
                resource.getResourcePath(),
                registry.getExtensionClassLoader(extensionId)
        );
        Path skillFile = resourceRoot
                .resolve("skills")
                .resolve(safeFileName(extensionId))
                .resolve(safeFileName(resource.getName()))
                .resolve("SKILL.md")
                .toAbsolutePath()
                .normalize();
        writeText(skillFile, content);
        return CodingSkillDescriptor.builder()
                .name(resource.getName())
                .description(resource.getDescription())
                .skillFilePath(skillFile.toString())
                .source("extension:" + extensionId)
                .build();
    }

    private static List<CodingPromptDescriptor> materializePrompts(List<ExtensionPromptResource> prompts,
                                                                   ExtensionRegistry registry,
                                                                   Path resourceRoot) {
        if (prompts == null || prompts.isEmpty()) {
            return new ArrayList<CodingPromptDescriptor>();
        }
        List<CodingPromptDescriptor> descriptors = new ArrayList<CodingPromptDescriptor>();
        for (ExtensionPromptResource prompt : prompts) {
            if (prompt == null) {
                continue;
            }
            String extensionId = requireExtensionId(prompt.getExtensionId(), "prompt");
            String content = ExtensionResourceResolver.readTextStrict(
                    prompt.getResourcePath(),
                    registry.getExtensionClassLoader(extensionId)
            );
            Path promptFile = resourceRoot
                    .resolve("prompts")
                    .resolve(safeFileName(extensionId))
                    .resolve(safeFileName(prompt.getName()) + ".md")
                    .toAbsolutePath()
                    .normalize();
            writeText(promptFile, content);
            descriptors.add(CodingPromptDescriptor.builder()
                    .name(prompt.getName())
                    .description(prompt.getDescription())
                    .promptFilePath(promptFile.toString())
                    .source("extension:" + extensionId)
                    .build());
        }
        return descriptors;
    }

    private static void writeText(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new ExtensionException("failed to materialize extension resource: " + file, ex);
        }
    }

    private static String requireExtensionId(String extensionId, String resourceType) {
        if (extensionId == null || extensionId.trim().isEmpty()) {
            throw new ExtensionException("extension " + resourceType + " resource is missing extension id");
        }
        return extensionId.trim();
    }

    private static String safeFileName(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "resource";
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
