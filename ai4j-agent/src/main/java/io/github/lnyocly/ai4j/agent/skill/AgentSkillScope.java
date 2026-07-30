package io.github.lnyocly.ai4j.agent.skill;

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
}
