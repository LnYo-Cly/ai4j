package io.github.lnyocly.ai4j.cli.runtime;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class CodingCliSessionRunnerArgumentParsingTest {

    @Test
    public void splitShellLikeArgumentsPreservesWindowsBackslashes() {
        List<String> arguments = CodingCliSessionRunner.splitShellLikeArguments(
                "resource --enable ask-user skill C:\\tmp\\agent\\skill"
        );

        Assert.assertEquals("resource", arguments.get(0));
        Assert.assertEquals("C:\\tmp\\agent\\skill", arguments.get(4));
    }

    @Test
    public void splitShellLikeArgumentsKeepsQuotedValuesAndEscapedQuotes() {
        List<String> arguments = CodingCliSessionRunner.splitShellLikeArguments(
                "run --enable ask-user ask \"hello world\" \"say \\\"hi\\\"\""
        );

        Assert.assertEquals("hello world", arguments.get(4));
        Assert.assertEquals("say \"hi\"", arguments.get(5));
    }
}
