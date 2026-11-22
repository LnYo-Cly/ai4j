package io.github.lnyocly.ai4j.protocol;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Package dependency fixture for the new kernel (design F1/A1).
 *
 * <p>Asserts, by scanning source imports:</p>
 * <ul>
 *   <li>the four kernel packages are acyclic in the direction
 *       {@code model → protocol → transport → client};</li>
 *   <li>{@code model} imports no {@code platform} package;</li>
 *   <li>{@code client} imports no {@code platform} implementation class;</li>
 *   <li>no kernel package imports {@code tool}/{@code mcp}/{@code agent} (C6).</li>
 * </ul>
 *
 * <p>The scan is intentionally import-based and package-granular: it is exactly
 * the guarantee the design makes (F1 note), and it cannot be defeated by the
 * frozen legacy {@code service}/{@code platform} cycle because the four kernel
 * packages are disjoint from it.</p>
 */
public class PackageDependencyFixtureTest {

    private static final String PKG_ROOT = "io/github/lnyocly/ai4j/";

    private static final String AI4J_PACKAGE_PREFIX = "io.github.lnyocly.ai4j.";

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final List<String> KERNEL_PACKAGES =
            java.util.Arrays.asList("model", "protocol", "transport", "client");

    private static final Map<String, Set<String>> DEPENDENCIES = buildDependencies();

    @Test
    public void kernelPackagesAreAcyclic() {
        for (String from : KERNEL_PACKAGES) {
            for (String to : KERNEL_PACKAGES) {
                if (from.equals(to)) {
                    continue;
                }
                boolean fromImportsTo = DEPENDENCIES.get(from).contains(to);
                boolean toImportsFrom = DEPENDENCIES.get(to).contains(from);
                Assert.assertFalse(
                        "kernel dependency cycle detected between " + from + " and " + to
                                + ": " + from + " imports " + DEPENDENCIES.get(from)
                                + ", " + to + " imports " + DEPENDENCIES.get(to),
                        fromImportsTo && toImportsFrom);
            }
        }
    }

    @Test
    public void kernelDependsOnlyInTheDeclaredDirection() {
        Assert.assertTrue("protocol must use model", DEPENDENCIES.get("protocol").contains("model"));
        Assert.assertTrue("transport must use model", DEPENDENCIES.get("transport").contains("model"));
        Assert.assertTrue("transport must use protocol",
                DEPENDENCIES.get("transport").contains("protocol"));
        Assert.assertTrue("client must use model", DEPENDENCIES.get("client").contains("model"));
        Assert.assertTrue("client must use protocol", DEPENDENCIES.get("client").contains("protocol"));
        Assert.assertTrue("client must use transport",
                DEPENDENCIES.get("client").contains("transport"));
    }

    @Test
    public void modelDoesNotImportPlatform() {
        Assert.assertFalse("model must not import platform: " + DEPENDENCIES.get("model"),
                DEPENDENCIES.get("model").contains("platform"));
    }

    @Test
    public void clientDoesNotImportPlatformImplementation() {
        List<String> offenders = new ArrayList<String>();
        for (String importedPackage : DEPENDENCIES.get("client")) {
            if (headOf(importedPackage).equals("platform")) {
                offenders.add(importedPackage);
            }
        }
        Assert.assertTrue("client must not import platform.* implementation classes: " + offenders,
                offenders.isEmpty());
    }

    @Test
    public void kernelDoesNotImportToolMcpOrAgent() {
        for (String kernelPackage : KERNEL_PACKAGES) {
            for (String importedPackage : DEPENDENCIES.get(kernelPackage)) {
                String head = headOf(importedPackage);
                Assert.assertFalse(kernelPackage + " must not import " + importedPackage,
                        "tool".equals(head) || "mcp".equals(head) || "tools".equals(head)
                                || "agent".equals(head) || "agentflow".equals(head));
            }
        }
    }

    private static String headOf(String importedPackage) {
        if (importedPackage.isEmpty()) {
            return "";
        }
        int dot = importedPackage.indexOf('.');
        return dot < 0 ? importedPackage : importedPackage.substring(0, dot);
    }

    private static Map<String, Set<String>> buildDependencies() {
        Map<String, Set<String>> dependencies = new LinkedHashMap<String, Set<String>>();
        for (String kernelPackage : KERNEL_PACKAGES) {
            Set<String> imported = new LinkedHashSet<String>();
            Path packageRoot = resolvePackageRoot(kernelPackage);
            Assert.assertTrue("kernel package must exist: " + packageRoot,
                    Files.isDirectory(packageRoot));
            try (Stream<Path> files = Files.walk(packageRoot)) {
                for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                    imported.addAll(packagesImportedBy(file));
                }
            } catch (IOException e) {
                throw new AssertionError("unable to scan " + packageRoot, e);
            }
            dependencies.put(kernelPackage, imported);
        }
        return dependencies;
    }

    /**
     * Locate the package under either the reactor root or the module directory,
     * since surefire runs with the module as the working directory.
     */
    private static Path resolvePackageRoot(String kernelPackage) {
        Path fromReactorRoot = Paths.get("ai4j/src/main/java", PKG_ROOT, kernelPackage);
        if (Files.isDirectory(fromReactorRoot)) {
            return fromReactorRoot;
        }
        Path fromModule = Paths.get("src/main/java", PKG_ROOT, kernelPackage);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        return fromReactorRoot;
    }

    private static Set<String> packagesImportedBy(Path file) throws IOException {
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        Set<String> packages = new HashSet<String>();
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            String imported = matcher.group(1);
            int lastDot = imported.lastIndexOf('.');
            if (lastDot > 0) {
                packages.add(relativize(imported.substring(0, lastDot)));
            }
        }
        return packages;
    }

    /** Normalize kernel-relative package paths so dependency assertions stay readable. */
    private static String relativize(String importedPackage) {
        if (importedPackage.equals("io.github.lnyocly.ai4j")) {
            return "";
        }
        if (importedPackage.startsWith(AI4J_PACKAGE_PREFIX)) {
            return kernelPackageOf(importedPackage.substring(AI4J_PACKAGE_PREFIX.length()));
        }
        return importedPackage;
    }

    /**
     * Collapse a kernel subpackage onto its owning kernel package: importing
     * {@code protocol.messages} from {@code protocol} is an intra-package edge,
     * not a dependency on a sibling kernel package.
     */
    private static String kernelPackageOf(String relativePackage) {
        for (String kernelPackage : KERNEL_PACKAGES) {
            if (relativePackage.equals(kernelPackage)
                    || relativePackage.startsWith(kernelPackage + ".")) {
                return kernelPackage;
            }
        }
        return relativePackage;
    }
}
