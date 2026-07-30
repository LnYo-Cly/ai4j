package io.github.lnyocly.ai4j.agent.skill;

import io.github.lnyocly.ai4j.agent.AgentRequest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves host-owned Skill discovery scope once at the start of each Agent run.
 */
public interface AgentSkillResolver {

    AgentSkillScope resolve(AgentRequest request);

    static AgentSkillResolver forWorkspace(final Path workspaceRoot, List<String> skillDirectories) {
        final List<String> copiedDirectories = skillDirectories == null
                ? new ArrayList<String>() : new ArrayList<String>(skillDirectories);
        copiedDirectories.add(0, ".agents/skills");
        copiedDirectories.add(0, ".ai4j/skills");
        return new AgentSkillResolver() {
            @Override
            public AgentSkillScope resolve(AgentRequest request) {
                return AgentSkillScope.builder()
                        .workspaceRoot(workspaceRoot)
                        .skillDirectories(new ArrayList<String>(copiedDirectories))
                        .includeDefaultSkillRoots(false)
                        .build();
            }
        };
    }

    /**
     * Opts into process-user Skill roots for local developer tools. Server applications should use
     * {@link #forWorkspace(Path, List)} or a host-owned resolver instead.
     */
    static AgentSkillResolver forWorkspaceIncludingUserHome(final Path workspaceRoot,
                                                             List<String> skillDirectories) {
        final List<String> copiedDirectories = skillDirectories == null
                ? new ArrayList<String>() : new ArrayList<String>(skillDirectories);
        return new AgentSkillResolver() {
            @Override
            public AgentSkillScope resolve(AgentRequest request) {
                return AgentSkillScope.builder()
                        .workspaceRoot(workspaceRoot)
                        .skillDirectories(new ArrayList<String>(copiedDirectories))
                        .includeDefaultSkillRoots(true)
                        .build();
            }
        };
    }
}
