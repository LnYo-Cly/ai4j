package io.github.lnyocly.ai4j.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SkillDescriptor {

    private String name;
    private String description;
    private String skillFilePath;
    private String source;
    private boolean disableModelInvocation;

    /**
     * Optional host-provided SKILL.md body. When present, {@code skillFilePath} is a stable
     * virtual location resolved by the host's scoped Skill reader rather than the local filesystem.
     */
    private String content;

    /**
     * Source-compatible constructor retained for callers compiled before in-memory Skills existed.
     */
    public SkillDescriptor(String name, String description, String skillFilePath, String source,
                           boolean disableModelInvocation) {
        this(name, description, skillFilePath, source, disableModelInvocation, null);
    }

    public SkillDescriptor(String name, String description, String skillFilePath, String source) {
        this(name, description, skillFilePath, source, false);
    }
}
