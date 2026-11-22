package io.github.lnyocly.ai4j.flowgram.springboot;

import io.github.lnyocly.ai4j.flowgram.springboot.autoconfigure.FlowGramAutoConfiguration;
import io.github.lnyocly.ai4j.flowgram.springboot.config.FlowGramProperties;
import io.github.lnyocly.ai4j.flowgram.springboot.security.DefaultFlowGramCallerResolver;
import io.github.lnyocly.ai4j.flowgram.springboot.security.FlowGramCaller;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramJsonSupport;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.web.context.request.WebRequest;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BootDualStackCompatibilityTest {

    private static final List<String> FORBIDDEN_CONSTANT_POOL_FRAGMENTS = Arrays.asList(
            "javax/annotation/",
            "javax/servlet/",
            "com/alibaba/fastjson2/"
    );

    @Test
    public void classesShouldNotReferenceForbiddenBoot2OrFastjson2Packages() throws Exception {
        Path classesDir = resolveClassesDirectory(FlowGramAutoConfiguration.class);
        Assert.assertTrue("Expected classes directory at " + classesDir, Files.isDirectory(classesDir));

        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(classesDir)) {
            classFiles = walk.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class"))
                    .collect(Collectors.toList());
        }
        Assert.assertFalse("Expected compiled class files in " + classesDir, classFiles.isEmpty());

        List<String> violations = new ArrayList<String>();
        for (Path classFile : classFiles) {
            List<String> utf8Entries = readConstantPoolUtf8Entries(classFile);
            for (String entry : utf8Entries) {
                for (String forbidden : FORBIDDEN_CONSTANT_POOL_FRAGMENTS) {
                    if (entry.contains(forbidden)) {
                        violations.add(classesDir.relativize(classFile) + " -> " + entry);
                    }
                }
            }
        }
        Assert.assertTrue("Forbidden bytecode references found: " + violations, violations.isEmpty());
    }

    @Test
    public void springFactoriesAndAutoConfigurationImportsShouldMatch() throws Exception {
        Path classesDir = resolveClassesDirectory(FlowGramAutoConfiguration.class);
        Path factoriesPath = classesDir.resolve("META-INF").resolve("spring.factories");
        Path importsPath = classesDir.resolve("META-INF")
                .resolve("spring")
                .resolve("org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        Assert.assertTrue("Missing spring.factories at " + factoriesPath, Files.isRegularFile(factoriesPath));
        Assert.assertTrue("Missing AutoConfiguration.imports at " + importsPath, Files.isRegularFile(importsPath));

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(factoriesPath)) {
            properties.load(in);
        }
        String rawFactories = properties.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
        Assert.assertNotNull("Missing EnableAutoConfiguration entry in spring.factories", rawFactories);

        List<String> fromFactories = new ArrayList<String>();
        for (String token : rawFactories.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                fromFactories.add(trimmed);
            }
        }

        List<String> fromImports = new ArrayList<String>();
        for (String line : Files.readAllLines(importsPath, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                fromImports.add(trimmed);
            }
        }

        Assert.assertFalse("AutoConfiguration lists must not be empty", fromFactories.isEmpty());
        Assert.assertEquals(fromFactories, fromImports);
    }

    @Test
    public void callerResolverShouldReadHeadersThroughContainerNeutralWebRequest() {
        FlowGramProperties properties = new FlowGramProperties();
        properties.getAuth().setEnabled(true);
        properties.getAuth().setHeaderName("X-Caller-Id");

        final Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Caller-Id", "alice");
        headers.put("X-Tenant-Id", "acme");

        WebRequest webRequest = (WebRequest) Proxy.newProxyInstance(
                WebRequest.class.getClassLoader(),
                new Class<?>[]{WebRequest.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("getHeader".equals(method.getName()) && args != null && args.length == 1) {
                            return headers.get(String.valueOf(args[0]));
                        }
                        return null;
                    }
                }
        );

        DefaultFlowGramCallerResolver resolver = new DefaultFlowGramCallerResolver(properties);
        FlowGramCaller caller = resolver.resolve(webRequest);

        Assert.assertFalse(caller.isAnonymous());
        Assert.assertEquals("alice", caller.getCallerId());
        Assert.assertEquals("acme", caller.getTenantId());
    }

    @Test
    public void jacksonSupportShouldPreserveLinkedHashMapOrder() {
        Map<String, Object> ordered = new LinkedHashMap<String, Object>();
        ordered.put("zebra", 1);
        ordered.put("alpha", 2);
        ordered.put("middle", 3);

        String json = FlowGramJsonSupport.toJson(ordered);
        Assert.assertEquals("{\"zebra\":1,\"alpha\":2,\"middle\":3}", json);

        LinkedHashMap<String, Object> parsed = FlowGramJsonSupport.readMapOrNull(json);
        Assert.assertNotNull(parsed);
        Assert.assertEquals(Arrays.asList("zebra", "alpha", "middle"), new ArrayList<String>(parsed.keySet()));
    }

    private static Path resolveClassesDirectory(Class<?> anchor) throws URISyntaxException {
        URL location = anchor.getProtectionDomain().getCodeSource().getLocation();
        if (location != null) {
            Path path = Paths.get(location.toURI());
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Paths.get("target", "classes");
    }

    private static List<String> readConstantPoolUtf8Entries(Path classFile) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(classFile)))) {
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                return Collections.emptyList();
            }
            in.readUnsignedShort(); // minor_version
            in.readUnsignedShort(); // major_version
            int constantPoolCount = in.readUnsignedShort();
            List<String> entries = new ArrayList<String>();
            for (int i = 1; i < constantPoolCount; i++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1: // CONSTANT_Utf8
                        entries.add(in.readUTF());
                        break;
                    case 3: // CONSTANT_Integer
                    case 4: // CONSTANT_Float
                    case 9: // CONSTANT_Fieldref
                    case 10: // CONSTANT_Methodref
                    case 11: // CONSTANT_InterfaceMethodref
                    case 12: // CONSTANT_NameAndType
                    case 17: // CONSTANT_Dynamic
                    case 18: // CONSTANT_InvokeDynamic
                        skipFully(in, 4);
                        break;
                    case 5: // CONSTANT_Long
                    case 6: // CONSTANT_Double
                        skipFully(in, 8);
                        i++; // occupies two constant pool slots
                        break;
                    case 7: // CONSTANT_Class
                    case 8: // CONSTANT_String
                    case 16: // CONSTANT_MethodType
                    case 19: // CONSTANT_Module
                    case 20: // CONSTANT_Package
                        skipFully(in, 2);
                        break;
                    case 15: // CONSTANT_MethodHandle
                        skipFully(in, 3);
                        break;
                    default:
                        throw new IOException("Unsupported constant pool tag " + tag + " in " + classFile);
                }
            }
            return entries;
        }
    }

    private static void skipFully(DataInputStream in, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int skipped = in.skipBytes(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("Unexpected EOF while skipping constant pool bytes");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }
}
