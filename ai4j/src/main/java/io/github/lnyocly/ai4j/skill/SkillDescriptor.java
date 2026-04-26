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
}
