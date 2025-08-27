package io.github.lnyocly.ai4j.service;

import io.github.lnyocly.ai4j.config.AiPlatform;
import lombok.Data;

import java.util.List;

@Data
public class AiConfig {

    private List<AiPlatform> platforms;
}
