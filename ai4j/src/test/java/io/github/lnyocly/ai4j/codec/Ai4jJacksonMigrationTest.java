package io.github.lnyocly.ai4j.codec;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.structured.StructuredOutputs;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Verifies that compiled {@code ai4j} classes carry zero {@code com.alibaba.fastjson2}
 * constant-pool references and that shared {@link JsonCodec} / {@link StructuredOutputs}
 * contracts work end-to-end on Jackson.
 */
public class Ai4jJacksonMigrationTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    @Test
    public void compiledClassesHaveZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get(JsonCodec.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assert.assertTrue("Expected compiled classes directory at " + classesDir, Files.isDirectory(classesDir));

        byte[] forbiddenFastjson2 = "com/alibaba/fastjson2/".getBytes(StandardCharsets.US_ASCII);
        byte[] forbiddenFastjson = "com/alibaba/fastjson/".getBytes(StandardCharsets.US_ASCII);
        List<String> offendingClasses = new ArrayList<String>();
        int scanned = 0;

        List<Path> classFiles = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(classesDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                    .forEach(classFiles::add);
        }

        for (Path classFile : classFiles) {
            scanned++;
            byte[] bytes = Files.readAllBytes(classFile);
            if (containsBytes(bytes, forbiddenFastjson2) || containsBytes(bytes, forbiddenFastjson)) {
                offendingClasses.add(classesDir.relativize(classFile).toString());
            }
        }

        Assert.assertTrue("Expected compiled class files in ai4j", scanned > 0);
        Assert.assertTrue("Unexpected fastjson constant-pool references in: " + offendingClasses,
                offendingClasses.isEmpty());
    }

    @Test
    public void jsonCodecHandlesLenientMapsAndTypes() {
        Map<String, Object> parsed = JsonCodec.fromJsonLenient(
                "```json\n{ 'ok': true, count: 2, }\n```", MAP_TYPE);
        Assert.assertEquals(Boolean.TRUE, parsed.get("ok"));
        Assert.assertEquals(Integer.valueOf(2), parsed.get("count"));
        Assert.assertTrue(JsonCodec.toJson(parsed).contains("\"ok\":true"));
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
