package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;

public class ProcessBuilderDockerProcessRunnerTest {

    @Test
    public void executesLocalJavaVersionSubprocessDeterministically() throws Exception {
        String javaExecutable = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilderDockerProcessRunner runner = new ProcessBuilderDockerProcessRunner();

        DockerProcessOutput output = runner.run(
                Arrays.asList(javaExecutable, "-version"),
                null,
                15_000L
        );

        Assert.assertFalse(output.isTimedOut());
        Assert.assertEquals(0, output.getExitCode());
        String combined = output.getStdout() + output.getStderr();
        Assert.assertTrue("Expected java version output, got: " + combined,
                combined.toLowerCase().contains("version"));
    }
}
