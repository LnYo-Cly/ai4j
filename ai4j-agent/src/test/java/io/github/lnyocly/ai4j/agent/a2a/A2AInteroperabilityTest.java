package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A2A 互操作性测试 - 验证A2A协议实现与外部代理的兼容性
 *
 * <p>此测试验证:</p>
 * <ul>
 *   <li>A2A 1.0规范兼容性</li>
 *   <li>序列化/反序列化正确性</li>
 *   <li>跨框架通信能力</li>
 *   <li>错误处理规范性</li>
 * </ul>
 */
public class A2AInteroperabilityTest {

    /**
     * 测试A2A 1.0 AgentCard最小结构
     */
    @Test
    public void testMinimalAgentCardStructure() {
        String minimalJson = "{\"name\":\"test\",\"description\":\"test\",\"version\":\"1.0\",\"url\":\"http://localhost\",\"protocol\":\"a2a/1.0\",\"agentUri\":\"agent://test\"}";

        AgentCard card = JSON.parseObject(minimalJson, AgentCard.class);

        assertNotNull(card);
        assertEquals("test", card.getName());
        assertEquals("test", card.getDescription());
        assertEquals("1.0", card.getVersion());
        assertEquals("http://localhost", card.getUrl());
        assertEquals("a2a/1.0", card.getProtocol());
        assertEquals("agent://test", card.getAgentUri());
    }

    /**
     * 测试A2A 1.0完整AgentCard结构
     */
    @Test
    public void testFullAgentCardStructure() {
        String fullJson = "{\"name\":\"full-agent\",\"description\":\"Full featured agent\",\"version\":\"2.0\",\"url\":\"http://example.com\",\"protocol\":\"a2a/1.0\",\"agentUri\":\"agent://full-agent\",\"capabilities\":[\"search\",\"analysis\"],\"skills\":[{\"name\":\"code-review\",\"description\":\"Review code\"}],\"authentication\":{\"type\":\"api-key\",\"header\":\"X-API-Key\",\"obtainAt\":\"http://example.com\"},\"endpoints\":{\"search\":\"POST /tasks/send\"}}";

        AgentCard card = JSON.parseObject(fullJson, AgentCard.class);

        assertNotNull(card);
        assertEquals("full-agent", card.getName());
        assertEquals(2, card.getCapabilities().size());
        assertTrue(card.getCapabilities().contains("search"));
        assertEquals(1, card.getSkills().size());
        assertEquals("code-review", card.getSkills().get(0).getName());
        assertNotNull(card.getAuthentication());
        assertEquals("api-key", card.getAuthentication().getType());
        assertEquals("POST /tasks/send", card.getEndpoints().get("search"));
    }

    /**
     * 测试A2A任务响应结构
     */
    @Test
    public void testA2ATaskResponseStructure() {
        String taskResponse = "{\"jsonrpc\":\"2.0\",\"result\":{\"id\":\"task-123\",\"status\":{\"state\":\"completed\"},\"artifacts\":[{\"parts\":[{\"type\":\"text\",\"text\":\"Response text\"}]}]},\"id\":1}";

        Map<String, Object> response = JSON.parseObject(taskResponse, Map.class);

        assertNotNull(response);
        assertEquals("2.0", response.get("jsonrpc"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertNotNull(result);
        assertEquals("task-123", result.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) result.get("status");
        assertNotNull(status);
        assertEquals("completed", status.get("state"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) result.get("artifacts");
        assertNotNull(artifacts);
        assertEquals(1, artifacts.size());
    }

    /**
     * 测试A2A错误响应结构
     */
    @Test
    public void testA2AErrorResponseStructure() {
        String errorResponse = "{\"jsonrpc\":\"2.0\",\"result\":{\"id\":\"task-123\",\"status\":{\"state\":\"failed\",\"message\":\"Task failed\"},\"artifacts\":[]},\"id\":1}";

        Map<String, Object> response = JSON.parseObject(errorResponse, Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        @SuppressWarnings("unchecked")
        Map<String, Object> status = (Map<String, Object>) result.get("status");

        assertEquals("failed", status.get("state"));
        assertEquals("Task failed", status.get("message"));
    }

    /**
     * 测试任务状态枚举兼容性
     */
    @Test
    public void testTaskStateCompatibility() {
        // 测试所有标准A2A状态值
        assertEquals(TaskState.WORKING, TaskState.fromValue("working"));
        assertEquals(TaskState.COMPLETED, TaskState.fromValue("completed"));
        assertEquals(TaskState.FAILED, TaskState.fromValue("failed"));
        assertEquals(TaskState.CANCELED, TaskState.fromValue("canceled"));

        // 测试大小写不敏感
        assertEquals(TaskState.WORKING, TaskState.fromValue("WORKING"));
        assertEquals(TaskState.COMPLETED, TaskState.fromValue("COMPLETED"));
        assertEquals(TaskState.FAILED, TaskState.fromValue("FAILED"));
        assertEquals(TaskState.CANCELED, TaskState.fromValue("CANCELED"));

        // 测试终止状态
        assertTrue(TaskState.COMPLETED.isTerminal());
        assertTrue(TaskState.FAILED.isTerminal());
        assertTrue(TaskState.CANCELED.isTerminal());
        assertFalse(TaskState.WORKING.isTerminal());
    }

    /**
     * 测试A2A协议版本字段
     */
    @Test
    public void testProtocolVersionField() {
        AgentCard card = new AgentCard();
        card.setProtocol("a2a/1.0");

        // 验证新字段
        assertEquals("a2a/1.0", card.getProtocol());

        // 测试向后兼容
        card.setProtocolVersion("1.0");
        assertEquals("a2a/1.0", card.getProtocol());
        assertEquals("1.0", card.getProtocolVersion());
    }

    /**
     * 测试A2A Agent URI格式
     */
    @Test
    public void testAgentUriFormat() {
        AgentCard card = new AgentCard();
        card.setAgentUri("agent://my-agent");

        assertEquals("agent://my-agent", card.getAgentUri());

        // 测试序列化包含agentUri
        String json = JSON.toJSONString(card);
        assertTrue(json.contains("agent://my-agent"));
    }

    /**
     * 测试A2A Skill定义结构
     */
    @Test
    public void testA2ASkillStructure() {
        String skillJson = "{\"name\":\"test-skill\",\"description\":\"Test skill description\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"input\":{\"type\":\"string\"}}}}";

        A2ASkill skill = JSON.parseObject(skillJson, A2ASkill.class);

        assertNotNull(skill);
        assertEquals("test-skill", skill.getName());
        assertEquals("Test skill description", skill.getDescription());
        assertNotNull(skill.getInputSchema());

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = skill.getInputSchema();
        assertEquals("object", schema.get("type"));
    }

    /**
     * 测试A2A认证结构
     */
    @Test
    public void testAuthenticationStructure() {
        String authJson = "{\"type\":\"api-key\",\"header\":\"X-API-Key\",\"obtainAt\":\"http://example.com/get-key\"}";

        AgentCard.Authentication auth = JSON.parseObject(authJson, AgentCard.Authentication.class);

        assertNotNull(auth);
        assertEquals("api-key", auth.getType());
        assertEquals("X-API-Key", auth.getHeader());
        assertEquals("http://example.com/get-key", auth.getObtainAt());
    }

    /**
     * 测试A2A错误码标准
     */
    @Test
    public void testStandardErrorCodes() {
        assertEquals("INVALID_REQUEST", A2AError.INVALID_REQUEST);
        assertEquals("AUTHENTICATION_FAILED", A2AError.AUTHENTICATION_FAILED);
        assertEquals("TASK_NOT_FOUND", A2AError.TASK_NOT_FOUND);
        assertEquals("INTERNAL_ERROR", A2AError.INTERNAL_ERROR);
    }

    /**
     * 测试A2A服务器配置链式API
     */
    @Test
    public void testServerConfigurationChain() throws Exception {
        Agent mockAgent = fixedAgent("mock response");
        A2AServer server = new A2AServer(mockAgent, 0, "test", "test");

        // 测试链式配置
        server
            .withCapability("test-capability")
            .withSkill("test-skill", "Test skill description")
            .withEndpoint("test-endpoint", "POST /test")
            .withAuthentication("api-key", "X-API-Key", "http://localhost");

        AgentCard card = server.getAgentCard();

        assertTrue(card.getCapabilities().contains("test-capability"));
        assertEquals("test-skill", card.getSkills().get(0).getName());
        assertEquals("POST /test", card.getEndpoints().get("test-endpoint"));
        assertEquals("api-key", card.getAuthentication().getType());

        server.close();
    }

    /**
     * 测试A2A序列化兼容性（与JSON-RPC 2.0）
     */
    @Test
    public void testJsonRpcCompatibility() {
        // 测试JSON-RPC 2.0基本结构
        String jsonRpcRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"tasks/send\",\"params\":{\"id\":\"task-123\",\"message\":{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"text\":\"test\"}]}},\"id\":1}";

        Map<String, Object> request = JSON.parseObject(jsonRpcRequest, Map.class);

        assertEquals("2.0", request.get("jsonrpc"));
        assertEquals("tasks/send", request.get("method"));
        assertNotNull(request.get("params"));
        assertEquals(1, request.get("id"));
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
