package io.github.lnyocly.ai4j.plugin.colab;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ColabJacksonMigrationTest {

    @Test
    public void compiledClassesContainZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get("target", "classes").toAbsolutePath().normalize();
        if (!Files.isDirectory(classesDir)) {
            classesDir = Paths.get("ai4j-plugin-colab", "target", "classes").toAbsolutePath().normalize();
        }
        Assert.assertTrue("Expected compiled classes directory at " + classesDir, Files.isDirectory(classesDir));

        List<String> violations = new ArrayList<String>();
        try (Stream<Path> stream = Files.walk(classesDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                    .forEach(path -> {
                        try {
                            String bytes = new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1);
                            if (bytes.contains("com/alibaba/fastjson2/")) {
                                violations.add(path.toString());
                            }
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        Assert.assertTrue("Found fastjson2 references in compiled classes: " + violations, violations.isEmpty());
    }
}
