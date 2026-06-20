package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.coding.skill.CodingSkillDescriptor;
import io.github.lnyocly.ai4j.coding.skill.CodingSkillDiscovery;
import io.github.lnyocly.ai4j.coding.workspace.LocalWorkspaceFileService;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceFileReadResult;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CodingSkillSupportTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldDiscoverSkillsAndInjectAvailableSkillsPrompt() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-skills").toPath();
        Path workspaceSkillFile = writeSkill(
                workspaceRoot.resolve(".ai4j").resolve("skills").resolve("reviewer").resolve("SKILL.md"),
                "---\nname: reviewer\ndescription: Review code changes for risks.\n---\n"
        );
        Path fakeHome = temporaryFolder.newFolder("fake-home").toPath();
        Path globalSkillFile = writeSkill(
                fakeHome.resolve(".ai4j").resolve("skills").resolve("refactorer").resolve("SKILL.md"),
                "# refactorer\nSimplify recently modified code without changing behavior.\n"
        );

        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", fakeHome.toString());
        try {
            WorkspaceContext workspaceContext = WorkspaceContext.builder()
                    .rootPath(workspaceRoot.toString())
                    .description("Skill-aware workspace")
                    .build();

            WorkspaceContext enriched = CodingSkillDiscovery.enrich(workspaceContext);
            List<CodingSkillDescriptor> skills = enriched.getAvailableSkills();
            assertEquals(2, skills.size());
            assertEquals("reviewer", skills.get(0).getName());
            assertEquals("workspace", skills.get(0).getSource());
            assertEquals(workspaceSkillFile.toAbsolutePath().normalize().toString(), skills.get(0).getSkillFilePath());
            assertEquals("refactorer", skills.get(1).getName());
            assertEquals("global", skills.get(1).getSource());
            assertEquals(globalSkillFile.toAbsolutePath().normalize().toString(), skills.get(1).getSkillFilePath());
            assertTrue(enriched.getAllowedReadRoots().contains(workspaceSkillFile.getParent().getParent().toString()));
            assertTrue(enriched.getAllowedReadRoots().contains(globalSkillFile.getParent().getParent().toString()));

            CapturingModelClient modelClient = new CapturingModelClient();
            CodingAgent agent = CodingAgents.builder()
                    .modelClient(modelClient)
                    .model("glm-4.5-flash")
                    .workspaceContext(workspaceContext)
                    .systemPrompt("Base prompt.")
                    .build();

            CodingSession session = agent.newSession();
            try {
                session.run("Use the most relevant skill.");
            } finally {
                session.close();
            }

            AgentPrompt prompt = modelClient.getLastPrompt();
            assertNotNull(prompt);
            assertTrue(prompt.getSystemPrompt().contains("<available_skills>"));
            assertTrue(prompt.getSystemPrompt().contains("name: reviewer"));
            assertTrue(prompt.getSystemPrompt().contains(workspaceSkillFile.toAbsolutePath().normalize().toString()));
            assertTrue(prompt.getSystemPrompt().contains("name: refactorer"));
            assertTrue(prompt.getSystemPrompt().contains(globalSkillFile.toAbsolutePath().normalize().toString()));
        } finally {
            restoreProperty("user.home", originalUserHome);
        }
    }

    @Test
    public void shouldAllowReadOnlySkillFilesOutsideWorkspace() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-read-skill").toPath();
        Path fakeHome = temporaryFolder.newFolder("fake-home-read").toPath();
        Path globalSkillFile = writeSkill(
                fakeHome.resolve(".ai4j").resolve("skills").resolve("planner").resolve("SKILL.md"),
                "---\nname: planner\ndescription: Plan implementation steps.\n---\n"
        );

        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", fakeHome.toString());
        try {
            WorkspaceContext workspaceContext = CodingSkillDiscovery.enrich(WorkspaceContext.builder()
                    .rootPath(workspaceRoot.toString())
                    .build());

            LocalWorkspaceFileService fileService = new LocalWorkspaceFileService(workspaceContext);
            WorkspaceFileReadResult result = fileService.readFile(globalSkillFile.toString(), 1, 2, 4000);
            assertTrue(result.getContent().contains("planner"));
            assertEquals(globalSkillFile.toAbsolutePath().normalize().toString().replace('\\', '/'), result.getPath());

            try {
                fileService.writeFile(globalSkillFile.toString(), "mutated", false);
                fail("writeFile should not allow writes outside the workspace root");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("escapes workspace root"));
            }
        } finally {
            restoreProperty("user.home", originalUserHome);
        }
    }

    @Test
    public void shouldInjectEnabledExtensionSkillAndPromptResources() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("workspace-extension-resources").toPath();
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(workspaceRoot.toString())
                .description("Extension resource workspace")
                .build();
        ExtensionRegistry registry = ExtensionRegistry.of(new CodingResourceExtension())
                .enable("coding-resource-pack");
        CapturingModelClient modelClient = new CapturingModelClient();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model("glm-4.5-flash")
                .workspaceContext(workspaceContext)
                .extensions(registry)
                .systemPrompt("Base prompt.")
                .build();

        WorkspaceContext enriched = agent.getWorkspaceContext();
        assertEquals(1, enriched.getAvailableSkills().size());
        assertEquals("coding-extension-skill", enriched.getAvailableSkills().get(0).getName());
        assertEquals("extension:coding-resource-pack", enriched.getAvailableSkills().get(0).getSource());
        assertEquals(1, enriched.getAvailablePrompts().size());
        assertEquals("coding-extension-prompt", enriched.getAvailablePrompts().get(0).getName());
        assertEquals("extension:coding-resource-pack", enriched.getAvailablePrompts().get(0).getSource());

        LocalWorkspaceFileService fileService = new LocalWorkspaceFileService(enriched);
        WorkspaceFileReadResult skillRead = fileService.readFile(
                enriched.getAvailableSkills().get(0).getSkillFilePath(),
                1,
                10,
                4000
        );
        assertTrue(skillRead.getContent().contains("name: coding-extension-skill"));
        assertTrue(skillRead.getContent().contains("Verify coding agent extension skill projection."));
        WorkspaceFileReadResult promptRead = fileService.readFile(
                enriched.getAvailablePrompts().get(0).getPromptFilePath(),
                1,
                10,
                4000
        );
        assertTrue(promptRead.getContent().contains("Coding extension prompt fixture"));

        try {
            fileService.writeFile(enriched.getAvailableSkills().get(0).getSkillFilePath(), "mutated", false);
            fail("extension resources should be read-only outside the workspace root");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("escapes workspace root"));
        }

        try (CodingSession session = agent.newSession()) {
            session.run("Use extension resources.");
        }
        AgentPrompt prompt = modelClient.getLastPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.getSystemPrompt().contains("<available_skills>"));
        assertTrue(prompt.getSystemPrompt().contains("name: coding-extension-skill"));
        assertTrue(prompt.getSystemPrompt().contains("<available_prompts>"));
        assertTrue(prompt.getSystemPrompt().contains("name: coding-extension-prompt"));
    }

    private static Path writeSkill(Path skillFile, String content) throws Exception {
        Files.createDirectories(skillFile.getParent());
        Files.write(skillFile, content.getBytes(StandardCharsets.UTF_8));
        return skillFile;
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }

    private static final class CodingResourceExtension implements Ai4jExtension {
        public ExtensionManifest manifest() {
            return ExtensionManifest.builder()
                    .id("coding-resource-pack")
                    .name("Coding Resource Pack")
                    .capability(ExtensionCapability.SKILL)
                    .capability(ExtensionCapability.PROMPT)
                    .build();
        }

        public void apply(ExtensionContext context) {
            context.skills().register(ExtensionSkillResource.builder()
                    .name("coding-extension-skill")
                    .description("Coding extension skill")
                    .resourcePath("skills/coding-extension/SKILL.md")
                    .build());
            context.prompts().register(ExtensionPromptResource.builder()
                    .name("coding-extension-prompt")
                    .description("Coding extension prompt")
                    .resourcePath("prompts/coding-extension.md")
                    .build());
        }
    }

    private static final class CapturingModelClient implements AgentModelClient {

        private AgentPrompt lastPrompt;

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            lastPrompt = prompt;
            return AgentModelResult.builder()
                    .outputText("ok")
                    .rawResponse("ok")
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }

        public AgentPrompt getLastPrompt() {
            return lastPrompt;
        }
    }
}
