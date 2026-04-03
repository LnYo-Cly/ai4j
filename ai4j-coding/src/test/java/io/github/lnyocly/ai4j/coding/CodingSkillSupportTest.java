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
