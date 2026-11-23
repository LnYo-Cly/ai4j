package io.github.lnyocly.ai4j.plugin.redis;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RedisJacksonMigrationTest {

    @Test
    public void compiledClassesContainZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get("target", "classes").toAbsolutePath().normalize();
        if (!Files.isDirectory(classesDir)) {
            classesDir = Paths.get("ai4j-plugin-redis", "target", "classes").toAbsolutePath().normalize();
        }
        Assert.assertTrue("Expected compiled classes directory at " + classesDir, Files.isDirectory(classesDir));

        byte[] needle = ("com/alibaba/" + "fastjson2/").getBytes(StandardCharsets.US_ASCII);
        List<String> violations = new ArrayList<String>();
        try (Stream<Path> stream = Files.walk(classesDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
                    .forEach(path -> {
                        try {
                            byte[] bytes = Files.readAllBytes(path);
                            if (indexOf(bytes, needle) >= 0) {
                                violations.add(path.toString());
                            }
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        Assert.assertTrue("Found fastjson2 references in compiled classes: " + violations, violations.isEmpty());
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
