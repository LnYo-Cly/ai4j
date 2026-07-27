package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * A2A性能测试 - 测试A2A实现的性能特征
 *
 * <p>测试维度:</p>
 * <ul>
 *   <li>序列化/反序列化性能</li>
 *   <li>服务器启动性能</li>
 *   <li>并发处理能力</li>
 *   <li>内存占用</li>
 * </ul>
 */
public class A2APerformanceTest {

    private static final int WARMUP_ITERATIONS = 1000;
    private static final int TEST_ITERATIONS = 10000;

    /**
     * 测试AgentCard序列化性能
     */
    @Test
    public void testAgentCardSerializationPerformance() {
        System.out.println("=== AgentCard序列化性能测试 ===");

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            AgentCard card = createFullAgentCard();
            JSON.toJSONString(card);
        }

        // 测试
        long startTime = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            AgentCard card = createFullAgentCard();
            JSON.toJSONString(card);
        }
        long endTime = System.nanoTime();

        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / TEST_ITERATIONS;
        System.out.printf("平均序列化时间: %.3f ms%n", avgTimeMs);
        System.out.printf("吞吐量: %.0f ops/sec%n", TEST_ITERATIONS / ((endTime - startTime) / 1_000_000_000.0));

        // 验证性能要求
        if (avgTimeMs > 10) {
            System.err.println("警告: 序列化时间超过10ms要求");
        }
    }

    /**
     * 测试AgentCard反序列化性能
     */
    @Test
    public void testAgentCardDeserializationPerformance() {
        System.out.println("=== AgentCard反序列化性能测试 ===");

        String json = JSON.toJSONString(createFullAgentCard());

        // 预热
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            JSON.parseObject(json, AgentCard.class);
        }

        // 测试
        long startTime = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            JSON.parseObject(json, AgentCard.class);
        }
        long endTime = System.nanoTime();

        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / TEST_ITERATIONS;
        System.out.printf("平均反序列化时间: %.3f ms%n", avgTimeMs);
        System.out.printf("吞吐量: %.0f ops/sec%n", TEST_ITERATIONS / ((endTime - startTime) / 1_000_000_000.0));

        if (avgTimeMs > 10) {
            System.err.println("警告: 反序列化时间超过10ms要求");
        }
    }

    /**
     * 测试A2AServer启动性能
     */
    @Test
    public void testServerStartupPerformance() throws IOException {
        System.out.println("=== A2AServer启动性能测试 ===");

        List<Long> startupTimes = new ArrayList<Long>();

        for (int i = 0; i < 10; i++) {
            long startTime = System.nanoTime();

            Agent agent = fixedAgent("mock response");
            A2AServer server = new A2AServer(agent, 0, "test", "test");

            long endTime = System.nanoTime();
            startupTimes.add(endTime - startTime);

            server.close();
        }

        double avgStartupMs = startupTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0) / 1_000_000.0;

        System.out.printf("平均启动时间: %.3f ms%n", avgStartupMs);

        if (avgStartupMs > 1000) {
            System.err.println("警告: 启动时间超过1秒要求");
        }
    }

    /**
     * 测试并发处理能力
     */
    @Test
    public void testConcurrentProcessingPerformance() throws IOException, InterruptedException {
        System.out.println("=== 并发处理性能测试 ===");

        final int THREAD_COUNT = 50;
        final int REQUESTS_PER_THREAD = 100;

        Agent agent = fixedAgent("mock response");
        A2AServer server = new A2AServer(agent, 0, "test", "test");
        final A2AClient client = new A2AClient();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始

                    for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
                        try {
                            String response = client.sendTask(server.getBaseUrl(), "test");
                            if (response != null && !response.trim().isEmpty()) {
                                successCount.incrementAndGet();
                            } else {
                                errorCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 统一开始
        endLatch.await();        // 等待完成

        long endTime = System.nanoTime();
        executor.shutdown();
        server.close();

        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        int totalRequests = THREAD_COUNT * REQUESTS_PER_THREAD;
        double throughput = totalRequests / (totalTimeMs / 1000.0);

        System.out.printf("总请求数: %d%n", totalRequests);
        System.out.printf("成功: %d, 失败: %d%n", successCount.get(), errorCount.get());
        System.out.printf("总耗时: %.2f ms%n", totalTimeMs);
        System.out.printf("吞吐量: %.0f req/sec%n", throughput);
        System.out.printf("错误率: %.2f%%%n", (errorCount.get() * 100.0 / totalRequests));

        if (errorCount.get() > 0) {
            System.err.println("警告: 发现处理错误");
        }
        assertEquals("A2A HTTP benchmark must complete every request", totalRequests, successCount.get());
        assertEquals("A2A HTTP benchmark must not report errors", 0, errorCount.get());
    }

    /**
     * 测试内存占用
     */
    @Test
    public void testMemoryUsage() {
        System.out.println("=== 内存占用测试 ===");

        Runtime runtime = Runtime.getRuntime();

        // 强制GC
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 创建大量AgentCard实例
        List<AgentCard> cards = new ArrayList<AgentCard>();
        for (int i = 0; i < 1000; i++) {
            cards.add(createFullAgentCard());
        }

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        double avgMemoryPerCard = memoryUsed / 1000.0;

        System.out.printf("1000个AgentCard内存占用: %.2f KB%n", memoryUsed / 1024.0);
        System.out.printf("平均每个AgentCard: %.2f bytes%n", avgMemoryPerCard);

        // 清理
        cards.clear();
        cards = null;
        System.gc();
    }

    /**
     * 测试字符串处理性能
     */
    @Test
    public void testStringProcessingPerformance() {
        System.out.println("=== 字符串处理性能测试 ===");

        String testString = "This is a test string for A2A processing performance measurement.";

        long startTime = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS * 100; i++) {
            String result = testString.toUpperCase();
            result = result.toLowerCase();
            result = result.trim();
            result.contains("test");
        }
        long endTime = System.nanoTime();

        double avgTimeNs = (endTime - startTime) / (TEST_ITERATIONS * 100.0);
        System.out.printf("平均字符串操作时间: %.2f ns%n", avgTimeNs);
    }

    // === 辅助方法 ===

    private AgentCard createFullAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("performance-test-agent");
        card.setDescription("Agent for performance testing");
        card.setVersion("1.0");
        card.setUrl("http://localhost:8080");
        card.setProtocol("a2a/1.0");
        card.setAgentUri("agent://performance-test");

        // 添加能力
        for (int i = 0; i < 10; i++) {
            card.withCapability("capability-" + i);
        }

        // 添加技能
        for (int i = 0; i < 5; i++) {
            card.withSkill("skill-" + i, "Test skill " + i);
        }

        // 添加认证
        card.setAuthentication(new AgentCard.Authentication());
        card.getAuthentication().setType("api-key");
        card.getAuthentication().setHeader("X-API-Key");
        card.getAuthentication().setObtainAt("http://localhost");

        return card;
    }

    private static Agent fixedAgent(String output) {
        return Agents.react()
                .modelClient(new FixedModelClient(output))
                .model("test-model")
                .build();
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String output;

        private FixedModelClient(String output) {
            this.output = output;
        }

        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(output).build();
        }

        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return AgentModelResult.builder().outputText(output).build();
        }
    }
}
