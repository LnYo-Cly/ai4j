package io.github.lnyocly.ai4j.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.cli.render.PatchSummaryFormatter;
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
        ObjectNode output = CliJson.newObject();
        ArrayNode fileChanges = CliJson.newArray();
        fileChanges.addObject()
                .put("path", "notes/todo.txt")
                .put("operation", "add")
                .put("linesAdded", 3)
                .put("linesRemoved", 0);
        fileChanges.addObject()
                .put("path", "src/App.java")
                .put("operation", "update")
                .put("linesAdded", 8)
                .put("linesRemoved", 2);
        output.set("fileChanges", fileChanges);

        List<String> lines = PatchSummaryFormatter.summarizePatchResult(output, 8);

        assertEquals(Arrays.asList(
                "Created notes/todo.txt (+3 -0)",
                "Edited src/App.java (+8 -2)"
        ), lines);
    }
}
