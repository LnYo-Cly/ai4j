package io.github.lnyocly.ai4j.rag;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Verifies {@code ai4j-rag} compiled bytecode contains zero {@code fastjson2} references.
 */
public class RagJacksonMigrationTest {

    @Test
    public void compiledClassesHaveZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get(RagExtension.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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

        Assert.assertTrue("Expected compiled class files in ai4j-rag", scanned > 0);
        Assert.assertTrue("Unexpected fastjson constant-pool references in: " + offendingClasses,
                offendingClasses.isEmpty());
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
