package io.github.lnyocly.ai4j.coding.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodingSkillDescriptor {

    private String name;
    private String description;
    private String skillFilePath;
    private String source;
    private boolean disableModelInvocation;

    public CodingSkillDescriptor(String name, String description, String skillFilePath, String source) {
        this(name, description, skillFilePath, source, false);
    }
}
