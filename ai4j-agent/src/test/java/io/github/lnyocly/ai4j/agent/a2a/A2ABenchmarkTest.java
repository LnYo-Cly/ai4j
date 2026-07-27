package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * A2A基准测试 - 简化的性能基准测试（不依赖JMH）
 *
 * <p>提供关键操作的性能基准，用于回归测试</p>
 */
public class A2ABenchmarkTest {

    private static final int WARMUP = 5000;
    private static final int ITERATIONS = 50000;

    /**
     * AgentCard序列化基准测试
     */
    @Test
    public void benchmarkAgentCardSerialization() {
        System.out.println("=== AgentCard序列化基准测试 ===");

        AgentCard card = createTypicalAgentCard();

        // 预热
        for (int i = 0; i < WARMUP; i++) {
            JSON.toJSONString(card);
        }

        // 基准测试
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            JSON.toJSONString(card);
        }
        long totalTime = System.nanoTime() - startTime;

        reportResults("序列化", ITERATIONS, totalTime);
    }

    /**
     * AgentCard反序列化基准测试
     */
    @Test
    public void benchmarkAgentCardDeserialization() {
        System.out.println("=== AgentCard反序列化基准测试 ===");

        String json = JSON.toJSONString(createTypicalAgentCard());

        // 预热
        for (int i = 0; i < WARMUP; i++) {
            JSON.parseObject(json, AgentCard.class);
        }

        // 基准测试
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            JSON.parseObject(json, AgentCard.class);
        }
        long totalTime = System.nanoTime() - startTime;

        reportResults("反序列化", ITERATIONS, totalTime);
    }

    /**
     * TaskState解析基准测试
     */
    @Test
    public void benchmarkTaskStateParsing() {
        System.out.println("=== TaskState解析基准测试 ===");

        // 预热
        for (int i = 0; i < WARMUP; i++) {
            TaskState.fromValue("completed");
        }

        // 基准测试
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            TaskState.fromValue("completed");
        }
        long totalTime = System.nanoTime() - startTime;

        reportResults("状态解析", ITERATIONS, totalTime);
    }

    /**
     * 字符串处理基准测试（trim操作）
     */
    @Test
    public void benchmarkStringProcessing() {
        System.out.println("=== 字符串处理基准测试 ===");

        String testString = "  test string with spaces  ";

        // 预热
        for (int i = 0; i < WARMUP; i++) {
            testString.trim();
            testString.toLowerCase();
        }

        // 基准测试
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            String trimmed = testString.trim();
            String lower = trimmed.toLowerCase();
        }
        long totalTime = System.nanoTime() - startTime;

        reportResults("字符串处理", ITERATIONS, totalTime);
    }

    /**
     * JSON构建基准测试
     */
    @Test
    public void benchmarkJsonConstruction() {
        System.out.println("=== JSON构建基准测试 ===");

        // 预热
        for (int i = 0; i < WARMUP; i++) {
            buildA2AResponse();
        }

        // 基准测试
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            buildA2AResponse();
        }
        long totalTime = System.nanoTime() - startTime;

        reportResults("JSON构建", ITERATIONS, totalTime);
    }

    /**
     * 集合操作基准测试
     */
    @Test
    public void benchmarkCollectionOperations() {
        System.out.println("=== 集合操作基准测试 ===");

        // 预热
        for (int i = 0; i < WARMUP; i++) {
            testListOperations();
        }

        // 基准测试
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            testListOperations();
        }
        long totalTime = System.nanoTime() - startTime;

        reportResults("集合操作", ITERATIONS, totalTime);
    }

    // === 辅助方法 ===

    private AgentCard createTypicalAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("benchmark-agent");
        card.setDescription("Agent for benchmark testing");
        card.setVersion("1.0");
        card.setUrl("http://localhost:8080");
        card.setProtocol("a2a/1.0");
        card.setAgentUri("agent://benchmark");

        // 典型配置
        card.withCapability("test-capability");
        card.withSkill("test-skill", "Test skill");

        return card;
    }

    private String buildA2AResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"jsonrpc\":\"2.0\",\"result\":{\"id\":\"test-123\",\"status\":{\"state\":\"completed\"},");
        sb.append("\"artifacts\":[{\"parts\":[{\"type\":\"text\",\"text\":\"response\"}]}]},\"id\":1}");
        return sb.toString();
    }

    private void testListOperations() {
        java.util.List<String> list = new java.util.ArrayList<String>();
        for (int i = 0; i < 10; i++) {
            list.add("item-" + i);
        }
        list.size();
        list.get(0);
    }

    private void reportResults(String operation, int iterations, long totalTimeNs) {
        double avgTimeNs = (double) totalTimeNs / iterations;
        double avgTimeMs = avgTimeNs / 1_000_000.0;
        double throughput = iterations / (totalTimeNs / 1_000_000_000.0);

        System.out.printf("操作: %s%n", operation);
        System.out.printf("迭代次数: %,d%n", iterations);
        System.out.printf("总时间: %.2f ms%n", totalTimeNs / 1_000_000.0);
        System.out.printf("平均时间: %.4f ms (%.2f ns)%n", avgTimeMs, avgTimeNs);
        System.out.printf("吞吐量: %.0f ops/sec%n", throughput);

        // 性能评估
        if (avgTimeMs < 1.0) {
            System.out.println("性能评估: 优秀 ⚡");
        } else if (avgTimeMs < 5.0) {
            System.out.println("性能评估: 良好 ✓");
        } else if (avgTimeMs < 10.0) {
            System.out.println("性能评估: 可接受 ~");
        } else {
            System.out.println("性能评估: 需要优化 ⚠️");
        }
        System.out.println();
    }

    /**
     * 性能回归测试
     */
    @Test
    public void performanceRegressionTest() {
        System.out.println("=== 性能回归测试 ===");

        // 设置性能基线（基于之前的测试结果）
        double BASELINE_SERIALIZATION_MS = 0.5;
        double BASELINE_DESERIALIZATION_MS = 0.8;
        double BASELINE_STATE_PARSING_NS = 50.0;

        // 序列化测试
        AgentCard card = createTypicalAgentCard();
        long start1 = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            JSON.toJSONString(card);
        }
        long time1 = System.nanoTime() - start1;
        double avg1 = time1 / 1_000_000.0 / 10000;

        // 反序列化测试
        String json = JSON.toJSONString(card);
        long start2 = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            JSON.parseObject(json, AgentCard.class);
        }
        long time2 = System.nanoTime() - start2;
        double avg2 = time2 / 1_000_000.0 / 10000;

        // 状态解析测试
        long start3 = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            TaskState.fromValue("completed");
        }
        long time3 = System.nanoTime() - start3;
        double avg3 = time3 / 1_000_000.0 / 10000;

        // 检查性能回归
        System.out.println("序列化性能:");
        System.out.printf("  基线: %.3f ms, 当前: %.3f ms", BASELINE_SERIALIZATION_MS, avg1);
        if (avg1 > BASELINE_SERIALIZATION_MS * 1.2) {
            System.err.println(" -> 性能退化警告! ⚠️");
        } else {
            System.out.println(" -> 正常 ✓");
        }

        System.out.println("反序列化性能:");
        System.out.printf("  基线: %.3f ms, 当前: %.3f ms", BASELINE_DESERIALIZATION_MS, avg2);
        if (avg2 > BASELINE_DESERIALIZATION_MS * 1.2) {
            System.err.println(" -> 性能退化警告! ⚠️");
        } else {
            System.out.println(" -> 正常 ✓");
        }

        System.out.println("状态解析性能:");
        System.out.printf("  基线: %.3f ms, 当前: %.3f ms", BASELINE_STATE_PARSING_NS / 1000.0, avg3);
        if (avg3 > (BASELINE_STATE_PARSING_NS / 1000.0) * 1.2) {
            System.err.println(" -> 性能退化警告! ⚠️");
        } else {
            System.out.println(" -> 正常 ✓");
        }
    }
}