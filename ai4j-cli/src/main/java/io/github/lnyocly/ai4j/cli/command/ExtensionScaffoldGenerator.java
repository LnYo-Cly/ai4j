package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.extension.ExtensionManifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class ExtensionScaffoldGenerator {

    private ExtensionScaffoldGenerator() {
    }

    static Result generate(Options options) throws IOException {
        Options safeOptions = Options.copyOf(options);
        requireEmptyOrMissingDirectory(safeOptions.targetDirectory);

        List<Path> createdFiles = new ArrayList<Path>();
        Path mainJava = safeOptions.targetDirectory
                .resolve("src/main/java")
                .resolve(packagePath(safeOptions.packageName))
                .resolve(safeOptions.className + ".java");
        Path testJava = safeOptions.targetDirectory
                .resolve("src/test/java")
                .resolve(packagePath(safeOptions.packageName))
                .resolve(safeOptions.className + "Test.java");
        Path serviceFile = safeOptions.targetDirectory
                .resolve("src/main/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension");
        Path skillFile = safeOptions.targetDirectory
                .resolve("src/main/resources/skills")
                .resolve(safeOptions.resourceSegment)
                .resolve("SKILL.md");
        Path promptFile = safeOptions.targetDirectory
                .resolve("src/main/resources/prompts")
                .resolve(safeOptions.resourceSegment + "-summary.md");

        writeFile(safeOptions.targetDirectory.resolve("pom.xml"), renderPom(safeOptions), createdFiles);
        writeFile(safeOptions.targetDirectory.resolve("README.md"), renderReadme(safeOptions), createdFiles);
        writeFile(mainJava, renderExtensionClass(safeOptions), createdFiles);
        writeFile(serviceFile, safeOptions.packageName + "." + safeOptions.className + "\n", createdFiles);
        writeFile(skillFile, renderSkill(safeOptions), createdFiles);
        writeFile(promptFile, renderPrompt(safeOptions), createdFiles);
        writeFile(testJava, renderTest(safeOptions), createdFiles);

        return new Result(safeOptions.targetDirectory, createdFiles);
    }

    private static void requireEmptyOrMissingDirectory(Path targetDirectory) throws IOException {
        if (!Files.exists(targetDirectory)) {
            return;
        }
        if (!Files.isDirectory(targetDirectory)) {
            throw new IllegalArgumentException("target path exists but is not a directory: " + targetDirectory);
        }
        DirectoryStream<Path> stream = Files.newDirectoryStream(targetDirectory);
        try {
            if (stream.iterator().hasNext()) {
                throw new IllegalArgumentException("target directory must be empty: " + targetDirectory);
            }
        } finally {
            stream.close();
        }
    }

    private static void writeFile(Path file, String content, List<Path> createdFiles) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        createdFiles.add(file);
    }

    private static String renderPom(Options options) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "\n"
                + "  <groupId>" + xml(options.groupId) + "</groupId>\n"
                + "  <artifactId>" + xml(options.artifactId) + "</artifactId>\n"
                + "  <version>" + xml(options.version) + "</version>\n"
                + "  <packaging>jar</packaging>\n"
                + "\n"
                + "  <name>" + xml(options.displayName) + "</name>\n"
                + "  <description>AI4J extension plugin package.</description>\n"
                + "\n"
                + "  <properties>\n"
                + "    <maven.compiler.source>1.8</maven.compiler.source>\n"
                + "    <maven.compiler.target>1.8</maven.compiler.target>\n"
                + "    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n"
                + "  </properties>\n"
                + "\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>io.github.lnyo-cly</groupId>\n"
                + "      <artifactId>ai4j-extension-api</artifactId>\n"
                + "      <version>" + xml(options.extensionApiVersion) + "</version>\n"
                + "    </dependency>\n"
                + "    <dependency>\n"
                + "      <groupId>junit</groupId>\n"
                + "      <artifactId>junit</artifactId>\n"
                + "      <version>4.13.2</version>\n"
                + "      <scope>test</scope>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "\n"
                + "  <build>\n"
                + "    <plugins>\n"
                + "      <plugin>\n"
                + "        <groupId>org.apache.maven.plugins</groupId>\n"
                + "        <artifactId>maven-compiler-plugin</artifactId>\n"
                + "        <version>3.11.0</version>\n"
                + "        <configuration>\n"
                + "          <source>${maven.compiler.source}</source>\n"
                + "          <target>${maven.compiler.target}</target>\n"
                + "          <encoding>${project.build.sourceEncoding}</encoding>\n"
                + "        </configuration>\n"
                + "      </plugin>\n"
                + "    </plugins>\n"
                + "  </build>\n"
                + "</project>\n";
    }

    private static String renderExtensionClass(Options options) {
        return "package " + options.packageName + ";\n"
                + "\n"
                + "import io.github.lnyocly.ai4j.extension.Ai4jExtension;\n"
                + "import io.github.lnyocly.ai4j.extension.ExtensionCapability;\n"
                + "import io.github.lnyocly.ai4j.extension.ExtensionContext;\n"
                + "import io.github.lnyocly.ai4j.extension.ExtensionManifest;\n"
                + "import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;\n"
                + "import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;\n"
                + "import io.github.lnyocly.ai4j.extension.guardrail.GuardrailDecision;\n"
                + "import io.github.lnyocly.ai4j.extension.guardrail.GuardrailRequest;\n"
                + "import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;\n"
                + "import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;\n"
                + "import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;\n"
                + "import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;\n"
                + "import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;\n"
                + "\n"
                + "public final class " + options.className + " implements Ai4jExtension {\n"
                + "\n"
                + "    public ExtensionManifest manifest() {\n"
                + "        return ExtensionManifest.builder()\n"
                + "                .id(\"" + java(options.extensionId) + "\")\n"
                + "                .name(\"" + java(options.displayName) + "\")\n"
                + "                .version(\"" + java(options.version) + "\")\n"
                + "                .vendor(\"" + java(options.vendor) + "\")\n"
                + "                .capability(ExtensionCapability.TOOL)\n"
                + "                .capability(ExtensionCapability.COMMAND)\n"
                + "                .capability(ExtensionCapability.SKILL)\n"
                + "                .capability(ExtensionCapability.PROMPT)\n"
                + "                .capability(ExtensionCapability.GUARDRAIL)\n"
                + "                .configPrefix(\"ai4j.extensions." + java(options.namespace) + "\")\n"
                + "                .build();\n"
                + "    }\n"
                + "\n"
                + "    public void apply(ExtensionContext context) {\n"
                + "        context.tools().register(ExtensionToolSpec.builder()\n"
                + "                        .name(\"" + java(options.toolName) + "\")\n"
                + "                        .description(\"Echo input text for extension smoke tests\")\n"
                + "                        .inputSchema(\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"text\\\":{\\\"type\\\":\\\"string\\\",\\\"description\\\":\\\"Text to echo\\\"}},\\\"required\\\":[\\\"text\\\"]}\")\n"
                + "                        .build(),\n"
                + "                new ExtensionToolExecutor() {\n"
                + "                    public String execute(ExtensionToolCall call) {\n"
                + "                        return call == null ? \"\" : call.getArguments();\n"
                + "                    }\n"
                + "                });\n"
                + "\n"
                + "        context.commands().register(ExtensionCommandSpec.builder()\n"
                + "                        .name(\"" + java(options.commandName) + "\")\n"
                + "                        .description(\"Echo command arguments\")\n"
                + "                        .usage(\"/" + java(options.commandName) + " <text>\")\n"
                + "                        .build(),\n"
                + "                request -> request == null ? \"\" : request.getArguments());\n"
                + "\n"
                + "        context.skills().register(ExtensionSkillResource.builder()\n"
                + "                .name(\"" + java(options.skillName) + "\")\n"
                + "                .description(\"Example workflow skill\")\n"
                + "                .resourcePath(\"skills/" + java(options.resourceSegment) + "/SKILL.md\")\n"
                + "                .build());\n"
                + "\n"
                + "        context.prompts().register(ExtensionPromptResource.builder()\n"
                + "                .name(\"" + java(options.promptName) + "\")\n"
                + "                .description(\"Example prompt resource\")\n"
                + "                .resourcePath(\"prompts/" + java(options.resourceSegment) + "-summary.md\")\n"
                + "                .build());\n"
                + "\n"
                + "        context.guardrails().register(new ExtensionGuardrail() {\n"
                + "            public String name() {\n"
                + "                return \"" + java(options.guardrailName) + "\";\n"
                + "            }\n"
                + "\n"
                + "            public GuardrailDecision evaluate(GuardrailRequest request) {\n"
                + "                return GuardrailDecision.allow();\n"
                + "            }\n"
                + "        });\n"
                + "    }\n"
                + "}\n";
    }

    private static String renderTest(Options options) {
        return "package " + options.packageName + ";\n"
                + "\n"
                + "import io.github.lnyocly.ai4j.extension.ExtensionRegistry;\n"
                + "import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;\n"
                + "import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;\n"
                + "import org.junit.Assert;\n"
                + "import org.junit.Test;\n"
                + "\n"
                + "public class " + options.className + "Test {\n"
                + "\n"
                + "    @Test\n"
                + "    public void extension_contract_is_valid() {\n"
                + "        ExtensionRegistry registry = ExtensionRegistry.of(new " + options.className + "());\n"
                + "        ExtensionValidationReport report = ExtensionValidator.validate(registry, \"" + java(options.extensionId) + "\");\n"
                + "\n"
                + "        Assert.assertTrue(report.getIssues().toString(), report.isValid());\n"
                + "    }\n"
                + "}\n";
    }

    private static String renderSkill(Options options) {
        return "---\n"
                + "name: " + options.skillName + "\n"
                + "description: Example workflow skill contributed by " + options.displayName + ".\n"
                + "---\n"
                + "\n"
                + "# " + options.displayName + " Skill\n"
                + "\n"
                + "Use this skill as a starting point for workflows that should travel with the plugin jar.\n"
                + "\n"
                + "## Workflow\n"
                + "\n"
                + "1. Read the host application's task context.\n"
                + "2. Decide whether plugin tools are relevant.\n"
                + "3. Call only explicitly exposed tools.\n";
    }

    private static String renderPrompt(Options options) {
        return "# " + options.displayName + " Prompt\n"
                + "\n"
                + "Summarize the plugin result for the application user.\n"
                + "\n"
                + "Input:\n"
                + "- Task context\n"
                + "- Tool result\n"
                + "\n"
                + "Output:\n"
                + "- A concise answer\n"
                + "- Any follow-up action required from the application\n";
    }

    private static String renderReadme(Options options) {
        return "# " + options.displayName + "\n"
                + "\n"
                + "This is an AI4J extension plugin package generated by `ai4j-cli extension init`.\n"
                + "\n"
                + "## Package Metadata\n"
                + "\n"
                + "| Field | Value |\n"
                + "| --- | --- |\n"
                + "| Maven groupId | `" + options.groupId + "` |\n"
                + "| Maven artifactId | `" + options.artifactId + "` |\n"
                + "| Version | `" + options.version + "` |\n"
                + "| Extension id | `" + options.extensionId + "` |\n"
                + "| Extension class | `" + options.packageName + "." + options.className + "` |\n"
                + "| Config prefix | `ai4j.extensions." + options.namespace + "` |\n"
                + "| Vendor | `" + options.vendor + "` |\n"
                + "| AI4J extension API | `" + options.extensionApiVersion + "` |\n"
                + "\n"
                + "## Runtime Resources\n"
                + "\n"
                + "| Type | Name | Notes |\n"
                + "| --- | --- | --- |\n"
                + "| Tool | `" + options.toolName + "` | Example echo tool with JSON input schema. |\n"
                + "| Command | `" + options.commandName + "` | Human-invoked CLI command; it is not exposed to models. |\n"
                + "| Skill | `" + options.skillName + "` | Classpath resource at `skills/" + options.resourceSegment + "/SKILL.md`. |\n"
                + "| Prompt | `" + options.promptName + "` | Classpath resource at `prompts/" + options.resourceSegment + "-summary.md`. |\n"
                + "| Guardrail | `" + options.guardrailName + "` | Example allow-all guardrail; replace it with real policy or remove it. |\n"
                + "\n"
                + "The `ServiceLoader` registration lives at `src/main/resources/META-INF/services/io.github.lnyocly.ai4j.extension.Ai4jExtension`.\n"
                + "\n"
                + "## Author Workflow\n"
                + "\n"
                + "1. Run the generated validator test before editing behavior.\n"
                + "2. Replace the echo tool, command, Skill, Prompt, and guardrail with real plugin logic.\n"
                + "3. Keep the manifest id, tool names, resource names, and input schemas stable after users depend on them.\n"
                + "4. Re-run validation after every resource path, schema, or manifest change.\n"
                + "5. Publish the jar through your normal Maven repository flow; AI4J does not host or install this plugin for users.\n"
                + "\n"
                + "## Validate Locally\n"
                + "\n"
                + "The generated test uses `ExtensionValidator` directly and does not need a running AI provider:\n"
                + "\n"
                + "```bash\n"
                + "mvn test\n"
                + "```\n"
                + "\n"
                + "When this plugin jar is on the `ai4j-cli` classpath, these commands provide manual smoke checks:\n"
                + "\n"
                + "```bash\n"
                + "ai4j-cli extension validate " + options.extensionId + "\n"
                + "ai4j-cli extension inspect " + options.extensionId + " --runtime\n"
                + "ai4j-cli extension resource --enable " + options.extensionId + " skill " + options.skillName + "\n"
                + "ai4j-cli extension resource --enable " + options.extensionId + " prompt " + options.promptName + "\n"
                + "ai4j-cli extension run --enable " + options.extensionId + " " + options.commandName + " hello\n"
                + "```\n"
                + "\n"
                + "CLI validation and runtime inspection may call `apply(...)` to collect contributed resources. They do not expose tools to a model.\n"
                + "\n"
                + "## Host Integration\n"
                + "\n"
                + "Users add this plugin as a normal dependency managed by their build tool:\n"
                + "\n"
                + "```xml\n"
                + "<dependency>\n"
                + "  <groupId>" + xml(options.groupId) + "</groupId>\n"
                + "  <artifactId>" + xml(options.artifactId) + "</artifactId>\n"
                + "  <version>" + xml(options.version) + "</version>\n"
                + "</dependency>\n"
                + "```\n"
                + "\n"
                + "Then the host application enables and exposes resources explicitly:\n"
                + "\n"
                + "```java\n"
                + "ExtensionRegistry registry = ExtensionRegistry.discover()\n"
                + "        .enable(\"" + options.extensionId + "\")\n"
                + "        .exposeTool(\"" + options.toolName + "\");\n"
                + "```\n"
                + "\n"
                + "Spring Boot hosts can use configuration for the same gate:\n"
                + "\n"
                + "```yaml\n"
                + "ai:\n"
                + "  extensions:\n"
                + "    enabled:\n"
                + "      - " + options.extensionId + "\n"
                + "    tools:\n"
                + "      expose:\n"
                + "        - " + options.toolName + "\n"
                + "```\n"
                + "\n"
                + "Classpath discovery does not enable this extension. Enabling it does not automatically expose tools to a model. The host application still decides which plugin packages are enabled and which tools are exposed.\n"
                + "\n"
                + "## Security And Side Effects\n"
                + "\n"
                + "- Declared permissions: none in the generated sample. Add manifest `.permission(...)` entries if the plugin touches network, files, databases, or external APIs.\n"
                + "- Side effects: the generated sample is echo-only. Replace this line with the real side effects before publishing.\n"
                + "- Environment variables: none in the generated sample. Document required env vars by name and never hardcode secrets.\n"
                + "- Tool exposure: tools can be called by a model only after the host calls `exposeTool(...)` or configures `ai.extensions.tools.expose`.\n"
                + "- Command execution: `extension run` requires `--enable`; classpath discovery never executes commands implicitly.\n"
                + "\n"
                + "## Publish Checklist\n"
                + "\n"
                + "- Maven / Gradle coordinates and supported AI4J version range.\n"
                + "- Manifest id, version, vendor, config prefix, and declared permissions.\n"
                + "- Tools, commands, skills, prompts, and guardrails contributed by the plugin.\n"
                + "- JSON input schema for each tool and example command invocations.\n"
                + "- Network, filesystem, database, external API, and credential requirements.\n"
                + "- Local smoke evidence such as `mvn test` and `ai4j-cli extension validate " + options.extensionId + "`.\n";
    }

    private static Path packagePath(String packageName) {
        return java.nio.file.Paths.get(packageName.replace('.', '/'));
    }

    private static String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String java(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') {
                builder.append("\\\\");
            } else if (ch == '"') {
                builder.append("\\\"");
            } else if (ch == '\n') {
                builder.append("\\n");
            } else if (ch == '\r') {
                builder.append("\\r");
            } else if (ch == '\t') {
                builder.append("\\t");
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    static final class Result {
        private final Path targetDirectory;
        private final List<Path> createdFiles;

        private Result(Path targetDirectory, List<Path> createdFiles) {
            this.targetDirectory = targetDirectory;
            this.createdFiles = createdFiles == null
                    ? Collections.<Path>emptyList()
                    : Collections.unmodifiableList(new ArrayList<Path>(createdFiles));
        }

        Path getTargetDirectory() {
            return targetDirectory;
        }

        List<Path> getCreatedFiles() {
            return createdFiles;
        }
    }

    static final class Options {
        private final Path targetDirectory;
        private final String extensionId;
        private final String packageName;
        private final String displayName;
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String className;
        private final String vendor;
        private final String extensionApiVersion;
        private final String namespace;
        private final String resourceSegment;
        private final String toolName;
        private final String commandName;
        private final String skillName;
        private final String promptName;
        private final String guardrailName;

        private Options(Builder builder) {
            this.targetDirectory = requireTargetDirectory(builder.targetDirectory);
            this.extensionId = requireExtensionId(builder.extensionId);
            this.packageName = requirePackageName(builder.packageName);
            this.displayName = defaultIfBlank(builder.displayName, titleFromId(this.extensionId));
            this.groupId = requireCoordinateId(defaultIfBlank(builder.groupId, this.packageName), "group id");
            this.artifactId = requireCoordinateId(defaultIfBlank(builder.artifactId, this.extensionId), "artifact id");
            this.version = defaultIfBlank(builder.version, "1.0.0");
            this.className = requireClassName(defaultIfBlank(builder.className, classNameFromId(this.extensionId)));
            this.vendor = defaultIfBlank(builder.vendor, "example");
            this.extensionApiVersion = defaultIfBlank(builder.extensionApiVersion, "2.3.0");
            this.namespace = namespaceFromId(this.extensionId);
            this.resourceSegment = resourceSegmentFromId(this.extensionId);
            this.toolName = this.namespace + ".echo";
            this.commandName = this.resourceSegment + "-echo";
            this.skillName = this.resourceSegment + "-skill";
            this.promptName = this.resourceSegment + "-prompt";
            this.guardrailName = this.namespace + ".guardrail";
        }

        static Options copyOf(Options options) {
            if (options == null) {
                throw new IllegalArgumentException("scaffold options must not be null");
            }
            return options;
        }

        static Builder builder() {
            return new Builder();
        }

        Path getTargetDirectory() {
            return targetDirectory;
        }

        String getExtensionId() {
            return extensionId;
        }

        String getPackageName() {
            return packageName;
        }

        String getClassName() {
            return className;
        }

        private static Path requireTargetDirectory(Path targetDirectory) {
            if (targetDirectory == null) {
                throw new IllegalArgumentException("target directory is required");
            }
            return targetDirectory.toAbsolutePath().normalize();
        }

        private static String requireExtensionId(String value) {
            String normalized = ExtensionManifest.requireId(value, "extension id");
            return requireCoordinateId(normalized, "extension id");
        }

        private static String requireCoordinateId(String value, String field) {
            String normalized = ExtensionManifest.requireId(value, field);
            if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
                throw new IllegalArgumentException(field + " must start with a letter or digit and contain only letters, digits, dot, underscore, or hyphen");
            }
            return normalized;
        }

        private static String requirePackageName(String value) {
            String normalized = ExtensionManifest.requireId(value, "java package");
            String[] parts = normalized.split("\\.");
            for (String part : parts) {
                if (!isJavaIdentifier(part) || isJavaKeyword(part)) {
                    throw new IllegalArgumentException("java package contains an invalid segment: " + part);
                }
            }
            return normalized;
        }

        private static String requireClassName(String value) {
            String normalized = ExtensionManifest.requireId(value, "class name");
            if (!isJavaIdentifier(normalized) || isJavaKeyword(normalized)) {
                throw new IllegalArgumentException("class name is not a valid Java identifier: " + normalized);
            }
            return normalized;
        }

        private static String defaultIfBlank(String value, String defaultValue) {
            String normalized = ExtensionManifest.emptyToNull(value);
            return normalized == null ? defaultValue : normalized;
        }

        private static String classNameFromId(String extensionId) {
            StringBuilder builder = new StringBuilder();
            boolean capitalizeNext = true;
            for (int i = 0; i < extensionId.length(); i++) {
                char ch = extensionId.charAt(i);
                if (Character.isLetterOrDigit(ch)) {
                    if (builder.length() == 0 && Character.isDigit(ch)) {
                        builder.append("Ai4j");
                    }
                    builder.append(capitalizeNext ? Character.toUpperCase(ch) : ch);
                    capitalizeNext = false;
                } else {
                    capitalizeNext = true;
                }
            }
            if (builder.length() == 0) {
                builder.append("Ai4jPlugin");
            }
            if (!builder.toString().endsWith("Extension")) {
                builder.append("Extension");
            }
            return builder.toString();
        }

        private static String titleFromId(String extensionId) {
            String[] parts = extensionId.split("[._-]+");
            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (part.length() == 0) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    builder.append(part.substring(1));
                }
            }
            return builder.length() == 0 ? extensionId : builder.toString();
        }

        private static String namespaceFromId(String extensionId) {
            String normalized = extensionId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
            normalized = normalized.replaceAll("\\.+", ".");
            normalized = strip(normalized, '.');
            return normalized.length() == 0 ? "plugin" : normalized;
        }

        private static String resourceSegmentFromId(String extensionId) {
            String normalized = extensionId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            normalized = normalized.replaceAll("-+", "-");
            normalized = strip(normalized, '-');
            return normalized.length() == 0 ? "plugin" : normalized;
        }

        private static String strip(String value, char ch) {
            int start = 0;
            int end = value.length();
            while (start < end && value.charAt(start) == ch) {
                start++;
            }
            while (end > start && value.charAt(end - 1) == ch) {
                end--;
            }
            return value.substring(start, end);
        }

        private static boolean isJavaIdentifier(String value) {
            if (value == null || value.length() == 0) {
                return false;
            }
            if (!Character.isJavaIdentifierStart(value.charAt(0))) {
                return false;
            }
            for (int i = 1; i < value.length(); i++) {
                if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isJavaKeyword(String value) {
            return "abstract".equals(value) || "assert".equals(value) || "boolean".equals(value)
                    || "break".equals(value) || "byte".equals(value) || "case".equals(value)
                    || "catch".equals(value) || "char".equals(value) || "class".equals(value)
                    || "const".equals(value) || "continue".equals(value) || "default".equals(value)
                    || "do".equals(value) || "double".equals(value) || "else".equals(value)
                    || "enum".equals(value) || "extends".equals(value) || "final".equals(value)
                    || "finally".equals(value) || "float".equals(value) || "for".equals(value)
                    || "goto".equals(value) || "if".equals(value) || "implements".equals(value)
                    || "import".equals(value) || "instanceof".equals(value) || "int".equals(value)
                    || "interface".equals(value) || "long".equals(value) || "native".equals(value)
                    || "new".equals(value) || "package".equals(value) || "private".equals(value)
                    || "protected".equals(value) || "public".equals(value) || "return".equals(value)
                    || "short".equals(value) || "static".equals(value) || "strictfp".equals(value)
                    || "super".equals(value) || "switch".equals(value) || "synchronized".equals(value)
                    || "this".equals(value) || "throw".equals(value) || "throws".equals(value)
                    || "transient".equals(value) || "try".equals(value) || "void".equals(value)
                    || "volatile".equals(value) || "while".equals(value);
        }

        static final class Builder {
            private Path targetDirectory;
            private String extensionId;
            private String packageName;
            private String displayName;
            private String groupId;
            private String artifactId;
            private String version;
            private String className;
            private String vendor;
            private String extensionApiVersion;

            private Builder() {
            }

            Builder targetDirectory(Path targetDirectory) {
                this.targetDirectory = targetDirectory;
                return this;
            }

            Builder extensionId(String extensionId) {
                this.extensionId = extensionId;
                return this;
            }

            Builder packageName(String packageName) {
                this.packageName = packageName;
                return this;
            }

            Builder displayName(String displayName) {
                this.displayName = displayName;
                return this;
            }

            Builder groupId(String groupId) {
                this.groupId = groupId;
                return this;
            }

            Builder artifactId(String artifactId) {
                this.artifactId = artifactId;
                return this;
            }

            Builder version(String version) {
                this.version = version;
                return this;
            }

            Builder className(String className) {
                this.className = className;
                return this;
            }

            Builder vendor(String vendor) {
                this.vendor = vendor;
                return this;
            }

            Builder extensionApiVersion(String extensionApiVersion) {
                this.extensionApiVersion = extensionApiVersion;
                return this;
            }

            Options build() {
                return new Options(this);
            }
        }
    }
}
