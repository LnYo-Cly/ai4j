package io.github.lnyocly.ai4j.agent.blueprint;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Accessor for bundled Agent Blueprint schema artifacts.
 */
public final class AgentBlueprintSchemas {

    public static final String V1_SCHEMA_RESOURCE = "ai4j/agent-blueprint.schema.json";

    private AgentBlueprintSchemas() {
    }

    public static InputStream openV1JsonSchemaStream() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = contextClassLoader == null ? null : contextClassLoader.getResourceAsStream(V1_SCHEMA_RESOURCE);
        if (inputStream == null) {
            inputStream = AgentBlueprintSchemas.class.getClassLoader().getResourceAsStream(V1_SCHEMA_RESOURCE);
        }
        if (inputStream == null) {
            throw new IllegalStateException("Agent Blueprint JSON Schema resource not found: " + V1_SCHEMA_RESOURCE);
        }
        return inputStream;
    }

    public static String v1JsonSchema() {
        InputStream inputStream = openV1JsonSchemaStream();
        try {
            return readUtf8(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read Agent Blueprint JSON Schema resource.", ex);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static void writeV1JsonSchema(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("schema output path is required");
        }
        try {
            Path parent = path.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, v1JsonSchema().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write Agent Blueprint JSON Schema to " + path + ".", ex);
        }
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
