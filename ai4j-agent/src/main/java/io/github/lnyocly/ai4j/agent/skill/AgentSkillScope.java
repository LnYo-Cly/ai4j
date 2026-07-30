package io.github.lnyocly.ai4j.agent.skill;

import io.github.lnyocly.ai4j.skill.SkillDescriptor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Host-owned Skill discovery configuration for one Agent run.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentSkillScope {

    private Path workspaceRoot;

    @Builder.Default
    private List<String> skillDirectories = new ArrayList<String>();

    /**
     * Includes the standard workspace and process-user Skill roots. Custom host resolvers default
     * to explicit roots so a tenant policy cannot accidentally expose server-global Skills.
     */
    @Builder.Default
    private boolean includeDefaultSkillRoots = false;

    /**
     * Empty means every discovered Skill is enabled. Manual-only still requires explicit selection.
     */
    @Builder.Default
    private List<String> enabledSkillNames = new ArrayList<String>();

    /**
     * Host-provided Skills added to the discovered file catalog for this run. A descriptor with
     * {@link SkillDescriptor#getContent()} uses {@code skillFilePath} as a stable virtual location.
     */
    @Builder.Default
    private List<SkillDescriptor> providedSkills = new ArrayList<SkillDescriptor>();

    /**
     * Source-compatible constructor retained for callers compiled before host-provided Skills.
     */
    public AgentSkillScope(Path workspaceRoot, List<String> skillDirectories,
                           boolean includeDefaultSkillRoots, List<String> enabledSkillNames) {
        this(workspaceRoot, skillDirectories, includeDefaultSkillRoots, enabledSkillNames,
                new ArrayList<SkillDescriptor>());
    }
}
