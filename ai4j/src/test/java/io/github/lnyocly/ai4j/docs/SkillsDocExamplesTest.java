package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.skill.SkillDescriptor;
import io.github.lnyocly.ai4j.skill.Skills;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Executable source of truth for the snippets in
 * {@code docs/core-sdk/skills/overview.md} and {@code discovery-and-loading.md}.
 *
 * <p>Creates a temp workspace with a SKILL.md, runs discovery end to end.
 * No key, no network; runs in CI.
 */
public class SkillsDocExamplesTest {

    // ---- §7 发现 + 目录 ----

    @Test
    public void discoverFindsSkillFromWorkspace() throws Exception {
        Path workspace = createWorkspaceWithSkill();

        Skills.DiscoveryResult result = Skills.discoverDefault(workspace);

        Assert.assertNotNull(result);
        Assert.assertFalse("应发现 skill", result.getSkills().isEmpty());

        SkillDescriptor skill = result.getSkills().get(0);
        Assert.assertEquals("code-review", skill.getName());
        Assert.assertTrue(skill.getDescription().contains("code review"));
        Assert.assertTrue("应记录 skill 文件路径", skill.getSkillFilePath().endsWith("SKILL.md"));
        Assert.assertFalse("应给出只读根", result.getAllowedReadRoots().isEmpty());
    }

    // ---- §6.3 暴露层：buildAvailableSkillsPrompt 只给目录不给正文 ----

    @Test
    public void availableSkillsPromptListsNamesNotFullBody() throws Exception {
        Path workspace = createWorkspaceWithSkill();
        Skills.DiscoveryResult result = Skills.discoverDefault(workspace);

        String prompt = Skills.buildAvailableSkillsPrompt(result.getSkills());

        System.out.println(prompt);
        Assert.assertTrue("目录应含 skill 名", prompt.contains("code-review"));
        Assert.assertTrue("应提示读取 SKILL.md", prompt.contains("SKILL.md"));
        // 正文里的步骤细节不应出现在目录里
        Assert.assertFalse("正文不应泄漏进目录", prompt.contains("1. Read the diff"));
    }

    // ---- §6.4 createToolContext 产出只读根 ----

    @Test
    public void createToolContextExposesAllowedReadRoots() throws Exception {
        Path workspace = createWorkspaceWithSkill();

        io.github.lnyocly.ai4j.tool.BuiltInToolContext ctx =
                Skills.createToolContext(workspace);

        Assert.assertNotNull(ctx.getAllowedReadRoots());
        Assert.assertFalse("只读根应包含 skill 目录", ctx.getAllowedReadRoots().isEmpty());
    }

    // ---- appendAvailableSkillsPrompt 把目录拼到既有 prompt 上 ----

    @Test
    public void appendAvailableSkillsPromptMergesIntoBasePrompt() throws Exception {
        Path workspace = createWorkspaceWithSkill();
        Skills.DiscoveryResult result = Skills.discoverDefault(workspace);

        String merged = Skills.appendAvailableSkillsPrompt(
                "You are a coding agent.", result.getSkills());

        Assert.assertTrue("应保留原 prompt", merged.contains("You are a coding agent."));
        Assert.assertTrue("应追加 skill 目录", merged.contains("code-review"));
    }

    // ---- helpers ----

    private Path createWorkspaceWithSkill() throws Exception {
        Path workspace = Files.createTempDirectory("ai4j-skill-test-");
        Path skillDir = workspace.resolve(".ai4j/skills/code-review");
        Files.createDirectories(skillDir);
        Files.write(skillDir.resolve("SKILL.md"),
                ("---\n"
                        + "name: code-review\n"
                        + "description: How to do a thorough code review\n"
                        + "---\n"
                        + "# Code Review\n\n"
                        + "Steps:\n"
                        + "1. Read the diff carefully.\n"
                        + "2. Check naming and structure.\n"
                        + "3. Verify tests cover the change.\n"
                ).getBytes(StandardCharsets.UTF_8));
        return workspace;
    }
}
