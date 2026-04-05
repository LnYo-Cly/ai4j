package io.github.lnyocly.ai4j.coding.workspace;

import java.io.IOException;
import java.util.List;

public interface WorkspaceFileService {

    List<WorkspaceEntry> listFiles(String path, int maxDepth, int maxEntries) throws IOException;

    WorkspaceFileReadResult readFile(String path, Integer startLine, Integer endLine, Integer maxChars) throws IOException;

    WorkspaceWriteResult writeFile(String path, String content, boolean append) throws IOException;
}
