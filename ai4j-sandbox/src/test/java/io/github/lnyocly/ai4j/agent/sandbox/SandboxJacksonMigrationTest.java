package io.github.lnyocly.ai4j.agent.sandbox;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies compiled {@code ai4j-sandbox} classes contain zero fastjson2 constant-pool references.
 */
public class SandboxJacksonMigrationTest {

    @Test
    public void compiledClassesHaveNoFastjson2References() throws Exception {
        File classesDir = new File(SandboxExtension.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Assert.assertTrue("classes output directory must exist: " + classesDir, classesDir.isDirectory());

        byte[] needle = ("com/alibaba/" + "fastjson2/").getBytes(StandardCharsets.US_ASCII);
        List<File> classFiles = new ArrayList<File>();
        collectClassFiles(classesDir, classFiles);
        Assert.assertFalse("expected compiled sandbox class files", classFiles.isEmpty());

        List<String> offenders = new ArrayList<String>();
        for (File classFile : classFiles) {
            byte[] bytes = readAll(classFile);
            if (indexOf(bytes, needle) >= 0) {
                offenders.add(classesDir.toPath().relativize(classFile.toPath()).toString());
            }
        }
        Assert.assertTrue("unexpected fastjson2 references in compiled classes: " + offenders, offenders.isEmpty());
    }

    private static void collectClassFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectClassFiles(child, out);
            } else if (child.getName().endsWith(".class")) {
                out.add(child);
            }
        }
    }

    private static byte[] readAll(File file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (InputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return out.toByteArray();
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
