package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.service.factory.AiService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.InitializingBean;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        Path classesDir = resolveClassesDirectory(AiConfigAutoConfiguration.class);
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
        Path classesDir = resolveClassesDirectory(AiConfigAutoConfiguration.class);
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
    public void aiConfigAutoConfigurationShouldInitializeViaInitializingBean() throws Exception {
        Assert.assertTrue(InitializingBean.class.isAssignableFrom(AiConfigAutoConfiguration.class));

        OpenAiConfigProperties openAi = new OpenAiConfigProperties();
        openAi.setApiKey("dual-stack-key");
        openAi.setApiHost("https://dual-stack.example/");

        AiConfigAutoConfiguration autoConfiguration = new AiConfigAutoConfiguration(
                new OkHttpConfigProperties(),
                openAi,
                new PineconeConfigProperties(),
                new QdrantConfigProperties(),
                new MilvusConfigProperties(),
                new PgVectorConfigProperties(),
                new RedisVectorConfigProperties(),
                new ElasticsearchConfigProperties(),
                new ChromaConfigProperties(),
                new VectorStoreProperties(),
                new SearXNGConfigProperties(),
                new AiConfigProperties(),
                new ZhipuConfigProperties(),
                new AnthropicConfigProperties(),
                new DeepSeekConfigProperties(),
                new MoonshotConfigProperties(),
                new HunyuanConfigProperties(),
                new LingyiConfigProperties(),
                new OllamaConfigProperties(),
                new MinimaxConfigProperties(),
                new BaichuanConfigProperties(),
                new DashScopeConfigProperties(),
                new DoubaoConfigProperties(),
                new JinaConfigProperties(),
                new SunoConfigProperties(),
                new TypeSafeConfigProperties(),
                new AgentFlowProperties(),
                new MinerUConfigProperties()
        );

        ((InitializingBean) autoConfiguration).afterPropertiesSet();

        AiService aiService = autoConfiguration.aiService();
        Assert.assertNotNull(aiService.getConfiguration().getOkHttpClient());
        Assert.assertEquals("dual-stack-key", aiService.getConfiguration().getOpenAiConfig().getApiKey());
        Assert.assertEquals("https://dual-stack.example/", aiService.getConfiguration().getOpenAiConfig().getApiHost());
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
