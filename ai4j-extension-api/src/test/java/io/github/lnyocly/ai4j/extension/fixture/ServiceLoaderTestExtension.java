package io.github.lnyocly.ai4j.extension.fixture;

import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionContext;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;

public class ServiceLoaderTestExtension implements Ai4jExtension {

    public ExtensionManifest manifest() {
        return ExtensionManifest.builder()
                .id("service-loader-pack")
                .capability(ExtensionCapability.SKILL)
                .build();
    }

    public void apply(ExtensionContext context) {
    }
}
