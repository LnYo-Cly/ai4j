package io.github.lnyocly.ai4j.mcp.transport;

import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SseTransportTest {

    private RawSseFixture fixture;

    @After
    public void tearDown() throws Exception {
        if (fixture != null) {
            fixture.close();
            fixture = null;
        }
    }

    @Test
    public void receivesExplicitMessageEvents() throws Exception {
        fixture = new RawSseFixture(false);
        fixture.start();

        SseTransport transport = new SseTransport(fixture.getSseUrl());
        CapturingHandler handler = new CapturingHandler();
        transport.setMessageHandler(handler);

        transport.start().get(5, TimeUnit.SECONDS);
        transport.sendMessage(new McpRequest("initialize", 1L, Collections.<String, Object>emptyMap()))
                .get(5, TimeUnit.SECONDS);

        Assert.assertTrue("expected a response event", handler.messageLatch.await(5, TimeUnit.SECONDS));
        Assert.assertNull("unexpected transport error", handler.lastError);
        Assert.assertNotNull(handler.lastMessage);
        Assert.assertTrue(handler.lastMessage.isResponse());
        Assert.assertEquals(1L, ((Number) handler.lastMessage.getId()).longValue());
        Assert.assertEquals("POST", fixture.lastRequestMethod.get());
        Assert.assertTrue(fixture.lastRequestBody.get().contains("\"initialize\""));
        Assert.assertNull("fixture server failed", fixture.failure.get());

        transport.stop().get(5, TimeUnit.SECONDS);
    }

    @Test
    public void defaultsUnnamedEventsToMessageAndJoinsMultilineData() throws Exception {
        fixture = new RawSseFixture(true);
        fixture.start();

        SseTransport transport = new SseTransport(fixture.getSseUrl());
        CapturingHandler handler = new CapturingHandler();
        transport.setMessageHandler(handler);

        transport.start().get(5, TimeUnit.SECONDS);
        transport.sendMessage(new McpRequest("initialize", 2L, Collections.<String, Object>emptyMap()))
                .get(5, TimeUnit.SECONDS);

        Assert.assertTrue("expected a response event", handler.messageLatch.await(5, TimeUnit.SECONDS));
        Assert.assertNull("unexpected transport error", handler.lastError);
        Assert.assertNotNull(handler.lastMessage);
        Assert.assertTrue(handler.lastMessage.isResponse());
        Assert.assertEquals(2L, ((Number) handler.lastMessage.getId()).longValue());
        Assert.assertNotNull(handler.lastMessage.getResult());
        Assert.assertTrue(String.valueOf(handler.lastMessage.getResult()).contains("multi-line"));
        Assert.assertNull("fixture server failed", fixture.failure.get());

        transport.stop().get(5, TimeUnit.SECONDS);
    }

    private static final class CapturingHandler implements McpTransport.McpMessageHandler {

        private final CountDownLatch messageLatch = new CountDownLatch(1);
        private volatile McpMessage lastMessage;
        private volatile Throwable lastError;

        @Override
        public void handleMessage(McpMessage message) {
            this.lastMessage = message;
            messageLatch.countDown();
        }

        @Override
        public void onConnected() {
        }

        @Override
        public void onDisconnected(String reason) {
        }

        @Override
        public void onError(Throwable error) {
            this.lastError = error;
            messageLatch.countDown();
        }
    }

    private static final class RawSseFixture implements Closeable {

        private final boolean unnamedMessageEvent;
        private final ServerSocket serverSocket;
        private final AtomicReference<String> lastRequestMethod = new AtomicReference<String>();
        private final AtomicReference<String> lastRequestBody = new AtomicReference<String>();
        private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        private final CountDownLatch endpointServed = new CountDownLatch(1);

        private volatile Thread serverThread;
        private volatile Socket sseSocket;
        private volatile Socket postSocket;

        private RawSseFixture(boolean unnamedMessageEvent) throws IOException {
            this.unnamedMessageEvent = unnamedMessageEvent;
            this.serverSocket = new ServerSocket(0);
        }

        private void start() {
            serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    serve();
                }
            }, "raw-sse-fixture");
            serverThread.setDaemon(true);
            serverThread.start();
        }

        private String getSseUrl() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/sse";
        }

        private void serve() {
            try {
                sseSocket = serverSocket.accept();
                handleSseConnection(sseSocket);
            } catch (Throwable t) {
                if (!serverSocket.isClosed()) {
                    failure.compareAndSet(null, t);
                }
            }
        }

        private void handleSseConnection(Socket socket) throws Exception {
            BufferedReader requestReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = requestReader.readLine();
            if (requestLine == null || !requestLine.startsWith("GET /sse ")) {
                throw new IOException("unexpected SSE request line: " + requestLine);
            }
            drainHeaders(requestReader);

            OutputStream sseOutput = socket.getOutputStream();
            writeAscii(sseOutput,
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: keep-alive\r\n\r\n");
            writeAscii(sseOutput,
                    "event: endpoint\n" +
                    "data: /message?session_id=test-session\n\n");
            sseOutput.flush();
            endpointServed.countDown();

            postSocket = serverSocket.accept();
            handlePostConnection(postSocket, sseOutput);
        }

        private void handlePostConnection(Socket socket, OutputStream sseOutput) throws Exception {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = reader.readLine();
            if (requestLine == null || !requestLine.startsWith("POST /message?session_id=test-session ")) {
                throw new IOException("unexpected POST request line: " + requestLine);
            }
            lastRequestMethod.set(requestLine.substring(0, requestLine.indexOf(' ')));

            int contentLength = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                }
            }

            char[] body = new char[contentLength];
            int offset = 0;
            while (offset < contentLength) {
                int read = reader.read(body, offset, contentLength - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            lastRequestBody.set(new String(body, 0, offset));

            OutputStream postOutput = socket.getOutputStream();
            writeAscii(postOutput,
                    "HTTP/1.1 202 Accepted\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: 8\r\n" +
                    "Connection: close\r\n\r\n" +
                    "Accepted");
            postOutput.flush();

            try {
                if (!endpointServed.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("endpoint event was not served");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while waiting for endpoint event", e);
            }

            if (unnamedMessageEvent) {
                writeAscii(sseOutput,
                        "data: {\"jsonrpc\":\"2.0\",\"id\":2,\n" +
                        "data: \"result\":{\"value\":\"multi-line\"}}\n\n");
            } else {
                writeAscii(sseOutput,
                        "event: message\n" +
                        "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"value\":\"ok\"}}\n\n");
            }
            sseOutput.flush();
            Thread.sleep(100);
        }

        private void drainHeaders(BufferedReader reader) throws IOException {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // drain request headers
            }
        }

        private void writeAscii(OutputStream outputStream, String value) throws IOException {
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closeQuietly(postSocket);
            closeQuietly(sseSocket);
            serverSocket.close();
            if (serverThread != null) {
                try {
                    serverThread.join(TimeUnit.SECONDS.toMillis(2));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while stopping raw SSE fixture", e);
                }
            }
        }

        private void closeQuietly(Socket socket) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
