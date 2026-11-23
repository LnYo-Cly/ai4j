package io.github.lnyocly.ai4j.ambient.evolve;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.skill.SkillDescriptor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * L2 RSI artifact representing a patched or refined {@code SKILL.md} instruction
 * candidate derived from human steer corrections or repeated error trajectories.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SkillPatchCandidate implements RsiCandidate {

    private String candidateId;
    private String scopeKey;
    private String diagnosisId;
    private String skillName;
    @Builder.Default
    private String baseVersion = "v1";
    @Builder.Default
    private String targetVersion = "v2";
    @Builder.Default
    private String metaVersion = "meta-v1";
    private String description;
    private String markdownContent;
    private String patchSummary;
    @Builder.Default
    private List<String> requiredPrechecks = new ArrayList<String>();
    @Builder.Default
    private CandidateStatus status = CandidateStatus.PROPOSED;

    @Override
    public CandidateTier getTier() {
        return CandidateTier.L2_SKILL_PATCH;
    }

    @Override
    public String getDigest() {
        return AmbientSupport.sha256Hex("L2|" + AmbientSupport.emptyToEmpty(skillName)
                + "|" + AmbientSupport.emptyToEmpty(targetVersion)
                + "|" + AmbientSupport.normalizeMarkdown(markdownContent));
    }

    public ExtensionSkillResource toExtensionSkillResource(String resourcePath) {
        return ExtensionSkillResource.builder()
                .name(skillName)
                .description(description)
                .resourcePath(resourcePath == null ? "skills/" + skillName + "/SKILL.md" : resourcePath)
                .build();
    }

    public SkillDescriptor toSkillDescriptor(String virtualPath) {
        return SkillDescriptor.builder()
                .name(skillName)
                .description(description)
                .skillFilePath(virtualPath == null ? ".ai4j/skills/" + skillName + "/SKILL.md" : virtualPath)
                .content(markdownContent)
                .build();
    }
}
