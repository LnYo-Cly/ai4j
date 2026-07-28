package io.github.lnyocly.ai4j.extension.skill;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

@Experimental(since = "2.4.3")
public interface SkillRegistry {

    void register(ExtensionSkillResource resource);
}
