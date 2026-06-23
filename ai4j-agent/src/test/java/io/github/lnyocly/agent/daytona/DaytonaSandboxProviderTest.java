package io.github.lnyocly.agent.daytona;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxConfig;
import io.github.lnyocly.ai4j.agent.sandbox.daytona.DaytonaSandboxProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class DaytonaSandboxProviderTest {

    @Test
    public void createSessionShouldAttachExistingSandboxAndExecuteWithoutDeletingOnClose() throws Exception {
        DaytonaLocalHttpServer server = DaytonaLocalHttpServer.start();
        try {
            server.enqueue(200, sandbox("sandbox-existing", "workspace-1", "stopped", server.baseUrl() + "/toolbox"))
                    .enqueue(200, sandbox("sandbox-existing", "workspace-1", "started", server.baseUrl() + "/toolbox"))
                    .enqueue(200, "{\"exitCode\":0,\"result\":\"hello daytona\"}");

            DaytonaSandboxProvider provider = new DaytonaSandboxProvider(config(server, "workspace-1", false, false));
            SandboxSession session = provider.createSession(SandboxSpec.builder()
                    .providerId("daytona")
                    .workspaceId("workspace-1")
                    .build());

            Assert.assertEquals("daytona", session.getProviderId());
            Assert.assertEquals("sandbox-existing", session.getSessionId());
            Assert.assertEquals(SandboxStatus.RUNNING, session.getStatus());

            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("cmd-attach")
                    .command("echo hello")
                    .workingDirectory("/workspace")
                    .stdin("input-bytes")
                    .timeoutMillis(1500L)
                    .environment("LANG", "C")
                    .build());

            Assert.assertEquals("cmd-attach", result.getCommandId());
            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertEquals("hello daytona", result.getStdout());
            Assert.assertEquals(2, result.getEvents().size());

            session.close();
            Assert.assertEquals(SandboxStatus.CLOSED, session.getStatus());
            Assert.assertEquals(3, server.records().size());
            Assert.assertEquals("GET", server.record(0).method);
            Assert.assertEquals("/sandbox/workspace-1", server.record(0).path);
            Assert.assertEquals("POST", server.record(1).method);
            Assert.assertEquals("/sandbox/sandbox-existing/start", server.record(1).path);
            Assert.assertEquals("POST", server.record(2).method);
            Assert.assertEquals("/toolbox/sandbox-existing/process/execute", server.record(2).path);
            Assert.assertTrue(server.record(2).body.contains("\"timeout\":2"));
            Assert.assertTrue(server.record(2).body.contains("\"stdin\":\"input-bytes\""));
            Assert.assertTrue(server.record(2).body.contains("\"envs\""));
            Assert.assertFalse("attached close must not delete unless deleteOnClose is true",
                    containsPath(server.records(), "/sandbox/sandbox-existing"));
        } finally {
            server.close();
        }
    }

    @Test
    public void explicitCreateIfMissingFalseShouldNotBeOverriddenByPlainSpec() throws Exception {
        DaytonaLocalHttpServer server = DaytonaLocalHttpServer.start();
        try {
            server.enqueue(404, "{\"message\":\"missing\"}");

            DaytonaSandboxProvider provider = new DaytonaSandboxProvider(config(server, "workspace-missing", false, false));
            try {
                provider.createSession(SandboxSpec.builder()
                        .providerId("daytona")
                        .workspaceId("workspace-missing")
                        .build());
                Assert.fail("expected attach miss to fail when createIfMissing is false");
            } catch (Exception expected) {
                Assert.assertTrue(expected.getMessage().contains("failed to create or attach Daytona sandbox session"));
            }

            Assert.assertEquals(1, server.records().size());
            Assert.assertEquals("GET", server.record(0).method);
            Assert.assertEquals("/sandbox/workspace-missing", server.record(0).path);
            Assert.assertFalse("createIfMissing=false must not POST /sandbox after attach miss",
                    containsPath(server.records(), "/sandbox"));
        } finally {
            server.close();
        }
    }

    @Test
    public void createSessionShouldCreateWhenAttachMissesAndDeleteOnClose() throws Exception {
        DaytonaLocalHttpServer server = DaytonaLocalHttpServer.start();
        try {
            server.enqueue(404, "{\"message\":\"missing\"}")
                    .enqueue(200, sandbox("sandbox-created", "workspace-created", "creating", null))
                    .enqueue(200, sandbox("sandbox-created", "workspace-created", "started", null))
                    .enqueue(200, "{\"url\":\"" + server.baseUrl() + "/toolbox\"}")
                    .enqueue(200, "{\"exitCode\":0,\"result\":\"created-ok\"}")
                    .enqueue(200, sandbox("sandbox-created", "workspace-created", "destroyed", null));

            SandboxSpec spec = SandboxSpec.builder()
                    .providerId("daytona")
                    .image("ubuntu-24.04")
                    .workspaceId("workspace-created")
                    .label("task", "unit")
                    .config("cpu", Integer.valueOf(2))
                    .config("memory", Integer.valueOf(4))
                    .build();

            DaytonaSandboxProvider provider = new DaytonaSandboxProvider(config(server, "workspace-created", true, true));
            SandboxSession session = provider.createSession(spec);
            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("cmd-create")
                    .command("pwd")
                    .build());
            session.close();

            Assert.assertEquals("sandbox-created", session.getSessionId());
            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertEquals("created-ok", result.getStdout());
            Assert.assertEquals("GET", server.record(0).method);
            Assert.assertEquals("/sandbox/workspace-created", server.record(0).path);
            Assert.assertEquals("POST", server.record(1).method);
            Assert.assertEquals("/sandbox", server.record(1).path);
            JSONObject createBody = JSON.parseObject(server.record(1).body);
            Assert.assertEquals("workspace-created", createBody.getString("name"));
            Assert.assertEquals("ubuntu-24.04", createBody.getString("snapshot"));
            Assert.assertEquals(Integer.valueOf(2), createBody.getInteger("cpu"));
            Assert.assertEquals(Integer.valueOf(4), createBody.getInteger("memory"));
            Assert.assertEquals("unit", createBody.getJSONObject("labels").getString("task"));
            Assert.assertEquals("GET", server.record(3).method);
            Assert.assertEquals("/sandbox/sandbox-created/toolbox-proxy-url", server.record(3).path);
            Assert.assertEquals("DELETE", server.record(5).method);
            Assert.assertEquals("/sandbox/sandbox-created", server.record(5).path);
        } finally {
            server.close();
        }
    }

    @Test
    public void createSessionShouldNotDeleteCreatedSandboxWhenDeleteOnCloseIsFalse() throws Exception {
        DaytonaLocalHttpServer server = DaytonaLocalHttpServer.start();
        try {
            server.enqueue(404, "{\"message\":\"missing\"}")
                    .enqueue(200, sandbox("sandbox-kept", "workspace-kept", "started", server.baseUrl() + "/toolbox"))
                    .enqueue(200, "{\"exitCode\":0,\"result\":\"kept-ok\"}");

            DaytonaSandboxProvider provider = new DaytonaSandboxProvider(config(server, "workspace-kept", false, true));
            SandboxSession session = provider.createSession(SandboxSpec.builder()
                    .providerId("daytona")
                    .workspaceId("workspace-kept")
                    .build());

            SandboxResult result = session.execute(SandboxCommand.builder()
                    .commandId("cmd-keep")
                    .command("echo keep")
                    .stdin("keep-input")
                    .build());
            session.close();

            Assert.assertEquals("sandbox-kept", session.getSessionId());
            Assert.assertEquals(Integer.valueOf(0), result.getExitCode());
            Assert.assertEquals("kept-ok", result.getStdout());
            Assert.assertEquals("GET", server.record(0).method);
            Assert.assertEquals("/sandbox/workspace-kept", server.record(0).path);
            Assert.assertEquals("POST", server.record(1).method);
            Assert.assertEquals("/sandbox", server.record(1).path);
            Assert.assertEquals("POST", server.record(2).method);
            Assert.assertEquals("/toolbox/sandbox-kept/process/execute", server.record(2).path);
            Assert.assertTrue(server.record(2).body.contains("\"stdin\":\"keep-input\""));
            Assert.assertFalse("created sandbox should not be deleted when deleteOnClose is false",
                    containsPath(server.records(), "/sandbox/sandbox-kept"));
        } finally {
            server.close();
        }
    }

    @Test
    public void providerShouldReadConfigFromSpecAndEnvironmentMap() {
        Map<String, String> env = new LinkedHashMap<String, String>();
        env.put("DAYTONA_API_KEY", "env-key");
        env.put("DAYTONA_API_URL", "http://daytona.local/api/");
        env.put("DAYTONA_TOOLBOX_PROXY_URL", "http://toolbox.local/");
        env.put("DAYTONA_ORGANIZATION_ID", "org-1");
        env.put("DAYTONA_TARGET", "us");

        SandboxSpec spec = SandboxSpec.builder()
                .providerId("daytona")
                .workspaceId("ws")
                .image("snap")
                .config("apiKey", "spec-key")
                .config("sandboxId", "sandbox-id")
                .config("deleteOnClose", "true")
                .config("connectTimeoutMillis", "1234")
                .build();

        DaytonaSandboxConfig config = DaytonaSandboxConfig.fromEnvironment(spec, env);

        Assert.assertEquals("daytona", config.getProviderId());
        Assert.assertEquals("spec-key", config.getApiKey());
        Assert.assertEquals("http://daytona.local/api", config.getApiUrl());
        Assert.assertEquals("http://toolbox.local", config.getToolboxProxyUrl());
        Assert.assertEquals("org-1", config.getOrganizationId());
        Assert.assertEquals("us", config.getTarget());
        Assert.assertEquals("snap", config.getSnapshot());
        Assert.assertEquals("sandbox-id", config.getSandboxId());
        Assert.assertEquals("ws", config.getSandboxName());
        Assert.assertTrue(config.isDeleteOnClose());
        Assert.assertEquals(1234L, config.getConnectTimeoutMillis());

        env.remove("DAYTONA_API_URL");
        DaytonaSandboxConfig defaultUrlConfig = DaytonaSandboxConfig.fromEnvironment(
                SandboxSpec.builder().providerId("daytona").build(),
                env);
        Assert.assertEquals(DaytonaSandboxConfig.DEFAULT_API_URL, defaultUrlConfig.getApiUrl());
    }

    private static DaytonaSandboxConfig config(DaytonaLocalHttpServer server,
                                               String sandboxName,
                                               boolean deleteOnClose,
                                               boolean createIfMissing) {
        return DaytonaSandboxConfig.builder()
                .apiKey("unit-key")
                .apiUrl(server.baseUrl())
                .organizationId("org-unit")
                .sandboxName(sandboxName)
                .createIfMissing(Boolean.valueOf(createIfMissing))
                .deleteOnClose(Boolean.valueOf(deleteOnClose))
                .connectTimeoutMillis(Long.valueOf(3000L))
                .readTimeoutMillis(Long.valueOf(3000L))
                .startTimeoutMillis(Long.valueOf(3000L))
                .pollIntervalMillis(Long.valueOf(1L))
                .build();
    }

    private static String sandbox(String id, String name, String state, String toolboxProxyUrl) {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("state", state);
        object.put("snapshot", "ubuntu");
        object.put("user", "daytona");
        object.put("target", "unit");
        if (toolboxProxyUrl != null) {
            object.put("toolboxProxyUrl", toolboxProxyUrl);
        }
        JSONObject labels = new JSONObject();
        labels.put("owner", "ai4j");
        object.put("labels", labels);
        return object.toJSONString();
    }

    private static boolean containsPath(Iterable<DaytonaLocalHttpServer.RequestRecord> records, String path) {
        for (DaytonaLocalHttpServer.RequestRecord record : records) {
            if (path.equals(record.path) && "DELETE".equals(record.method)) {
                return true;
            }
        }
        return false;
    }
}
