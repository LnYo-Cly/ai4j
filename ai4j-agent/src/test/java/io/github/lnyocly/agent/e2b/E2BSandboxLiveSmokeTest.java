package io.github.lnyocly.agent.e2b;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.sandbox.e2b.E2BSandboxProvider;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Live smoke test against the real E2B control + execution API. Skipped unless
 * {@code E2B_API_KEY} is set in the environment.
 */
@Category(LiveProviderTest.class)
public class E2BSandboxLiveSmokeTest {

    @Test
    public void liveCreateExecuteCloseShouldWorkWhenE2bEnvIsConfigured() throws Exception {
        String apiKey = System.getenv("E2B_API_KEY");
        Assume.assumeTrue("Skip E2B live smoke: E2B_API_KEY is required",
                apiKey != null && !apiKey.trim().isEmpty());

        SandboxSession session = null;
        try {
            session = new E2BSandboxProvider().createSession(SandboxSpec.builder()
                    .providerId("e2b")
                    .config("templateID", "base")
                    .config("timeoutSeconds", Integer.valueOf(300))
                    .config("readTimeoutMillis", Long.valueOf(60000L))
                    .build());

            Assert.assertEquals("e2b", session.getProviderId());
            Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());

            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("e2b-live-smoke")
                    .command("printf ai4j-e2b-live-ok")
                    .timeoutMillis(Long.valueOf(30000L))
                    .build());

            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertNotNull(result.getStdout());
            Assert.assertTrue("stdout should contain marker: " + result.getStdout(),
                    result.getStdout().contains("ai4j-e2b-live-ok"));

            // Non-zero exit must also propagate (Connect end.exitCode).
            SandboxResult failing = session.execute(SandboxCommand.builder()
                    .commandId("e2b-live-fail")
                    .command("sh -c 'exit 7'")
                    .build());
            Assert.assertEquals(Integer.valueOf(7), failing.getExitCode());
        } finally {
            if (session != null) {
                session.close();
                Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
            }
        }
    }
}
