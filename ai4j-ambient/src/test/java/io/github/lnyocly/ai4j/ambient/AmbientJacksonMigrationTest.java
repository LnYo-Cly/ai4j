package io.github.lnyocly.ai4j.ambient;

import io.github.lnyocly.ai4j.ambient.channel.AmbientExtension;
import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AmbientJacksonMigrationTest {

    @Test
    public void compiledClassesHaveZeroFastjson2References() throws Exception {
        Path classesDir = Paths.get(AmbientExtension.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue("Expected compiled classes directory at " + classesDir, Files.isDirectory(classesDir));

        byte[] forbiddenPrefix = "com/alibaba/fastjson2/".getBytes(StandardCharsets.US_ASCII);
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
            if (containsBytes(bytes, forbiddenPrefix)) {
                offendingClasses.add(classesDir.relativize(classFile).toString());
            }
        }

        assertTrue("Expected compiled class files in ai4j-ambient", scanned > 0);
        assertTrue("Unexpected fastjson2 constant pool references in: " + offendingClasses, offendingClasses.isEmpty());
    }

    @Test
    public void ambientSupportHelpersNormalizeAndHashDeterministically() {
        assertNull(AmbientSupport.trimToNull("   "));
        assertEquals("abc", AmbientSupport.trimToNull("  abc  "));
        assertEquals("a\nb", AmbientSupport.normalizeMarkdown("  a\r\nb \n "));
        assertEquals(AmbientSupport.sha256Hex("hello"), AmbientSupport.sha256Hex("hello"));
        assertEquals(64, AmbientSupport.sha256Hex("hello").length());
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
