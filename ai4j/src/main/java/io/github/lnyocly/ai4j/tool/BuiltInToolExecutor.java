package io.github.lnyocly.ai4j.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class BuiltInToolExecutor {

    private static final String BEGIN_PATCH = "*** Begin Patch";
    private static final String END_PATCH = "*** End Patch";
    private static final String ADD_FILE = "*** Add File:";
    private static final String ADD_FILE_ALIAS = "*** Add:";
    private static final String UPDATE_FILE = "*** Update File:";
    private static final String UPDATE_FILE_ALIAS = "*** Update:";
    private static final String DELETE_FILE = "*** Delete File:";
    private static final String DELETE_FILE_ALIAS = "*** Delete:";

    private BuiltInToolExecutor() {
    }

    public static boolean supports(String functionName) {
        return BuiltInTools.allCodingToolNames().contains(functionName);
    }

    public static String invoke(String functionName, String argument, BuiltInToolContext context) throws Exception {
        if (!supports(functionName)) {
            return null;
        }
        BuiltInToolContext effectiveContext = context == null
                ? BuiltInToolContext.builder().build()
                : context;

        JSONObject arguments = parseArguments(argument);
        if (BuiltInTools.READ_FILE.equals(functionName)) {
            return JSON.toJSONString(readFile(effectiveContext, arguments));
        }
        if (BuiltInTools.WRITE_FILE.equals(functionName)) {
            return JSON.toJSONString(writeFile(effectiveContext, arguments));
        }
        if (BuiltInTools.APPLY_PATCH.equals(functionName)) {
            return JSON.toJSONString(applyPatch(effectiveContext, arguments));
        }
        if (BuiltInTools.BASH.equals(functionName)) {
            return JSON.toJSONString(runBash(effectiveContext, arguments));
        }
        throw new IllegalArgumentException("Unsupported built-in tool: " + functionName);
    }

    private static Map<String, Object> readFile(BuiltInToolContext context, JSONObject arguments) throws IOException {
        String path = arguments.getString("path");
        if (isBlank(path)) {
            throw new IllegalArgumentException("path is required");
        }
        Path file = context.resolveReadablePath(path);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Path is a directory: " + path);
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int startLine = arguments.getInteger("startLine") == null || arguments.getInteger("startLine").intValue() < 1
                ? 1
                : arguments.getInteger("startLine").intValue();
        int endLine = arguments.getInteger("endLine") == null || arguments.getInteger("endLine").intValue() > lines.size()
                ? lines.size()
                : arguments.getInteger("endLine").intValue();
        if (endLine < startLine) {
            endLine = startLine - 1;
        }

        StringBuilder contentBuilder = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            if (i > lines.size()) {
                break;
            }
            if (contentBuilder.length() > 0) {
                contentBuilder.append('\n');
            }
            contentBuilder.append(lines.get(i - 1));
        }

        int maxChars = arguments.getInteger("maxChars") == null || arguments.getInteger("maxChars").intValue() <= 0
                ? context.getDefaultReadMaxChars()
                : arguments.getInteger("maxChars").intValue();
        String content = contentBuilder.toString();
        boolean truncated = false;
        if (content.length() > maxChars) {
            content = content.substring(0, maxChars);
            truncated = true;
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("path", toDisplayPath(context, file));
        result.put("content", content);
        result.put("startLine", startLine);
        result.put("endLine", endLine);
        result.put("truncated", truncated);
        return result;
    }

    private static Map<String, Object> writeFile(BuiltInToolContext context, JSONObject arguments) throws IOException {
        String path = safeTrim(arguments.getString("path"));
        if (isBlank(path)) {
            throw new IllegalArgumentException("path is required");
        }
        String content = arguments.containsKey("content") && arguments.get("content") != null
                ? arguments.getString("content")
                : "";
        String mode = firstNonBlank(safeTrim(arguments.getString("mode")), "overwrite").toLowerCase(Locale.ROOT);
        Path file = context.resolveWorkspacePath(path);
        if (Files.exists(file) && Files.isDirectory(file)) {
            throw new IllegalArgumentException("Target is a directory: " + path);
        }

        boolean existed = Files.exists(file);
        boolean appended = false;
        boolean created;
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if ("create".equals(mode)) {
            if (existed) {
                throw new IllegalArgumentException("File already exists: " + path);
            }
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE});
            created = true;
        } else if ("overwrite".equals(mode)) {
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE});
            created = !existed;
        } else if ("append".equals(mode)) {
            Files.write(file, bytes, new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE});
            created = !existed;
            appended = true;
        } else {
            throw new IllegalArgumentException("Unsupported write mode: " + mode);
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("path", path);
        result.put("resolvedPath", file.toString());
        result.put("mode", mode);
        result.put("created", created);
        result.put("appended", appended);
        result.put("bytesWritten", bytes.length);
        return result;
    }

    private static Map<String, Object> runBash(BuiltInToolContext context, JSONObject arguments) throws Exception {
        String action = firstNonBlank(arguments.getString("action"), "exec");
        if ("exec".equals(action)) {
            return exec(context, arguments);
        }
        BuiltInProcessRegistry processRegistry = context.getOrCreateProcessRegistry();
        if ("start".equals(action)) {
            return processRegistry.start(arguments.getString("command"), arguments.getString("cwd"));
        }
        if ("status".equals(action)) {
            return processRegistry.status(arguments.getString("processId"));
        }
        if ("logs".equals(action)) {
            return processRegistry.logs(
                    arguments.getString("processId"),
                    arguments.getLong("offset"),
                    arguments.getInteger("limit")
            );
        }
        if ("write".equals(action)) {
            String processId = arguments.getString("processId");
            int bytesWritten = processRegistry.write(processId, arguments.getString("input"));
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("process", processRegistry.status(processId));
            result.put("bytesWritten", bytesWritten);
            return result;
        }
        if ("stop".equals(action)) {
            return processRegistry.stop(arguments.getString("processId"));
        }
        if ("list".equals(action)) {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("processes", processRegistry.list());
            return result;
        }
        throw new IllegalArgumentException("Unsupported bash action: " + action);
    }

    private static Map<String, Object> exec(BuiltInToolContext context, JSONObject arguments) throws Exception {
        String command = arguments.getString("command");
        if (isBlank(command)) {
            throw new IllegalArgumentException("command is required");
        }
        Path workingDirectory = context.resolveWorkspacePath(arguments.getString("cwd"));
        long timeoutMs = arguments.getLong("timeoutMs") == null || arguments.getLong("timeoutMs").longValue() <= 0L
                ? context.getDefaultCommandTimeoutMs()
                : arguments.getLong("timeoutMs").longValue();

        ProcessBuilder processBuilder;
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("sh", "-lc", command);
        }
        processBuilder.directory(workingDirectory.toFile());
        Process process = processBuilder.start();

        Charset shellCharset = resolveShellCharset();
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutThread = new Thread(new StreamCollector(process.getInputStream(), stdout, shellCharset), "ai4j-built-in-stdout");
        Thread stderrThread = new Thread(new StreamCollector(process.getErrorStream(), stderr, shellCharset), "ai4j-built-in-stderr");
        stdoutThread.start();
        stderrThread.start();

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        int exitCode;
        if (finished) {
            exitCode = process.exitValue();
        } else {
            process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
            exitCode = -1;
            if (stderr.length() > 0) {
                stderr.append('\n');
            }
            stderr.append("Command timed out before exit. If it is interactive or long-running, use bash action=start and then bash action=logs/status/write/stop instead of bash action=exec.");
        }

        stdoutThread.join();
        stderrThread.join();

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("command", command);
        result.put("workingDirectory", workingDirectory.toString());
        result.put("stdout", stdout.toString());
        result.put("stderr", stderr.toString());
        result.put("exitCode", exitCode);
        result.put("timedOut", !finished);
        return result;
    }

    private static Charset resolveShellCharset() {
        try {
            String explicit = System.getProperty("ai4j.shell.encoding");
            if (!isBlank(explicit)) {
                return Charset.forName(explicit.trim());
            }
        } catch (Exception ignored) {
        }
        try {
            String env = System.getenv("AI4J_SHELL_ENCODING");
            if (!isBlank(env)) {
                return Charset.forName(env.trim());
            }
        } catch (Exception ignored) {
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return StandardCharsets.UTF_8;
        }
        String[] candidates = new String[]{
                System.getProperty("native.encoding"),
                System.getProperty("sun.jnu.encoding"),
                System.getProperty("file.encoding"),
                Charset.defaultCharset().name()
        };
        for (String candidate : candidates) {
            if (isBlank(candidate)) {
                continue;
            }
            try {
                return Charset.forName(candidate.trim());
            } catch (Exception ignored) {
            }
        }
        return Charset.defaultCharset();
    }

    private static Map<String, Object> applyPatch(BuiltInToolContext context, JSONObject arguments) throws IOException {
        String patch = arguments.getString("patch");
        if (isBlank(patch)) {
            throw new IllegalArgumentException("patch is required");
        }
        return applyPatch(context, patch);
    }

    private static Map<String, Object> applyPatch(BuiltInToolContext context, String patchText) throws IOException {
        List<String> lines = normalizeLines(patchText);
        if (lines.size() < 2 || !BEGIN_PATCH.equals(lines.get(0)) || !END_PATCH.equals(lines.get(lines.size() - 1))) {
            throw new IllegalArgumentException("Invalid patch envelope");
        }

        int index = 1;
        int operationsApplied = 0;
        Set<String> changedFiles = new LinkedHashSet<String>();
        List<Map<String, Object>> fileChanges = new ArrayList<Map<String, Object>>();
        while (index < lines.size() - 1) {
            String line = lines.get(index);
            PatchDirective directive = parseDirective(line);
            if (directive == null) {
                if (line.trim().isEmpty()) {
                    index++;
                    continue;
                }
                throw new IllegalArgumentException("Unsupported patch line: " + line);
            }
            if ("add".equals(directive.operation)) {
                PatchOperation operation = applyAddFile(context, lines, index + 1, directive.path);
                index = operation.nextIndex;
                operationsApplied++;
                changedFiles.add(operation.path);
                fileChanges.add(operation.fileChange);
                continue;
            }
            if ("update".equals(directive.operation)) {
                PatchOperation operation = applyUpdateFile(context, lines, index + 1, directive.path);
                index = operation.nextIndex;
                operationsApplied++;
                changedFiles.add(operation.path);
                fileChanges.add(operation.fileChange);
                continue;
            }
            if ("delete".equals(directive.operation)) {
                PatchOperation operation = applyDeleteFile(context, directive.path, index + 1);
                index = operation.nextIndex;
                operationsApplied++;
                changedFiles.add(operation.path);
                fileChanges.add(operation.fileChange);
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("filesChanged", changedFiles.size());
        result.put("operationsApplied", operationsApplied);
        result.put("changedFiles", new ArrayList<String>(changedFiles));
        result.put("fileChanges", fileChanges);
        return result;
    }

    private static PatchOperation applyAddFile(BuiltInToolContext context,
                                               List<String> lines,
                                               int startIndex,
                                               String path) throws IOException {
        Path file = context.resolveWorkspacePath(path);
        if (Files.exists(file)) {
            throw new IllegalArgumentException("File already exists: " + path);
        }
        List<String> contentLines = new ArrayList<String>();
        int index = startIndex;
        while (index < lines.size() - 1 && !lines.get(index).startsWith("*** ")) {
            String line = lines.get(index);
            if (!line.startsWith("+")) {
                throw new IllegalArgumentException("Add file lines must start with '+': " + line);
            }
            contentLines.add(line.substring(1));
            index++;
        }
        writePatchFile(file, joinLines(contentLines));
        return new PatchOperation(index, normalizeRelativePath(path), buildFileChange(normalizeRelativePath(path), "add", contentLines.size(), 0));
    }

    private static PatchOperation applyUpdateFile(BuiltInToolContext context,
                                                  List<String> lines,
                                                  int startIndex,
                                                  String path) throws IOException {
        Path file = context.resolveWorkspacePath(path);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }

        List<String> body = new ArrayList<String>();
        int index = startIndex;
        while (index < lines.size() - 1 && !lines.get(index).startsWith("*** ")) {
            body.add(lines.get(index));
            index++;
        }

        List<String> normalizedBody = normalizeUpdateBody(body);
        String original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String updated = applyUpdateBody(original, normalizedBody, path);
        writePatchFile(file, updated);
        return new PatchOperation(
                index,
                normalizeRelativePath(path),
                buildFileChange(normalizeRelativePath(path), "update", countPrefixedLines(normalizedBody, '+'), countPrefixedLines(normalizedBody, '-'))
        );
    }

    private static PatchOperation applyDeleteFile(BuiltInToolContext context,
                                                  String path,
                                                  int startIndex) throws IOException {
        Path file = context.resolveWorkspacePath(path);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }
        String original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int removed = splitContentLines(original).size();
        Files.delete(file);
        return new PatchOperation(startIndex, normalizeRelativePath(path), buildFileChange(normalizeRelativePath(path), "delete", 0, removed));
    }

    private static String applyUpdateBody(String original, List<String> body, String path) {
        List<String> originalLines = splitContentLines(original);
        List<List<String>> hunks = parseHunks(body);

        List<String> output = new ArrayList<String>();
        int cursor = 0;
        for (List<String> hunk : hunks) {
            List<String> anchor = resolveAnchor(hunk);
            int matchIndex = findAnchor(originalLines, cursor, anchor);
            if (matchIndex < 0) {
                throw new IllegalArgumentException("Failed to locate patch hunk in file: " + path);
            }

            appendRange(output, originalLines, cursor, matchIndex);
            int current = matchIndex;
            for (String line : hunk) {
                if (line.startsWith("@@")) {
                    continue;
                }
                if (line.isEmpty()) {
                    throw new IllegalArgumentException("Invalid empty patch line in update body");
                }
                char prefix = line.charAt(0);
                String content = line.substring(1);
                switch (prefix) {
                    case ' ':
                        ensureMatch(originalLines, current, content, path);
                        output.add(originalLines.get(current));
                        current++;
                        break;
                    case '-':
                        ensureMatch(originalLines, current, content, path);
                        current++;
                        break;
                    case '+':
                        output.add(content);
                        break;
                    default:
                        throw new IllegalArgumentException("Unsupported update line: " + line);
                }
            }
            cursor = current;
        }

        appendRange(output, originalLines, cursor, originalLines.size());
        return joinLines(output);
    }

    private static List<List<String>> parseHunks(List<String> body) {
        List<List<String>> hunks = new ArrayList<List<String>>();
        List<String> current = new ArrayList<String>();
        for (String line : body) {
            if (line.startsWith("@@")) {
                if (!current.isEmpty()) {
                    hunks.add(current);
                    current = new ArrayList<String>();
                }
                current.add(line);
                continue;
            }
            if (line.startsWith(" ") || line.startsWith("+") || line.startsWith("-")) {
                current.add(line);
                continue;
            }
            if (line.trim().isEmpty()) {
                current.add(" ");
                continue;
            }
            throw new IllegalArgumentException("Unsupported update body line: " + line);
        }
        if (!current.isEmpty()) {
            hunks.add(current);
        }
        if (hunks.isEmpty()) {
            throw new IllegalArgumentException("Update file patch must contain at least one hunk");
        }
        return hunks;
    }

    private static List<String> resolveAnchor(List<String> hunk) {
        List<String> leading = new ArrayList<String>();
        for (String line : hunk) {
            if (line.startsWith("@@")) {
                continue;
            }
            if (line.startsWith(" ") || line.startsWith("-")) {
                leading.add(line.substring(1));
            } else if (line.startsWith("+")) {
                break;
            }
        }
        if (!leading.isEmpty()) {
            return leading;
        }
        for (String line : hunk) {
            if (line.startsWith("+")) {
                return java.util.Collections.singletonList(line.substring(1));
            }
        }
        return new ArrayList<String>();
    }

    private static int findAnchor(List<String> originalLines, int cursor, List<String> anchor) {
        if (anchor.isEmpty()) {
            return cursor;
        }
        for (int i = cursor; i <= originalLines.size() - anchor.size(); i++) {
            boolean match = true;
            for (int j = 0; j < anchor.size(); j++) {
                if (!originalLines.get(i + j).equals(anchor.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private static void appendRange(List<String> output, List<String> originalLines, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            output.add(originalLines.get(i));
        }
    }

    private static void ensureMatch(List<String> originalLines, int current, String expected, String path) {
        if (current >= originalLines.size()) {
            throw new IllegalArgumentException("Patch exceeds file length: " + path);
        }
        String actual = originalLines.get(current);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Patch context mismatch in file " + path + ": expected [" + expected + "] but found [" + actual + "]");
        }
    }

    private static int countPrefixedLines(List<String> lines, char prefix) {
        int count = 0;
        for (String line : lines) {
            if (!line.isEmpty() && line.charAt(0) == prefix) {
                count++;
            }
        }
        return count;
    }

    private static List<String> normalizeUpdateBody(List<String> body) {
        List<String> normalized = new ArrayList<String>();
        for (String line : body) {
            if (line != null) {
                normalized.add(line);
            }
        }
        return normalized;
    }

    private static void writePatchFile(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static List<String> splitContentLines(String content) {
        String normalized = content == null ? "" : content.replace("\r\n", "\n");
        List<String> lines = new ArrayList<String>();
        if (normalized.isEmpty()) {
            return lines;
        }
        String[] split = normalized.split("\n", -1);
        for (int i = 0; i < split.length; i++) {
            if (i == split.length - 1 && normalized.endsWith("\n") && split[i].isEmpty()) {
                continue;
            }
            lines.add(split[i]);
        }
        return lines;
    }

    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private static List<String> normalizeLines(String text) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n");
        String[] split = normalized.split("\n", -1);
        List<String> lines = new ArrayList<String>(split.length);
        for (String line : split) {
            lines.add(line);
        }
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static PatchDirective parseDirective(String line) {
        if (line.startsWith(ADD_FILE)) {
            return new PatchDirective("add", safeTrim(line.substring(ADD_FILE.length())));
        }
        if (line.startsWith(ADD_FILE_ALIAS)) {
            return new PatchDirective("add", safeTrim(line.substring(ADD_FILE_ALIAS.length())));
        }
        if (line.startsWith(UPDATE_FILE)) {
            return new PatchDirective("update", safeTrim(line.substring(UPDATE_FILE.length())));
        }
        if (line.startsWith(UPDATE_FILE_ALIAS)) {
            return new PatchDirective("update", safeTrim(line.substring(UPDATE_FILE_ALIAS.length())));
        }
        if (line.startsWith(DELETE_FILE)) {
            return new PatchDirective("delete", safeTrim(line.substring(DELETE_FILE.length())));
        }
        if (line.startsWith(DELETE_FILE_ALIAS)) {
            return new PatchDirective("delete", safeTrim(line.substring(DELETE_FILE_ALIAS.length())));
        }
        return null;
    }

    private static JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
    }

    private static Map<String, Object> buildFileChange(String path, String operation, int linesAdded, int linesRemoved) {
        Map<String, Object> change = new LinkedHashMap<String, Object>();
        change.put("path", path);
        change.put("operation", operation);
        change.put("linesAdded", linesAdded);
        change.put("linesRemoved", linesRemoved);
        return change;
    }

    private static String normalizeRelativePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private static String toDisplayPath(BuiltInToolContext context, Path file) {
        Path root = context.getWorkspaceRootPath();
        if (file == null) {
            return "";
        }
        if (!file.startsWith(root)) {
            return file.toString().replace('\\', '/');
        }
        if (file.equals(root)) {
            return ".";
        }
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class PatchDirective {

        private final String operation;
        private final String path;

        private PatchDirective(String operation, String path) {
            this.operation = operation;
            this.path = path;
        }
    }

    private static final class PatchOperation {

        private final int nextIndex;
        private final String path;
        private final Map<String, Object> fileChange;

        private PatchOperation(int nextIndex, String path, Map<String, Object> fileChange) {
            this.nextIndex = nextIndex;
            this.path = path;
            this.fileChange = fileChange;
        }
    }

    private static final class StreamCollector implements Runnable {

        private final InputStream inputStream;
        private final StringBuilder target;
        private final Charset charset;

        private StreamCollector(InputStream inputStream, StringBuilder target, Charset charset) {
            this.inputStream = inputStream;
            this.target = target;
            this.charset = charset;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (target) {
                        if (target.length() > 0) {
                            target.append('\n');
                        }
                        target.append(line);
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}
