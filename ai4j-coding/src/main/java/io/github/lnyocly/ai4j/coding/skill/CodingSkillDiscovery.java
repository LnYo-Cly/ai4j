package io.github.lnyocly.ai4j.coding.skill;

import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;
import io.github.lnyocly.ai4j.skill.Skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CodingSkillDiscovery {

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
        Skills.DiscoveryResult result = Skills.discoverDefault(source.getRoot(), source.getSkillDirectories());
        return new DiscoveryResult(
                toCodingDescriptors(result.getSkills()),
                result.getAllowedReadRoots()
        );
    }

    private static List<CodingSkillDescriptor> toCodingDescriptors(List<SkillDescriptor> skills) {
        if (skills == null || skills.isEmpty()) {
            return Collections.emptyList();
        }
        List<CodingSkillDescriptor> descriptors = new ArrayList<CodingSkillDescriptor>();
        for (SkillDescriptor skill : skills) {
            if (skill == null) {
                continue;
            }
            descriptors.add(CodingSkillDescriptor.builder()
                    .name(skill.getName())
                    .description(skill.getDescription())
                    .skillFilePath(skill.getSkillFilePath())
                    .source(skill.getSource())
                    .build());
        }
        return descriptors;
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
