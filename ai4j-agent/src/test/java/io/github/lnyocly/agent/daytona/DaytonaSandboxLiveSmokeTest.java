package io.github.lnyocly.agent.daytona;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxProvider;
import org.junit.Assert;
import org.junit.experimental.categories.Category;
import org.junit.Assume;
import org.junit.Test;
import io.github.lnyocly.ai4j.test.LiveProviderTest;

@Category(LiveProviderTest.class)
public class DaytonaSandboxLiveSmokeTest {

    @Test
    public void liveCreateExecuteCloseShouldWorkWhenDaytonaEnvIsConfigured() throws Exception {
        String apiKey = System.getenv("DAYTONA_API_KEY");
        Assume.assumeTrue("Skip Daytona live smoke: DAYTONA_API_KEY is required",
                apiKey != null && !apiKey.trim().isEmpty());

        String name = "ai4j-agent-smoke-" + System.currentTimeMillis();
        SandboxSession session = null;
        try {
            session = new DaytonaSandboxProvider().createSession(SandboxSpec.builder()
                    .providerId("daytona")
                    .workspaceId(name)
                    .label("ai4j-sdk-test", "daytona-live-smoke")
                    .config("name", name)
                    .config("deleteOnClose", Boolean.TRUE)
                    .config("createIfMissing", Boolean.TRUE)
                    .config("autoDeleteInterval", Integer.valueOf(0))
                    .config("startTimeoutMillis", Long.valueOf(120000L))
                    .config("pollIntervalMillis", Long.valueOf(1000L))
                    .build());

            Assert.assertEquals("daytona", session.getProviderId());
            Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());

            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("daytona-live-smoke")
                    .command("printf ai4j-daytona-ok")
                    .timeoutMillis(Long.valueOf(30000L))
                    .build());

            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertTrue(result.getStdout() != null && result.getStdout().contains("ai4j-daytona-ok"));
        } finally {
            if (session != null) {
                session.close();
                Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
            }
        }
    }
}
