package io.github.lnyocly.agent.cubesandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxSession;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Real CubeSandbox smoke test.
 *
 * <p>Default Maven runs exclude {@link LiveProviderTest}. To run this against a
 * live CubeSandbox deployment, set env vars only (never commit values):
 * AI4J_CUBESANDBOX_LIVE=true, CUBE_API_URL, CUBE_TEMPLATE_ID, and optionally
 * CUBE_API_KEY/E2B_API_KEY, CUBE_PROXY_NODE_IP, CUBE_PROXY_PORT_HTTP,
 * CUBE_PROXY_SCHEME, CUBE_SANDBOX_DOMAIN.</p>
 */
@Category(LiveProviderTest.class)
public class CubeSandboxLiveProviderTest {

    @Test
    public void liveCubeSandboxShouldCreateExecuteAndDestroy() throws Exception {
        Assume.assumeTrue("Skip CubeSandbox live test unless AI4J_CUBESANDBOX_LIVE=true",
                "true".equalsIgnoreCase(System.getenv("AI4J_CUBESANDBOX_LIVE")));
        Assume.assumeTrue("Skip CubeSandbox live test because CUBE_API_URL/E2B_API_URL is missing",
                hasText(firstEnv("CUBE_API_URL", "E2B_API_URL")));
        Assume.assumeTrue("Skip CubeSandbox live test because CUBE_TEMPLATE_ID is missing",
                hasText(System.getenv("CUBE_TEMPLATE_ID")));

        CubeSandboxProvider provider = new CubeSandboxProvider();
        CubeSandboxSession session = null;
        try {
            session = provider.createSession(SandboxSpec.builder()
                    .providerId("cubesandbox")
                    .workspaceId("/workspace")
                    .label("ai4jLiveTest", "true")
                    .build());
            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("ai4j-live-cubesandbox")
                    .command("printf ai4j-cubesandbox-ok")
                    .workingDirectory("/workspace")
                    .timeoutMillis(30000L)
                    .build());
            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertTrue(result.getStdout(), result.getStdout().contains("ai4j-cubesandbox-ok"));
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private static String firstEnv(String first, String second) {
        String value = System.getenv(first);
        return hasText(value) ? value : System.getenv(second);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
