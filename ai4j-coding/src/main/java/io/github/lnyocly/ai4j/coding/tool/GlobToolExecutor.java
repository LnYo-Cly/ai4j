package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GlobToolExecutor implements ToolExecutor {

    static final int DEFAULT_MAX_RESULTS = 1000;

    private final WorkspaceContext workspaceContext;

    public GlobToolExecutor(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String pattern = safeTrim(arguments.getString("pattern"));
        if (isBlank(pattern)) {
            throw new IllegalArgumentException("pattern is required");
        }
        String basePath = safeTrim(arguments.getString("path"));
        if (isBlank(basePath)) {
            basePath = ".";
        }
        Integer maxResultsRaw = arguments.getInteger("maxResults");
        int maxResults = maxResultsRaw == null || maxResultsRaw <= 0 ? DEFAULT_MAX_RESULTS : maxResultsRaw;

        Path searchBase = workspaceContext.resolveReadablePath(basePath);
        if (!Files.exists(searchBase)) {
            throw new IllegalArgumentException("Path does not exist: " + basePath);
        }

        List<String> matches = glob(searchBase, pattern, maxResults);

        JSONObject result = new JSONObject();
        result.put("pattern", pattern);
        result.put("path", basePath);
        result.put("matches", matches);
        result.put("count", matches.size());
        result.put("truncated", matches.size() >= maxResults);
        return JSON.toJSONString(result);
    }

    List<String> glob(Path searchBase, String pattern, int maxResults) throws IOException {
        String matcherSyntax = pattern.startsWith("glob:") || pattern.startsWith("regex:")
                ? pattern
                : "glob:" + pattern;
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(matcherSyntax);
        final List<String> results = new ArrayList<String>();

        if (Files.isRegularFile(searchBase)) {
            if (matchesPath(searchBase.getFileName(), searchBase, matcher, searchBase)) {
                results.add(toForwardSlash(searchBase.getFileName().toString()));
            }
            return results;
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
                if (results.size() >= maxResults) {
                    return FileVisitResult.TERMINATE;
                }
                if (workspaceContext.isExcluded(file)) {
                    return FileVisitResult.CONTINUE;
                }
                if (matchesPath(searchBase.relativize(file), file, matcher, searchBase)) {
                    results.add(toRelativeForwardSlash(file, searchBase));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(results);
        return results;
    }

    private boolean matchesPath(Path relativeSegment, Path fullPath, PathMatcher matcher, Path searchBase) {
        if (matcher.matches(relativeSegment)) {
            return true;
        }
        Path relativeFromWorkspace = workspaceContext.getRoot().relativize(fullPath);
        return matcher.matches(relativeFromWorkspace);
    }

    private String toRelativeForwardSlash(Path path, Path searchBase) {
        Path root = workspaceContext.getRoot();
        if (path.startsWith(root)) {
            return toForwardSlash(root.relativize(path).toString());
        }
        return toForwardSlash(searchBase.relativize(path).toString());
    }

    private static String toForwardSlash(String path) {
        return path.replace('\\', '/');
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
}
