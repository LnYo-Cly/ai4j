package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.patch.ApplyPatchFileChange;
import io.github.lnyocly.ai4j.coding.patch.ApplyPatchResult;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ApplyPatchToolExecutor implements ToolExecutor {

    private static final String BEGIN_PATCH = "*** Begin Patch";
    private static final String END_PATCH = "*** End Patch";
    private static final String ADD_FILE = "*** Add File:";
    private static final String ADD_FILE_ALIAS = "*** Add:";
    private static final String UPDATE_FILE = "*** Update File:";
    private static final String UPDATE_FILE_ALIAS = "*** Update:";
    private static final String DELETE_FILE = "*** Delete File:";
    private static final String DELETE_FILE_ALIAS = "*** Delete:";

    private final WorkspaceContext workspaceContext;

    public ApplyPatchToolExecutor(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String patch = arguments.getString("patch");
        if (patch == null || patch.trim().isEmpty()) {
            throw new IllegalArgumentException("patch is required");
        }
        ApplyPatchResult result = apply(patch);
        return JSON.toJSONString(result);
    }

    private ApplyPatchResult apply(String patchText) throws IOException {
        List<String> lines = normalizeLines(patchText);
        if (lines.size() < 2 || !BEGIN_PATCH.equals(lines.get(0)) || !END_PATCH.equals(lines.get(lines.size() - 1))) {
            throw new IllegalArgumentException("Invalid patch envelope");
        }

        int index = 1;
        int operationsApplied = 0;
        Set<String> changedFiles = new LinkedHashSet<String>();
        List<ApplyPatchFileChange> fileChanges = new ArrayList<ApplyPatchFileChange>();
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
                String path = directive.path;
                PatchOperation operation = applyAddFile(lines, index + 1, path);
                index = operation.nextIndex;
                operationsApplied++;
                changedFiles.add(operation.fileChange.getPath());
                fileChanges.add(operation.fileChange);
                continue;
            }
            if ("update".equals(directive.operation)) {
                String path = directive.path;
                PatchOperation operation = applyUpdateFile(lines, index + 1, path);
                index = operation.nextIndex;
                operationsApplied++;
                changedFiles.add(operation.fileChange.getPath());
                fileChanges.add(operation.fileChange);
                continue;
            }
            if ("delete".equals(directive.operation)) {
                String path = directive.path;
                PatchOperation operation = applyDeleteFile(path, index + 1);
                index = operation.nextIndex;
                operationsApplied++;
                changedFiles.add(operation.fileChange.getPath());
                fileChanges.add(operation.fileChange);
                continue;
            }
        }

        return ApplyPatchResult.builder()
                .filesChanged(changedFiles.size())
                .operationsApplied(operationsApplied)
                .changedFiles(new ArrayList<String>(changedFiles))
                .fileChanges(fileChanges)
                .build();
    }

    private PatchOperation applyAddFile(List<String> lines, int startIndex, String path) throws IOException {
        Path file = workspaceContext.resolveWorkspacePath(path);
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
        writeFile(file, joinLines(contentLines));
        return new PatchOperation(index, ApplyPatchFileChange.builder()
                .path(normalizeRelativePath(path))
                .operation("add")
                .linesAdded(contentLines.size())
                .linesRemoved(0)
                .build());
    }

    private PatchOperation applyUpdateFile(List<String> lines, int startIndex, String path) throws IOException {
        Path file = workspaceContext.resolveWorkspacePath(path);
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
        writeFile(file, updated);
        return new PatchOperation(index, ApplyPatchFileChange.builder()
                .path(normalizeRelativePath(path))
                .operation("update")
                .linesAdded(countPrefixedLines(normalizedBody, '+'))
                .linesRemoved(countPrefixedLines(normalizedBody, '-'))
                .build());
    }

    private PatchOperation applyDeleteFile(String path, int startIndex) throws IOException {
        Path file = workspaceContext.resolveWorkspacePath(path);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }
        String original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int removed = splitContentLines(original).size();
        Files.delete(file);
        return new PatchOperation(startIndex, ApplyPatchFileChange.builder()
                .path(normalizeRelativePath(path))
                .operation("delete")
                .linesAdded(0)
                .linesRemoved(removed)
                .build());
    }

    private String applyUpdateBody(String original, List<String> body, String path) {
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

    private List<List<String>> parseHunks(List<String> body) {
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

    private List<String> resolveAnchor(List<String> hunk) {
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
            if (line.startsWith("@@") || line.startsWith("+")) {
                continue;
            }
            leading.add(line.substring(1));
        }
        return leading;
    }

    private int findAnchor(List<String> originalLines, int fromIndex, List<String> anchor) {
        if (anchor.isEmpty()) {
            return fromIndex;
        }
        int max = originalLines.size() - anchor.size();
        for (int i = Math.max(0, fromIndex); i <= max; i++) {
            boolean matched = true;
            for (int j = 0; j < anchor.size(); j++) {
                if (!originalLines.get(i + j).equals(anchor.get(j))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    private void ensureMatch(List<String> originalLines, int current, String expected, String path) {
        if (current >= originalLines.size()) {
            throw new IllegalArgumentException("Patch exceeds file length: " + path);
        }
        String actual = originalLines.get(current);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Patch context mismatch for file " + path + ": expected '" + expected + "' but found '" + actual + "'");
        }
    }

    private void appendRange(List<String> output, List<String> originalLines, int from, int to) {
        for (int i = from; i < to; i++) {
            output.add(originalLines.get(i));
        }
    }

    private void writeFile(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> splitContentLines(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.isEmpty()) {
            return new ArrayList<String>();
        }
        String[] parts = normalized.split("\n", -1);
        List<String> lines = new ArrayList<String>();
        int length = parts.length;
        if (length > 0 && parts[length - 1].isEmpty()) {
            length--;
        }
        for (int i = 0; i < length; i++) {
            lines.add(parts[i]);
        }
        return lines;
    }

    private String joinLines(List<String> lines) {
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

    private String normalizeRelativePath(String path) {
        Path resolved = workspaceContext.resolveWorkspacePath(path);
        Path root = workspaceContext.getRoot();
        if (resolved.equals(root)) {
            return ".";
        }
        return root.relativize(resolved).toString().replace('\\', '/');
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
    }

    private List<String> normalizeLines(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        List<String> lines = new ArrayList<String>();
        int length = parts.length;
        if (length > 0 && parts[length - 1].isEmpty()) {
            length--;
        }
        for (int i = 0; i < length; i++) {
            lines.add(parts[i]);
        }
        return lines;
    }

    private int countPrefixedLines(List<String> lines, char prefix) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String line : lines) {
            if (line != null && !line.isEmpty() && line.charAt(0) == prefix) {
                count++;
            }
        }
        return count;
    }

    private List<String> normalizeUpdateBody(List<String> body) {
        if (body == null || body.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> normalized = new ArrayList<String>();
        boolean sawPatchContent = false;
        for (String line : body) {
            if (!sawPatchContent && isUnifiedDiffMetadataLine(line)) {
                continue;
            }
            normalized.add(line);
            if (line != null
                    && (line.startsWith("@@")
                    || line.startsWith(" ")
                    || line.startsWith("+")
                    || line.startsWith("-"))) {
                sawPatchContent = true;
            }
        }
        return normalized;
    }

    private boolean isUnifiedDiffMetadataLine(String line) {
        if (line == null) {
            return false;
        }
        return line.startsWith("--- ")
                || line.startsWith("+++ ")
                || line.startsWith("diff --git ")
                || line.startsWith("index ");
    }

    private PatchDirective parseDirective(String line) {
        String path = directivePath(line, ADD_FILE);
        if (path != null) {
            return new PatchDirective("add", path);
        }
        path = directivePath(line, ADD_FILE_ALIAS);
        if (path != null) {
            return new PatchDirective("add", path);
        }
        path = directivePath(line, UPDATE_FILE);
        if (path != null) {
            return new PatchDirective("update", path);
        }
        path = directivePath(line, UPDATE_FILE_ALIAS);
        if (path != null) {
            return new PatchDirective("update", path);
        }
        path = directivePath(line, DELETE_FILE);
        if (path != null) {
            return new PatchDirective("delete", path);
        }
        path = directivePath(line, DELETE_FILE_ALIAS);
        if (path != null) {
            return new PatchDirective("delete", path);
        }
        return null;
    }

    private String directivePath(String line, String directivePrefix) {
        if (line == null || directivePrefix == null || !line.startsWith(directivePrefix)) {
            return null;
        }
        String rawPath = line.substring(directivePrefix.length()).trim();
        String normalized = normalizePatchPath(rawPath);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Patch directive is missing a file path: " + line);
        }
        return normalized;
    }

    private String normalizePatchPath(String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        while ((path.startsWith("/") || path.startsWith("\\")) && !looksLikeAbsolutePath(path)) {
            path = path.substring(1).trim();
        }
        return path;
    }

    private boolean looksLikeAbsolutePath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        if (path.startsWith("\\\\") || path.startsWith("//")) {
            return true;
        }
        return path.length() >= 3
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && (path.charAt(2) == '\\' || path.charAt(2) == '/');
    }

    private static final class PatchOperation {

        private final int nextIndex;
        private final ApplyPatchFileChange fileChange;

        private PatchOperation(int nextIndex, ApplyPatchFileChange fileChange) {
            this.nextIndex = nextIndex;
            this.fileChange = fileChange;
        }
    }

    private static final class PatchDirective {

        private final String operation;
        private final String path;

        private PatchDirective(String operation, String path) {
            this.operation = operation;
            this.path = path;
        }
    }
}
