package io.github.lnyocly.ai4j.apidoc;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.javadoc.Javadoc;
import com.github.javaparser.ast.type.TypeParameter;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generates Docusaurus Markdown API reference pages from Java source files.
 */
public class ApiDocGenerator {

    private final Path sourceRoot;
    private final Path outputBase;
    private final String moduleId;
    private final JavaParser parser;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: ApiDocGenerator <sourceRoot> <outputBase> <moduleId>");
            System.err.println("  sourceRoot: path to Java source directory (e.g. module/src/main/java)");
            System.err.println("  outputBase: path to docs output directory (e.g. docs-site/docs/reference/api)");
            System.err.println("  moduleId: module identifier for subdir (e.g. extension-api)");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        Path outputBase = Paths.get(args[1]);
        String moduleId = args[2];

        try {
            new ApiDocGenerator(sourceRoot, outputBase, moduleId).generate();
        } catch (Exception e) {
            System.err.println("Error generating API docs: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public ApiDocGenerator(Path sourceRoot, Path outputBase, String moduleId) {
        this.sourceRoot = sourceRoot;
        this.outputBase = outputBase;
        this.moduleId = moduleId;
        this.parser = new JavaParser();
    }

    public void generate() throws IOException {
        Path moduleOutputDir = outputBase.resolve(moduleId);
        Files.createDirectories(moduleOutputDir);

        List<TypeInfo> types = discoverTypes(sourceRoot);
        types.sort(Comparator.comparing(t -> t.typeName.toLowerCase(Locale.ROOT)));

        System.out.println("Found " + types.size() + " public types");

        int position = 1;
        for (TypeInfo typeInfo : types) {
            generateTypePage(typeInfo, moduleOutputDir, position++);
        }

        generateCategoryJson(moduleOutputDir);
        System.out.println("Generated " + types.size() + " pages in " + moduleOutputDir);
    }

    private List<TypeInfo> discoverTypes(Path sourceRoot) throws IOException {
        List<TypeInfo> types = new ArrayList<>();

        Files.walk(sourceRoot, FileVisitOption.FOLLOW_LINKS)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) {
                        return;
                    }

                    for (TypeDeclaration<?> type : cu.getTypes()) {
                        if (isPublicType(type)) {
                            types.add(new TypeInfo(type, javaFile));
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Warning: failed to parse " + javaFile + ": " + e.getMessage());
                }
            });

        return types;
    }

    private boolean isPublicType(TypeDeclaration<?> type) {
        return type.getModifiers().contains(Modifier.publicModifier())
            && !type.isNestedType();
    }

    private void generateTypePage(TypeInfo typeInfo, Path outputDir, int position) throws IOException {
        TypeDeclaration<?> type = typeInfo.type;
        String typeName = type.getNameAsString();
        Path outputFile = outputDir.resolve(typeName + ".md");

        StringBuilder md = new StringBuilder();

        // Frontmatter
        md.append("---\n");
        md.append("title: ").append(typeName).append("\n");
        md.append("description: \"");
        String description = extractFirstSentence(type.getJavadoc());
        if (description.isEmpty()) {
            description = getTypeKindDescription(type) + " in " + type.getFullyQualifiedName().orElseGet(() -> typeName);
        }
        md.append(escapeYaml(description)).append("\"\n");
        md.append("tags: [reference]\n");
        md.append("sidebar_position: ").append(position).append("\n");
        md.append("---\n\n");

        // Type signature
        md.append("# `").append(typeName).append("`\n\n");
        md.append("`").append(type.getFullyQualifiedName().orElse(typeName)).append("`\n\n");
        md.append("```java\n");
        md.append(getTypeSignature(type)).append("\n");
        md.append("```\n\n");

        // Type Javadoc
        String typeJavadoc = extractJavadoc(type.getJavadoc());
        if (!typeJavadoc.isEmpty()) {
            md.append(typeJavadoc).append("\n\n");
        }

        // Public methods
        List<MethodDeclaration> publicMethods = type.getMethods().stream()
            .filter(m -> m.getModifiers().contains(Modifier.publicModifier()))
            .sorted(Comparator.comparing(MethodDeclaration::getNameAsString))
            .collect(Collectors.toList());

        if (!publicMethods.isEmpty()) {
            md.append("## Methods\n\n");
            for (MethodDeclaration method : publicMethods) {
                generateMethodSection(method, md);
            }
        }

        // Public fields
        List<FieldDeclaration> publicFields = type.getFields().stream()
            .filter(f -> f.getVariables().stream()
                .anyMatch(v -> f.getModifiers().contains(Modifier.publicModifier())))
            .sorted(Comparator.comparing(f -> f.getVariables().get(0).getNameAsString()))
            .collect(Collectors.toList());

        if (!publicFields.isEmpty()) {
            md.append("## Fields\n\n");
            for (FieldDeclaration field : publicFields) {
                generateFieldSection(field, md);
            }
        }

        Files.write(outputFile, md.toString().getBytes("UTF-8"));
    }

    private void generateMethodSection(MethodDeclaration method, StringBuilder md) {
        String methodName = method.getNameAsString();
        md.append("### `").append(methodName).append("`\n\n");
        md.append("```java\n");
        md.append(getMethodSignature(method)).append("\n");
        md.append("```\n\n");

        String methodJavadoc = extractMethodJavadoc(method);
        if (!methodJavadoc.isEmpty()) {
            md.append(methodJavadoc).append("\n\n");
        }
    }

    private void generateFieldSection(FieldDeclaration field, StringBuilder md) {
        md.append("- ```java\n");
        md.append(getFieldSignature(field)).append("\n");
        md.append("``` — ");

        String fieldJavadoc = extractJavadoc(field.getJavadoc());
        if (!fieldJavadoc.isEmpty()) {
            md.append(cleanJavadocText(fieldJavadoc));
        } else {
            md.append("Field of type `").append(field.getVariables().get(0).getType()).append("`");
        }
        md.append("\n\n");
    }

    private String getTypeKindDescription(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cls = (ClassOrInterfaceDeclaration) type;
            return cls.isInterface() ? "Interface" : "Class";
        } else if (type instanceof EnumDeclaration) {
            return "Enum";
        } else if (type.isAnnotationDeclaration()) {
            return "Annotation";
        }
        return "Type";
    }

    private String getTypeSignature(TypeDeclaration<?> type) {
        StringBuilder sb = new StringBuilder();

        // Modifiers
        sb.append(getModifiers(type.getModifiers()));

        // Kind and name
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cls = (ClassOrInterfaceDeclaration) type;
            if (cls.isInterface()) {
                sb.append("interface ");
            } else {
                sb.append("class ");
            }
        } else if (type instanceof EnumDeclaration) {
            sb.append("enum ");
        } else if (type.isAnnotationDeclaration()) {
            sb.append("@interface ");
        } else {
            sb.append("class ");
        }

        sb.append(type.getName());

        // Type parameters
        List<TypeParameter> typeParams = type.getTypeParameters();
        if (!typeParams.isEmpty()) {
            sb.append("<");
            sb.append(typeParams.stream()
                .map(Node::toString)
                .collect(Collectors.joining(", ")));
            sb.append(">");
        }

        // Extends
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cls = (ClassOrInterfaceDeclaration) type;
            cls.getExtendedTypes().ifPresent(types -> {
                if (!types.isEmpty()) {
                    sb.append(" extends ").append(types.get(0));
                }
            });

            // Implements
            cls.getImplementedTypes().ifPresent(types -> {
                if (!types.isEmpty()) {
                    sb.append(" implements ");
                    sb.append(types.stream().map(Node::toString).collect(Collectors.joining(", ")));
                }
            });
        } else if (type instanceof EnumDeclaration) {
            EnumDeclaration enumDecl = (EnumDeclaration) type;
            enumDecl.getImplementedTypes().ifPresent(types -> {
                if (!types.isEmpty()) {
                    sb.append(" implements ");
                    sb.append(types.stream().map(Node::toString).collect(Collectors.joining(", ")));
                }
            });
        }

        return sb.toString();
    }

    private String getMethodSignature(MethodDeclaration method) {
        StringBuilder sb = new StringBuilder();

        // Annotations
        for (var anno : method.getAnnotations()) {
            sb.append(anno).append(" ");
        }

        // Modifiers
        sb.append(getModifiers(method.getModifiers()));

        // Type parameters
        List<TypeParameter> typeParams = method.getTypeParameters();
        if (!typeParams.isEmpty()) {
            sb.append("<");
            sb.append(typeParams.stream()
                .map(Node::toString)
                .collect(Collectors.joining(", ")));
            sb.append("> ");
        }

        // Return type
        sb.append(method.getType()).append(" ");

        // Name
        sb.append(method.getName()).append("(");

        // Parameters
        sb.append(method.getParameters().stream()
            .map(p -> p.getType() + " " + p.getName())
            .collect(Collectors.joining(", ")));

        sb.append(")");

        // Throws
        List<com.github.javaparser.ast.type.ReferenceType> thrown = method.getThrownExceptions();
        if (!thrown.isEmpty()) {
            sb.append(" throws ");
            sb.append(thrown.stream()
                .map(Node::toString)
                .collect(Collectors.joining(", ")));
        }

        return sb.toString();
    }

    private String getFieldSignature(FieldDeclaration field) {
        StringBuilder sb = new StringBuilder();

        sb.append(getModifiers(field.getModifiers()));
        sb.append(field.getElementType()).append(" ");
        sb.append(field.getVariables().get(0).getName());

        return sb.toString();
    }

    private String getModifiers(List<Modifier> modifiers) {
        return modifiers.stream()
            .map(m -> m.toString())
            .collect(Collectors.joining(" ")) + " ";
    }

    private String extractJavadoc(Optional<Javadoc> javadoc) {
        if (javadoc.isEmpty()) {
            return "";
        }

        String text = javadoc.get().toText();
        return cleanJavadocText(text);
    }

    private String extractFirstSentence(Optional<Javadoc> javadoc) {
        if (javadoc.isEmpty()) {
            return "";
        }

        String text = javadoc.get().toText();
        int firstPeriod = text.indexOf(". ");
        if (firstPeriod > 0 && firstPeriod < 100) {
            return text.substring(0, firstPeriod + 1).trim();
        }
        return text.split("\n", 2)[0].trim();
    }

    private String extractMethodJavadoc(MethodDeclaration method) {
        if (method.getJavadoc().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String description = method.getJavadoc().get().toText();
        sb.append(cleanJavadocText(description));

        // Extract block tags
        for (var tag : method.getJavadoc().get().getBlockTags()) {
            String tagName = tag.getTagName();
            String content = tag.toText();

            switch (tagName) {
                case "@param":
                    sb.append("\n- **@param** ").append(content).append("\n");
                    break;
                case "@return":
                case "@returns":
                    sb.append("\n- **@return** ").append(content).append("\n");
                    break;
                case "@throws":
                case "@exception":
                    sb.append("\n- **@throws** ").append(content).append("\n");
                    break;
                default:
                    sb.append("\n- **").append(tagName).append("** ").append(content).append("\n");
                    break;
            }
        }

        return sb.toString();
    }

    private String cleanJavadocText(String text) {
        return text.replaceAll("^\\s*\\*\\s?", "")
                   .replaceAll("\n\\s*\\*\\s?", "\n")
                   .trim();
    }

    private String escapeYaml(String value) {
        // For YAML safety in double-quoted scalar, only escape backslash and double-quote
        // Colons are fine inside double quotes
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"");
    }

    private void generateCategoryJson(Path outputDir) throws IOException {
        Path categoryFile = outputDir.resolve("_category_.json");
        String content = "{\n  \"label\": \"ai4j-" + moduleId + "\",\n  \"position\": 1\n}\n";
        Files.write(categoryFile, content.getBytes("UTF-8"));
    }

    private static class TypeInfo {
        final TypeDeclaration<?> type;
        final Path sourceFile;
        final String typeName;

        TypeInfo(TypeDeclaration<?> type, Path sourceFile) {
            this.type = type;
            this.sourceFile = sourceFile;
            this.typeName = type.getNameAsString();
        }
    }
}
