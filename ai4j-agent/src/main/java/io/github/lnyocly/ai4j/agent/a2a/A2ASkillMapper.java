package io.github.lnyocly.ai4j.agent.a2a;

import io.github.lnyocly.ai4j.skill.SkillDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility for mapping ai4j {@link SkillDescriptor} to A2A {@link A2ASkill}.
 * Enables integration between ai4j's native Skills system and A2A protocol.
 */
public final class A2ASkillMapper {

    private A2ASkillMapper() {
    }

    /**
     * Maps a single ai4j SkillDescriptor to an A2ASkill.
     *
     * @param descriptor the ai4j skill descriptor
     * @return the corresponding A2A skill, or null if descriptor is null
     */
    public static A2ASkill mapToA2ASkill(SkillDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }

        String name = descriptor.getName();
        String description = descriptor.getDescription();

        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        A2ASkill skill = new A2ASkill();
        skill.setName(name.trim());
        skill.setDescription(description == null ? "" : description.trim());

        // Note: inputSchema could be extracted from a SKILL.md front matter field
        // if defined in the future. For now, leave it null (optional in A2A).

        return skill;
    }

    /**
     * Maps multiple ai4j SkillDescriptors to A2ASkills.
     *
     * @param descriptors the ai4j skill descriptors
     * @return list of corresponding A2A skills (excludes nulls)
     */
    public static List<A2ASkill> mapToA2ASkills(List<SkillDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return new ArrayList<A2ASkill>();
        }

        List<A2ASkill> skills = new ArrayList<A2ASkill>();
        for (SkillDescriptor descriptor : descriptors) {
            A2ASkill skill = mapToA2ASkill(descriptor);
            if (skill != null) {
                skills.add(skill);
            }
        }
        return skills;
    }

    /**
     * Adds ai4j skills to an A2AServer by mapping SkillDescriptors to A2ASkills.
     *
     * @param server the A2A server to configure
     * @param descriptors the ai4j skill descriptors
     */
    public static void addSkillsToServer(A2AServer server, List<SkillDescriptor> descriptors) {
        if (server == null || descriptors == null || descriptors.isEmpty()) {
            return;
        }

        for (SkillDescriptor descriptor : descriptors) {
            A2ASkill skill = mapToA2ASkill(descriptor);
            if (skill != null) {
                server.withSkill(skill.getName(), skill.getDescription(), skill.getInputSchema());
            }
        }
    }
}