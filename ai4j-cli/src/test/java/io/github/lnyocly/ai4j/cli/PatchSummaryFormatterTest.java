package io.github.lnyocly.ai4j.cli;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PatchSummaryFormatterTest {

    @Test
    public void summarizePatchRequestSupportsLongAndShortDirectives() {
        String patch = "*** Begin Patch\n"
                + "*** Add File: notes/todo.txt\n"
                + "+hello\n"
                + "*** Update: src/App.java\n"
                + "@@\n"
                + "-old\n"
                + "+new\n"
                + "*** Delete File: old.txt\n"
                + "*** End Patch";

        List<String> lines = PatchSummaryFormatter.summarizePatchRequest(patch, 8);

        assertEquals(Arrays.asList(
                "Add notes/todo.txt",
                "Update src/App.java",
                "Delete old.txt"
        ), lines);
    }

    @Test
    public void summarizePatchResultPrefersStructuredFileChanges() {
        JSONObject output = new JSONObject();
        JSONArray fileChanges = new JSONArray();
        fileChanges.add(new JSONObject()
                .fluentPut("path", "notes/todo.txt")
                .fluentPut("operation", "add")
                .fluentPut("linesAdded", 3)
                .fluentPut("linesRemoved", 0));
        fileChanges.add(new JSONObject()
                .fluentPut("path", "src/App.java")
                .fluentPut("operation", "update")
                .fluentPut("linesAdded", 8)
                .fluentPut("linesRemoved", 2));
        output.put("fileChanges", fileChanges);

        List<String> lines = PatchSummaryFormatter.summarizePatchResult(output, 8);

        assertEquals(Arrays.asList(
                "Created notes/todo.txt (+3 -0)",
                "Edited src/App.java (+8 -2)"
        ), lines);
    }
}
