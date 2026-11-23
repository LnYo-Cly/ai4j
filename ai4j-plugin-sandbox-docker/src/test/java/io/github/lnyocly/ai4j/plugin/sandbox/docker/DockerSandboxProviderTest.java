package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class DockerSandboxProviderTest {

    @Test
    public void discoversProviderViaServiceLoaderAndEnforcesHardenedContainerFlags() throws Exception {
        DockerSandboxProvider discovered = null;
        for (SandboxProvider provider : ServiceLoader.load(SandboxProvider.class)) {
            if (provider instanceof DockerSandboxProvider) {
                discovered = (DockerSandboxProvider) provider;
                break;
            }
        }
        Assert.assertNotNull(discovered);
        Assert.assertEquals("docker", discovered.getProviderId());
        Assert.assertTrue(discovered.supports(SandboxSpec.builder().providerId("docker").build()));
        Assert.assertTrue(discovered.supports(SandboxSpec.builder().providerId("docker-sandbox").build()));
        Assert.assertFalse(discovered.supports(SandboxSpec.builder().providerId("e2b").build()));

        RecordingRunner runner = new RecordingRunner();
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).stdout("cid-12345\n").durationMillis(12L).build());
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).stdout("hello\n").stderr("").durationMillis(8L).build());
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).stdout("cid-12345\n").durationMillis(5L).build());

        Map<String, Object> configMap = new LinkedHashMap<String, Object>();
        configMap.put("memory", "256m");
        configMap.put("cpus", "0.5");
        configMap.put("pidsLimit", Integer.valueOf(32));
        configMap.put("workingDirectory", "/work");

        DockerSandboxProvider provider = new DockerSandboxProvider(null, runner, Collections.<String, String>emptyMap());
        SandboxSession session = provider.createSession(SandboxSpec.builder()
                .providerId("docker")
                .image("python:3.11-alpine")
                .label("env", "test")
                .config(configMap)
                .build());

        Assert.assertEquals("cid-12345", ((DockerSandboxSession) session).getContainerId());
        Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());

        List<String> runCmd = runner.recordedCommands.get(0);
        Assert.assertEquals("docker", runCmd.get(0));
        Assert.assertEquals("run", runCmd.get(1));
        Assert.assertTrue(runCmd.contains("--read-only"));
        Assert.assertTrue(runCmd.contains("--network=none"));
        Assert.assertTrue(runCmd.contains("--memory"));
        Assert.assertEquals("256m", runCmd.get(runCmd.indexOf("--memory") + 1));
        Assert.assertTrue(runCmd.contains("--cpus"));
        Assert.assertEquals("0.5", runCmd.get(runCmd.indexOf("--cpus") + 1));
        Assert.assertTrue(runCmd.contains("--pids-limit"));
        Assert.assertEquals("32", runCmd.get(runCmd.indexOf("--pids-limit") + 1));
        Assert.assertTrue(runCmd.contains("python:3.11-alpine"));

        SandboxResult result = session.execute(SandboxCommand.builder()
                .commandId("cmd-1")
                .command("echo hello")
                .stdin("input-data")
                .environment("FOO", "BAR")
                .timeoutMillis(Long.valueOf(5000L))
                .build());

        Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
        Assert.assertEquals("hello\n", result.getStdout());
        Assert.assertFalse(result.isTimedOut());
        Assert.assertEquals("input-data", runner.recordedStdins.get(1));
        List<String> execCmd = runner.recordedCommands.get(1);
        Assert.assertEquals("exec", execCmd.get(1));
        Assert.assertTrue(execCmd.contains("-i"));
        Assert.assertTrue(execCmd.contains("FOO=BAR"));
        Assert.assertTrue(execCmd.contains("cid-12345"));

        session.close();
        Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
        List<String> rmCmd = runner.recordedCommands.get(2);
        Assert.assertEquals("docker", rmCmd.get(0));
        Assert.assertEquals("rm", rmCmd.get(1));
        Assert.assertEquals("-f", rmCmd.get(2));
        Assert.assertEquals("cid-12345", rmCmd.get(3));

        // Idempotent close should not invoke rm -f twice.
        session.close();
        Assert.assertEquals(3, runner.recordedCommands.size());
    }

    @Test
    public void timeoutTriggersForceRemoveCleanupAndMarksSessionFailed() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).stdout("cid-timeout\n").build());
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(124).stdout("partial").stderr("timed out").timedOut(true).durationMillis(1000L).build());
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).stdout("cid-timeout\n").build());

        DockerSandboxProvider provider = new DockerSandboxProvider(DockerSandboxConfig.builder().build(), runner);
        SandboxSession session = provider.createSession(SandboxSpec.builder().providerId("docker").build());

        SandboxResult result = session.execute(SandboxCommand.builder()
                .command("sleep 60")
                .timeoutMillis(Long.valueOf(100L))
                .build());

        Assert.assertTrue(result.isTimedOut());
        Assert.assertEquals(Integer.valueOf(124), result.getExitCode());
        Assert.assertEquals(SandboxStatus.FAILED, session.getStatus());
        Assert.assertEquals(3, runner.recordedCommands.size());

        List<String> rmCmd = runner.recordedCommands.get(2);
        Assert.assertEquals("rm", rmCmd.get(1));
        Assert.assertEquals("-f", rmCmd.get(2));
        Assert.assertEquals("cid-timeout", rmCmd.get(3));

        try {
            session.execute(SandboxCommand.builder().command("echo after-timeout").build());
            Assert.fail("Expected SandboxException after container was force-removed on timeout");
        } catch (SandboxException expected) {
            Assert.assertTrue(expected.getMessage().contains("no longer running"));
        }

        session.close();
        Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
    }

    @Test
    public void createSessionFailureTriggersBestEffortRemove() {
        RecordingRunner runner = new RecordingRunner();
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(125).stderr("image pull failed").build());
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).build());

        DockerSandboxProvider provider = new DockerSandboxProvider(DockerSandboxConfig.builder().build(), runner);
        try {
            provider.createSession(SandboxSpec.builder().providerId("docker").build());
            Assert.fail("Expected SandboxException on non-zero docker run exit");
        } catch (SandboxException ex) {
            Assert.assertTrue(ex.getMessage().contains("image pull failed"));
        }

        Assert.assertEquals(2, runner.recordedCommands.size());
        Assert.assertEquals("rm", runner.recordedCommands.get(1).get(1));
        Assert.assertEquals("-f", runner.recordedCommands.get(1).get(2));
    }

    @Test
    public void cancelForceRemovesContainerAndClosesSession() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).stdout("cid-cancel\n").build());
        runner.nextOutputs.add(DockerProcessOutput.builder().exitCode(0).stdout("cid-cancel\n").build());

        DockerSandboxProvider provider = new DockerSandboxProvider(DockerSandboxConfig.builder().build(), runner);
        SandboxSession session = provider.createSession(SandboxSpec.builder().providerId("docker").build());

        Assert.assertTrue(session.cancel("cmd-x"));
        Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
        Assert.assertFalse(session.cancel("cmd-x"));
        Assert.assertTrue(session.listArtifacts().isEmpty());
    }

    private static final class RecordingRunner implements DockerProcessRunner {
        private final List<List<String>> recordedCommands = new ArrayList<List<String>>();
        private final List<String> recordedStdins = new ArrayList<String>();
        private final List<DockerProcessOutput> nextOutputs = new ArrayList<DockerProcessOutput>();

        @Override
        public DockerProcessOutput run(List<String> command, String stdin, long timeoutMillis) throws IOException {
            recordedCommands.add(new ArrayList<String>(command));
            recordedStdins.add(stdin);
            if (nextOutputs.isEmpty()) {
                return DockerProcessOutput.builder().exitCode(0).build();
            }
            return nextOutputs.remove(0);
        }
    }
}
