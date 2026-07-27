package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrepToolExecutor implements ToolExecutor {

    static final int DEFAULT_MAX_RESULTS = 100;
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;

    private final WorkspaceContext workspaceContext;

    public GrepToolExecutor(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String patternStr = safeTrim(arguments.getString("pattern"));
        if (isBlank(patternStr)) {
            throw new IllegalArgumentException("pattern is required");
        }
        String basePath = safeTrim(arguments.getString("path"));
        if (isBlank(basePath)) {
            basePath = ".";
        }
        String include = safeTrim(arguments.getString("include"));
        Boolean caseInsensitive = arguments.getBoolean("caseInsensitive");
        Integer maxResultsRaw = arguments.getInteger("maxResults");
        int maxResults = maxResultsRaw == null || maxResultsRaw <= 0 ? DEFAULT_MAX_RESULTS : maxResultsRaw;

        int flags = 0;
        if (caseInsensitive != null && caseInsensitive) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        Pattern regex = Pattern.compile(patternStr, flags);

        Path searchBase = workspaceContext.resolveReadablePath(basePath);
        if (!Files.exists(searchBase)) {
            throw new IllegalArgumentException("Path does not exist: " + basePath);
        }

        PathMatcher includeMatcher = isBlank(include) ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + include);

        SearchResult result = search(searchBase, regex, includeMatcher, maxResults);

        JSONObject json = new JSONObject();
        json.put("pattern", patternStr);
        json.put("path", basePath);
        json.put("matches", result.matches);
        json.put("filesSearched", result.filesSearched);
        json.put("filesMatched", result.filesMatched);
        json.put("totalMatches", result.totalMatches);
        json.put("truncated", result.totalMatches >= maxResults);
        return JSON.toJSONString(json);
    }

    SearchResult search(Path searchBase, Pattern regex, PathMatcher includeMatcher, int maxResults) throws IOException {
        List<Map<String, Object>> matches = new ArrayList<Map<String, Object>>();
        final int[] filesSearched = {0};
        final int[] filesMatched = {0};
        final int[] totalMatches = {0};

        if (Files.isRegularFile(searchBase)) {
            searchInFile(searchBase, regex, includeMatcher, maxResults, matches,
                    filesSearched, filesMatched, totalMatches, searchBase);
            return new SearchResult(matches, filesSearched[0], filesMatched[0], totalMatches[0]);
        }

        Files.walkFileTree(searchBase, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(searchBase) && workspaceContext.isExcluded(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (totalMatches[0] >= maxResults) {
                    return FileVisitResult.TERMINATE;
                }
                if (workspaceContext.isExcluded(file)) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    searchInFile(file, regex, includeMatcher, maxResults, matches,
                            filesSearched, filesMatched, totalMatches, searchBase);
                } catch (IOException ignored) {
                    // Skip files that cannot be read
                }
                if (totalMatches[0] >= maxResults) {
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        return new SearchResult(matches, filesSearched[0], filesMatched[0], totalMatches[0]);
    }

    private void searchInFile(Path file, Pattern regex, PathMatcher includeMatcher,
                              int maxResults, List<Map<String, Object>> matches,
                              int[] filesSearched, int[] filesMatched, int[] totalMatches,
                              Path searchBase) throws IOException {
        if (Files.size(file) > MAX_FILE_SIZE_BYTES) {
            return;
        }
        if (includeMatcher != null) {
            Path fileName = file.getFileName();
            if (fileName == null || !includeMatcher.matches(fileName)) {
                return;
            }
        }
        filesSearched[0]++;

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        boolean fileMatched = false;
        for (int i = 0; i < lines.size(); i++) {
            if (totalMatches[0] >= maxResults) {
                return;
            }
            Matcher m = regex.matcher(lines.get(i));
            if (m.find()) {
                Map<String, Object> match = new LinkedHashMap<String, Object>();
                match.put("file", toRelativeForwardSlash(file));
                match.put("line", i + 1);
                match.put("content", lines.get(i));
                matches.add(match);
                fileMatched = true;
                totalMatches[0]++;
            }
        }
        if (fileMatched) {
            filesMatched[0]++;
        }
    }

    private String toRelativeForwardSlash(Path path) {
        Path root = workspaceContext.getRoot();
        if (path.startsWith(root)) {
            return root.relativize(path).toString().replace('\\', '/');
        }
        return path.toString().replace('\\', '/');
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
    }

    private static String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static class SearchResult {
        final List<Map<String, Object>> matches;
        final int filesSearched;
        final int filesMatched;
        final int totalMatches;

        SearchResult(List<Map<String, Object>> matches, int filesSearched, int filesMatched, int totalMatches) {
            this.matches = matches;
            this.filesSearched = filesSearched;
            this.filesMatched = filesMatched;
            this.totalMatches = totalMatches;
        }
    }
}
