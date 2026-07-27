package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/**
 * A2A互操作性测试 - 与外部A2A Agent的互操作验证
 *
 * <p>此测试验证ai4j A2A实现与外部框架的兼容性</p>
 *
 * <p>测试环境要求:</p>
 * <ul>
 *   <li>可选: 启动参考A2A服务器 (Node.js实现)</li>
 *   <li>或者使用模拟的外部Agent进行测试</li>
 * </ul>
 */
public class A2AInteropTest {

    private static final String TEST_AGENT_URL = System.getProperty("a2a.interop.url",
        System.getenv().getOrDefault("A2A_INTEROP_URL", "http://127.0.0.1:31337"));
    private static final String TEST_API_KEY = System.getProperty("a2a.interop.apiKey",
        System.getenv().getOrDefault("A2A_INTEROP_API_KEY", "test-api-key"));

    /**
     * 测试与外部A2A Agent的AgentCard发现
     */
    @Test
    public void testExternalAgentCardDiscovery() {
        System.out.println("=== 外部AgentCard发现测试 ===");

        // 注意：此测试需要外部Agent运行，如果不可用则跳过
        try {
            A2AClient client = new A2AClient(TEST_API_KEY);
            AgentCard card = client.discover(TEST_AGENT_URL);

            assertNotNull(card);
            assertNotNull(card.getName());
            assertNotNull(card.getProtocol());

            System.out.println("发现外部Agent:");
            System.out.println("  名称: " + card.getName());
            System.out.println("  描述: " + card.getDescription());
            System.out.println("  协议: " + card.getProtocol());
            System.out.println("  URI: " + card.getAgentUri());

            // 验证A2A 1.0兼容性
            assertTrue(card.getProtocol().startsWith("a2a/"));

        } catch (IOException e) {
            assumeNoException("skip: external A2A agent unavailable", e);
        }
    }

    /**
     * 测试与外部A2A Agent的任务通信
     */
    @Test
    public void testExternalAgentTaskCommunication() {
        System.out.println("=== 外部Agent任务通信测试 ===");

        try {
            A2AClient client = new A2AClient(TEST_API_KEY);

            // 发送简单任务
            String taskMessage = "Say hello in JSON format";
            String response = client.sendTask(TEST_AGENT_URL, taskMessage);

            assertNotNull(response);
            assertFalse(response.isEmpty());

            System.out.println("收到外部Agent响应:");
            System.out.println("  内容: " + response);

        } catch (IOException e) {
            assumeNoException("skip: external A2A agent unavailable", e);
        }
    }

    /**
     * 测试与外部A2A Agent的错误处理兼容性
     */
    @Test
    public void testExternalAgentErrorHandling() {
        System.out.println("=== 外部Agent错误处理测试 ===");

        try {
            new A2AClient(TEST_API_KEY).discover(TEST_AGENT_URL);
        } catch (IOException e) {
            assumeNoException("skip: external A2A agent unavailable", e);
        }

        try {
            A2AClient client = new A2AClient("invalid-api-key");
            client.sendTask(TEST_AGENT_URL, "auth test");
            fail("外部Agent没有拒绝无效API Key");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("401") || e.getMessage().contains(A2AError.AUTHENTICATION_FAILED));
            System.out.println("认证错误正确处理: " + e.getMessage());
        }
    }

    /**
     * 测试AgentCard结构兼容性（使用模拟数据）
     */
    @Test
    public void testAgentCardStructureCompatibility() {
        System.out.println("=== AgentCard结构兼容性测试 ===");

        // 模拟外部Agent的AgentCard（参考A2A规范）
        String externalCardJson = "{"
            + "\"name\":\"external-agent\","
            + "\"description\":\"External A2A Agent\","
            + "\"version\":\"1.0.0\","
            + "\"url\":\"http://localhost:3000\","
            + "\"protocol\":\"a2a/1.0\","
            + "\"agentUri\":\"agent://external\","
            + "\"capabilities\":[\"chat\",\"analysis\"],"
            + "\"skills\":[{\"name\":\"conversation\",\"description\":\"Natural language conversation\"}],"
            + "\"authentication\":{\"type\":\"api-key\",\"header\":\"X-API-Key\",\"obtainAt\":\"http://localhost:3000/register\"},"
            + "\"endpoints\":{\"chat\":\"POST /tasks/send\"}"
            + "}";

        // 测试ai4j能否解析外部AgentCard
        AgentCard card = JSON.parseObject(externalCardJson, AgentCard.class);

        assertNotNull(card);
        assertEquals("external-agent", card.getName());
        assertEquals("a2a/1.0", card.getProtocol());
        assertEquals("agent://external", card.getAgentUri());
        assertEquals(2, card.getCapabilities().size());
        assertEquals(1, card.getSkills().size());
        assertNotNull(card.getAuthentication());
        assertEquals("api-key", card.getAuthentication().getType());

        System.out.println("✓ ai4j成功解析外部AgentCard结构");
    }

    /**
     * 测试任务响应格式兼容性
     */
    @Test
    public void testTaskResponseFormatCompatibility() {
        System.out.println("=== 任务响应格式兼容性测试 ===");

        // 模拟外部Agent的任务响应
        String externalResponse = "{"
            + "\"jsonrpc\":\"2.0\","
            + "\"result\":{"
            + "  \"id\":\"task-123\","
            + "  \"status\":{"
            + "    \"state\":\"completed\","
            + "    \"message\":\"Task completed successfully\""
            + "  },"
            + "  \"artifacts\":["
            + "    {"
            + "      \"parts\":["
            + "        {\"type\":\"text\",\"text\":\"Hello from external agent!\"}"
            + "      ]"
            + "    }"
            + "  ]"
            + "},"
            + "\"id\":1"
            + "}";

        // 测试ai4j能否解析外部响应
        Map<String, Object> response = JSON.parseObject(externalResponse, Map.class);

        assertNotNull(response);
        assertEquals("2.0", response.get("jsonrpc"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) result.get("status");
        assertEquals("completed", status.get("state"));

        System.out.println("✓ ai4j成功解析外部任务响应格式");
    }

    /**
     * 测试错误响应格式兼容性
     */
    @Test
    public void testErrorResponseFormatCompatibility() {
        System.out.println("=== 错误响应格式兼容性测试 ===");

        // 模拟外部Agent的错误响应
        String externalError = "{"
            + "\"jsonrpc\":\"2.0\","
            + "\"result\":{"
            + "  \"id\":\"task-456\","
            + "  \"status\":{"
            + "    \"state\":\"failed\","
            + "    \"message\":\"Task failed due to invalid input\""
            + "  },"
            + "  \"artifacts\":[]"
            + "},"
            + "\"id\":1"
            + "}";

        // 测试ai4j能否解析错误响应
        Map<String, Object> response = JSON.parseObject(externalError, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) result.get("status");

        assertEquals("failed", status.get("state"));

        // 验证TaskState解析
        TaskState state = TaskState.fromValue((String) status.get("state"));
        assertEquals(TaskState.FAILED, state);
        assertTrue(state.isFailure());
        assertTrue(state.isTerminal());

        System.out.println("✓ ai4j成功解析外部错误响应格式");
    }

    /**
     * 测试JSON-RPC 2.0兼容性
     */
    @Test
    public void testJsonRpcCompatibility() {
        System.out.println("=== JSON-RPC 2.0兼容性测试 ===");

        // 模拟外部Agent的JSON-RPC请求
        String externalRequest = "{"
            + "\"jsonrpc\":\"2.0\","
            + "\"method\":\"tasks/send\","
            + "\"params\":{"
            + "  \"id\":\"ext-task-1\","
            + "  \"message\":{"
            + "    \"role\":\"user\","
            + "    \"parts\":["
            + "      {\"type\":\"text\",\"text\":\"Hello from external client\"}"
            + "    ]"
            + "  }"
            + "},"
            + "\"id\":42"
            + "}";

        // 测试ai4j能否解析JSON-RPC请求
        Map<String, Object> request = JSON.parseObject(externalRequest, Map.class);

        assertEquals("2.0", request.get("jsonrpc"));
        assertEquals("tasks/send", request.get("method"));
        assertEquals(42, request.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        assertNotNull(params);
        assertEquals("ext-task-1", params.get("id"));

        System.out.println("✓ ai4j成功解析外部JSON-RPC请求格式");
    }

    /**
     * 测试状态机兼容性
     */
    @Test
    public void testStateMachineCompatibility() {
        System.out.println("=== 状态机兼容性测试 ===");

        String[] states = {"working", "completed", "failed", "canceled"};

        for (String stateValue : states) {
            TaskState state = TaskState.fromValue(stateValue);
            assertNotNull(state);
            assertEquals(stateValue, state.getValue());
        }

        // 测试状态转换逻辑
        assertTrue(TaskState.WORKING.isTransitionValid(TaskState.COMPLETED));
        assertTrue(TaskState.WORKING.isTransitionValid(TaskState.FAILED));
        assertTrue(TaskState.WORKING.isTransitionValid(TaskState.CANCELED));

        assertFalse(TaskState.COMPLETED.isTransitionValid(TaskState.WORKING)); // 终止状态不能转换

        System.out.println("✓ 状态机与A2A规范完全兼容");
    }

    /**
     * 模拟完整的A2A通信流程
     */
    @Test
    public void testSimulatedA2ACommunicationFlow() {
        System.out.println("=== 模拟A2A通信流程测试 ===");

        try {
            // 1. 客户端发现AgentCard
            String agentCardJson = createMockAgentCard();
            AgentCard card = JSON.parseObject(agentCardJson, AgentCard.class);
            assertNotNull(card);
            System.out.println("1. ✓ AgentCard发现成功");

            // 2. 客户端提交任务
            String taskRequest = createMockTaskRequest();
            Map<String, Object> request = JSON.parseObject(taskRequest, Map.class);
            assertNotNull(request);
            System.out.println("2. ✓ 任务请求创建成功");

            // 3. 服务器处理任务（模拟）
            String taskResponse = createMockTaskResponse();
            Map<String, Object> response = JSON.parseObject(taskResponse, Map.class);
            assertNotNull(response);
            System.out.println("3. ✓ 任务响应解析成功");

            // 4. 客户端提取结果
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            @SuppressWarnings("unchecked")
            Map<String, Object> status = (Map<String, Object>) result.get("status");
            assertEquals("completed", status.get("state"));
            System.out.println("4. ✓ 任务状态验证成功");

            System.out.println("\n✓ 完整A2A通信流程模拟成功");

        } catch (Exception e) {
            fail("A2A通信流程测试失败: " + e.getMessage());
        }
    }

    // === 模拟数据创建方法 ===

    private String createMockAgentCard() {
        return "{"
            + "\"name\":\"mock-agent\","
            + "\"description\":\"Mock A2A Agent for testing\","
            + "\"version\":\"1.0\","
            + "\"url\":\"http://localhost:9999\","
            + "\"protocol\":\"a2a/1.0\","
            + "\"agentUri\":\"agent://mock\""
            + "}";
    }

    private String createMockTaskRequest() {
        return "{"
            + "\"jsonrpc\":\"2.0\","
            + "\"method\":\"tasks/send\","
            + "\"params\":{"
            + "  \"id\":\"test-task-1\","
            + "  \"message\":{"
            + "    \"role\":\"user\","
            + "    \"parts\":[{\"type\":\"text\",\"text\":\"Test message\"}]"
            + "  }"
            + "},"
            + "\"id\":1"
            + "}";
    }

    private String createMockTaskResponse() {
        return "{"
            + "\"jsonrpc\":\"2.0\","
            + "\"result\":{"
            + "  \"id\":\"test-task-1\","
            + "  \"status\":{\"state\":\"completed\"},"
            + "  \"artifacts\":[{\"parts\":[{\"type\":\"text\",\"text\":\"Test response\"}]}]"
            + "},"
            + "\"id\":1"
            + "}";
    }

}