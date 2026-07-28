package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Tests {@link A2AServer} — an ai4j agent exposed as an A2A HTTP service, called by {@link A2AClient}. */
public class A2AServerTest {

    private A2AServer server;
    private HttpServer callbackServer;

    @After
    public void stop() {
        if (server != null) {
            server.close();
        }
        if (callbackServer != null) {
            callbackServer.stop(0);
        }
    }

    @Test
    public void serverRespondsToDiscoverAndSendTask() throws Exception {
        // fake agent that always says "bonjour"
        Agent agent = Agents.react()
                .modelClient(new FixedModelClient("server agent says: bonjour"))
                .model("test-model")
                .build();
        server = new A2AServer(agent, 0, "test-server", "a test agent");

        A2AClient client = new A2AClient();
        AgentCard card = client.discover(server.getBaseUrl());
        assertEquals("test-server", card.getName());

        String response = client.sendTask(server.getBaseUrl(), "hello");
        assertTrue("server must relay the agent's response: " + response, response.contains("bonjour"));
    }

    @Test
    public void serverPreservesTaskIdAndReturnsStructuredErrors() throws Exception {
        Agent agent = Agents.react()
                .modelClient(new FixedModelClient("ok"))
                .model("test-model")
                .build();
        server = new A2AServer(agent, 0, "test-server", "a test agent");

        String request = JSON.toJSONString(A2AClient.buildTaskPayload("hello"));
        JSONObject requestJson = JSON.parseObject(request);
        String expectedTaskId = requestJson.getJSONObject("params").getString("id");

        HttpURLConnection connection = (HttpURLConnection) new URL(server.getBaseUrl() + "/tasks/send")
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        OutputStream output = connection.getOutputStream();
        try {
            output.write(request.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
        assertEquals(200, connection.getResponseCode());
        JSONObject response = JSON.parseObject(readBody(connection.getInputStream()));
        assertEquals(expectedTaskId, response.getJSONObject("result").getString("id"));
        connection.disconnect();

        connection = (HttpURLConnection) new URL(server.getBaseUrl() + "/tasks/send")
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        output = connection.getOutputStream();
        try {
            output.write("{".getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
        assertEquals(400, connection.getResponseCode());
        response = JSON.parseObject(readBody(connection.getErrorStream()));
        assertEquals("2.0", response.getString("jsonrpc"));
        assertEquals(A2AError.INVALID_REQUEST, response.getJSONObject("error").getString("code"));
        connection.disconnect();
    }

    @Test
    public void asyncTasksCanBeQueriedListedAndCanceled() throws Exception {
        GatedModelClient model = new GatedModelClient("late reply");
        server = new A2AServer(Agents.react().modelClient(model).model("test-model").build(), 0,
            "async-server", "an async test agent");
        A2AClient client = new A2AClient();

        JSONObject result = client.sendTaskResponse(server.getBaseUrl(), "wait", true);
        String taskId = result.getJSONObject("task").getString("id");
        assertTrue("agent task did not start", model.awaitStarted());
        assertEquals("TASK_STATE_WORKING", client.getTask(server.getBaseUrl(), taskId)
            .getJSONObject("status").getString("state"));

        JSONArray tasks = client.listTasks(server.getBaseUrl()).getJSONArray("tasks");
        assertTrue("task missing from ListTasks", containsTask(tasks, taskId));

        assertEquals("TASK_STATE_CANCELED", client.cancelTask(server.getBaseUrl(), taskId)
            .getJSONObject("status").getString("state"));
        model.release();
        assertEquals("TASK_STATE_CANCELED", client.getTask(server.getBaseUrl(), taskId)
            .getJSONObject("status").getString("state"));
    }

    @Test
    public void streamingReturnsTaskStatusAndArtifactEvents() throws Exception {
        server = new A2AServer(fixedAgent("stream reply"), 0, "stream-server", "a stream test agent");
        final List<JSONObject> events = new ArrayList<JSONObject>();

        new A2AClient().sendStreamingTask(server.getBaseUrl(), "stream", events::add);

        boolean sawTask = false;
        boolean sawCompleted = false;
        boolean sawArtifact = false;
        for (JSONObject event : events) {
            JSONObject task = event.getJSONObject("task");
            if (task != null && task.containsKey("id") && task.containsKey("status")) {
                sawTask = true;
            }
            JSONObject statusUpdate = event.getJSONObject("statusUpdate");
            if (statusUpdate != null && "TASK_STATE_COMPLETED".equals(statusUpdate
                .getJSONObject("status").getString("state"))) {
                sawCompleted = true;
            }
            JSONObject artifactUpdate = event.getJSONObject("artifactUpdate");
            if (artifactUpdate != null && "stream reply".equals(artifactUpdate.getJSONObject("artifact")
                .getJSONArray("parts").getJSONObject(0).getString("text"))) {
                sawArtifact = true;
            }
        }
        assertTrue("first streaming event must include a Task: " + events, sawTask);
        assertTrue("stream must include terminal status: " + events, sawCompleted);
        assertTrue("stream must include the artifact update: " + events, sawArtifact);
    }

    @Test
    public void subscriptionReturnsInitialTaskArtifactAndTerminalStatus() throws Exception {
        GatedModelClient model = new GatedModelClient("subscription reply");
        server = new A2AServer(Agents.react().modelClient(model).model("test-model").build(), 0,
            "subscription-server", "a subscription test agent");
        final A2AClient client = new A2AClient();
        String taskId = client.sendTaskResponse(server.getBaseUrl(), "wait", true)
            .getJSONObject("task").getString("id");
        assertTrue("agent task did not start", model.awaitStarted());

        final CountDownLatch initialTask = new CountDownLatch(1);
        final CountDownLatch artifact = new CountDownLatch(1);
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<Exception> failure = new AtomicReference<Exception>();
        Thread subscriber = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.subscribeToTask(server.getBaseUrl(), taskId, event -> {
                        if (event.getJSONObject("task") != null) {
                            initialTask.countDown();
                        }
                        JSONObject artifactUpdate = event.getJSONObject("artifactUpdate");
                        if (artifactUpdate != null && artifactUpdate.getJSONObject("artifact").toJSONString()
                            .contains("subscription reply")) {
                            artifact.countDown();
                        }
                        JSONObject statusUpdate = event.getJSONObject("statusUpdate");
                        if (statusUpdate != null && "TASK_STATE_COMPLETED".equals(statusUpdate
                            .getJSONObject("status").getString("state"))) {
                            completed.countDown();
                        }
                    });
                } catch (Exception e) {
                    failure.set(e);
                }
            }
        });
        subscriber.start();
        assertTrue("subscription did not receive its initial task", initialTask.await(5, TimeUnit.SECONDS));
        model.release();
        assertTrue("subscription did not receive the artifact", artifact.await(5, TimeUnit.SECONDS));
        assertTrue("subscription did not receive terminal status", completed.await(5, TimeUnit.SECONDS));
        subscriber.join(5000);
        assertFalse("subscription did not close after terminal status", subscriber.isAlive());
        assertTrue("subscription failed: " + failure.get(), failure.get() == null);
    }

    @Test
    public void pushNotificationsRejectLoopbackByDefault() throws Exception {
        server = new A2AServer(fixedAgent("done"), 0, "push-server", "a push test agent");
        A2AClient client = new A2AClient();
        String taskId = client.sendTaskResponse(server.getBaseUrl(), "complete", false)
            .getJSONObject("task").getString("id");

        try {
            client.createTaskPushNotificationConfig(server.getBaseUrl(), taskId,
                "http://127.0.0.1:1/callback", "token");
            fail("loopback push callback was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("not allowed"));
        }
    }

    @Test
    public void pushNotificationsDoNotFollowRedirects() throws Exception {
        final CountDownLatch redirectReceived = new CountDownLatch(1);
        final CountDownLatch redirectedTargetReceived = new CountDownLatch(1);
        callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        callbackServer.createContext("/redirect", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                readBody(exchange.getRequestBody());
                exchange.getResponseHeaders().set("Location", "/target");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
                redirectReceived.countDown();
            }
        });
        callbackServer.createContext("/target", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                redirectedTargetReceived.countDown();
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
            }
        });
        callbackServer.start();

        GatedModelClient model = new GatedModelClient("late reply");
        server = new A2AServer(Agents.react().modelClient(model).model("test-model").build(), 0,
            "push-server", "a push test agent").withPushNotificationUrlValidator(value -> true);
        A2AClient client = new A2AClient();
        String taskId = client.sendTaskResponse(server.getBaseUrl(), "wait", true)
            .getJSONObject("task").getString("id");
        assertTrue("agent task did not start", model.awaitStarted());

        String callbackUrl = "http://127.0.0.1:" + callbackServer.getAddress().getPort() + "/redirect";
        client.createTaskPushNotificationConfig(server.getBaseUrl(), taskId, callbackUrl, null);
        client.cancelTask(server.getBaseUrl(), taskId);
        model.release();

        assertTrue("push callback did not reach the redirect endpoint", redirectReceived.await(5, TimeUnit.SECONDS));
        assertFalse("push callback followed its redirect", redirectedTargetReceived.await(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void pushNotificationConfigurationCanBeManagedAndDeliveredWhenExplicitlyAllowed() throws Exception {
        final CountDownLatch delivered = new CountDownLatch(1);
        final AtomicReference<String> token = new AtomicReference<String>();
        final AtomicReference<String> authorization = new AtomicReference<String>();
        final AtomicReference<String> body = new AtomicReference<String>();
        final AtomicInteger callbackValidationCount = new AtomicInteger();
        callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        callbackServer.createContext("/callback", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                token.set(exchange.getRequestHeaders().getFirst("X-A2A-Notification-Token"));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                body.set(readBody(exchange.getRequestBody()));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                delivered.countDown();
            }
        });
        callbackServer.start();

        GatedModelClient model = new GatedModelClient("late reply");
        server = new A2AServer(Agents.react().modelClient(model).model("test-model").build(), 0,
            "push-server", "a push test agent").withPushNotificationUrlValidator(value -> {
                callbackValidationCount.incrementAndGet();
                return true;
            });
        A2AClient client = new A2AClient();
        String taskId = client.sendTaskResponse(server.getBaseUrl(), "wait", true)
            .getJSONObject("task").getString("id");
        assertTrue("agent task did not start", model.awaitStarted());

        String callbackUrl = "http://127.0.0.1:" + callbackServer.getAddress().getPort() + "/callback";
        Map<String, Object> incompleteAuthentication = new LinkedHashMap<String, Object>();
        incompleteAuthentication.put("scheme", "Bearer");
        try {
            client.createTaskPushNotificationConfig(server.getBaseUrl(), taskId, callbackUrl, null,
                incompleteAuthentication);
            fail("push authentication without credentials was accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("requires scheme and credentials"));
        }
        Map<String, Object> authentication = new LinkedHashMap<String, Object>();
        authentication.put("scheme", "Bearer");
        authentication.put("credentials", "callback-secret");
        JSONObject config = client.createTaskPushNotificationConfig(server.getBaseUrl(), taskId, callbackUrl,
            "notification-token", authentication);
        assertEquals(taskId, config.getString("taskId"));
        assertEquals(callbackUrl, config.getString("url"));
        assertFalse("push token must be write-only", config.containsKey("token"));
        assertFalse("push credentials must be write-only", config.getJSONObject("authentication")
            .containsKey("credentials"));
        JSONObject fetched = client.getTaskPushNotificationConfig(server.getBaseUrl(), taskId,
            config.getString("id"));
        assertEquals(config.getString("id"), fetched.getString("id"));
        assertFalse("fetched push token must be write-only", fetched.containsKey("token"));
        assertFalse("fetched push credentials must be write-only", fetched.getJSONObject("authentication")
            .containsKey("credentials"));
        JSONArray configurations = client.listTaskPushNotificationConfigs(server.getBaseUrl(), taskId)
            .getJSONArray("configs");
        assertEquals(1, configurations.size());
        assertFalse("listed push token must be write-only", configurations.getJSONObject(0).containsKey("token"));
        assertFalse("listed push credentials must be write-only", configurations.getJSONObject(0)
            .getJSONObject("authentication").containsKey("credentials"));

        client.cancelTask(server.getBaseUrl(), taskId);
        model.release();
        assertTrue("canceled task did not deliver push notification", delivered.await(5, TimeUnit.SECONDS));
        assertEquals("notification-token", token.get());
        assertEquals("Bearer callback-secret", authorization.get());
        assertTrue(body.get(), body.get().contains("statusUpdate"));
        assertTrue("push URL was not revalidated before delivery", callbackValidationCount.get() >= 2);

        client.deleteTaskPushNotificationConfig(server.getBaseUrl(), taskId, config.getString("id"));
        assertEquals(0, client.listTaskPushNotificationConfigs(server.getBaseUrl(), taskId)
            .getJSONArray("configs").size());
    }

    @Test
    public void pushNotificationConfigurationsAreBoundedPerTask() throws Exception {
        server = new A2AServer(fixedAgent("done"), 0, "push-capacity-server", "a push capacity test")
            .withPushNotificationUrlValidator(value -> true);
        A2AClient client = new A2AClient();
        String taskId = client.sendTaskResponse(server.getBaseUrl(), "complete", false)
            .getJSONObject("task").getString("id");

        for (int index = 0; index < 32; index++) {
            JSONObject config = client.createTaskPushNotificationConfig(server.getBaseUrl(), taskId,
                "https://callback.example/" + index, null);
            assertEquals(taskId, config.getString("taskId"));
        }
        try {
            client.createTaskPushNotificationConfig(server.getBaseUrl(), taskId,
                "https://callback.example/overflow", null);
            fail("unbounded push configurations were accepted");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("capacity reached"));
        }
    }

    @Test
    public void clientUsesAdvertisedApiKeyAndBearerSecurity() throws Exception {
        try (A2AServer apiKeyServer = new A2AServer(fixedAgent("api key reply"), 0,
            "api-key-server", "an authenticated test agent", "api-secret")
            .withAuthentication("api-key", "X-Test-A2A-Key", "https://example.test/keys")) {
            AgentCard card = new A2AClient().discover(apiKeyServer.getBaseUrl());
            assertTrue(card.getSecuritySchemes().containsKey("ai4jAuth"));
            assertEquals("api key reply", new A2AClient("api-secret")
                .sendTask(apiKeyServer.getBaseUrl(), "authenticate"));
        }
        try (A2AServer bearerServer = new A2AServer(fixedAgent("bearer reply"), 0,
            "bearer-server", "a bearer test agent", "bearer-secret")
            .withBearerAuthentication("https://example.test/token")) {
            assertEquals("bearer reply", A2AClient.bearerToken("bearer-secret")
                .sendTask(bearerServer.getBaseUrl(), "authenticate"));
        }
    }

    @Test
    @Category(LiveProviderTest.class)
    public void liveGlmAgentServedViaA2a() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String anthropicUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "glm-5.1");

        Agent agent = Agents.react()
                .anthropicMessages(key, anthropicUrl)
                .model(model)
                .maxOutputTokens(256)
                .build();
        server = new A2AServer(agent, 0, "ai4j-glm", "GLM via A2A");

        A2AClient client = new A2AClient();
        String response = client.sendTask(server.getBaseUrl(), "Say exactly: A2A round-trip OK");
        assertTrue("live GLM via A2A must respond: " + response,
                response != null && !response.trim().isEmpty());
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String fixedOutput;
        FixedModelClient(String fixedOutput) { this.fixedOutput = fixedOutput; }
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(fixedOutput).build();
        }
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return AgentModelResult.builder().outputText(fixedOutput).build();
        }
    }

    private static Agent fixedAgent(String output) {
        return Agents.react()
            .modelClient(new FixedModelClient(output))
            .model("test-model")
            .build();
    }

    private static boolean containsTask(JSONArray tasks, String taskId) {
        for (int index = 0; tasks != null && index < tasks.size(); index++) {
            JSONObject task = tasks.getJSONObject(index);
            if (taskId.equals(task == null ? null : task.getString("id"))) {
                return true;
            }
        }
        return false;
    }

    private static final class GatedModelClient implements AgentModelClient {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final String output;

        private GatedModelClient(String output) {
            this.output = output;
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            started.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted", e);
            }
            return AgentModelResult.builder().outputText(output).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while (input != null && (read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        if (input != null) {
            input.close();
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
