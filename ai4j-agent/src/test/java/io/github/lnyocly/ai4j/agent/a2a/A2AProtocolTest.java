package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A2A 1.0协议规范测试 - 验证序列化/反序列化和协议兼容性
 */
public class A2AProtocolTest {

    @Test
    public void testAgentCardSerialization() {
        AgentCard card = new AgentCard();
        card.setName("test-agent");
        card.setDescription("Test description");
        card.setVersion("1.0");
        card.setUrl("http://localhost:8080");
        card.setProtocol("a2a/1.0");
        card.setAgentUri("agent://test-agent");

        String json = JSON.toJSONString(card);
        assertTrue(json.contains("\"protocol\":\"a2a/1.0\""));
        assertTrue(json.contains("\"agentUri\":\"agent://test-agent\""));
    }

    @Test
    public void testAgentCardWithOptionalFields() {
        AgentCard card = new AgentCard();
        card.setName("full-agent");
        card.setDescription("Full featured agent");
        card.setVersion("2.0");
        card.setUrl("http://example.com");
        card.setProtocol("a2a/1.0");
        card.setAgentUri("agent://full-agent");

        // Capabilities
        card.withCapability("search");
        card.withCapability("analysis");

        // Skills
        card.withSkill("code-review", "Review code quality");
        card.withSkill("debug", "Debug issues");

        // Endpoints
        card.withEndpoint("search", "POST /tasks/send");

        // Authentication
        card.setAuthentication(new AgentCard.Authentication());
        card.getAuthentication().setType("api-key");
        card.getAuthentication().setHeader("X-API-Key");
        card.getAuthentication().setObtainAt("http://example.com/get-key");

        String json = JSON.toJSONString(card);

        assertTrue(json.contains("\"capabilities\""));
        assertTrue(json.contains("\"skills\""));
        assertTrue(json.contains("\"authentication\""));
        assertTrue(json.contains("\"endpoints\""));
    }

    @Test
    public void testTaskStateEnum() {
        assertEquals("working", TaskState.WORKING.getValue());
        assertEquals("completed", TaskState.COMPLETED.getValue());
        assertEquals("failed", TaskState.FAILED.getValue());
        assertEquals("canceled", TaskState.CANCELED.getValue());

        // Test state parsing
        assertEquals(TaskState.WORKING, TaskState.fromValue("working"));
        assertEquals(TaskState.COMPLETED, TaskState.fromValue("completed"));
        assertEquals(TaskState.FAILED, TaskState.fromValue("failed"));
        assertEquals(TaskState.CANCELED, TaskState.fromValue("canceled"));

        // Test case insensitivity
        assertEquals(TaskState.WORKING, TaskState.fromValue("WORKING"));
        assertEquals(TaskState.COMPLETED, TaskState.fromValue("COMPLETED"));

        // Test invalid value
        assertNull(TaskState.fromValue("invalid"));
        assertNull(TaskState.fromValue(null));
    }

    @Test
    public void testTaskStateProperties() {
        assertTrue(TaskState.COMPLETED.isTerminal());
        assertTrue(TaskState.FAILED.isTerminal());
        assertTrue(TaskState.CANCELED.isTerminal());
        assertFalse(TaskState.WORKING.isTerminal());

        assertTrue(TaskState.COMPLETED.isSuccess());
        assertFalse(TaskState.WORKING.isSuccess());

        assertTrue(TaskState.FAILED.isFailure());
        assertTrue(TaskState.CANCELED.isFailure());
        assertFalse(TaskState.COMPLETED.isFailure());
    }

    @Test
    public void testA2AErrorCreation() {
        A2AError error = A2AError.invalidRequest("Invalid parameter");
        assertEquals(A2AError.INVALID_REQUEST, error.getCode());
        assertEquals("Invalid parameter", error.getMessage());

        error = A2AError.authenticationFailed("Bad API key");
        assertEquals(A2AError.AUTHENTICATION_FAILED, error.getCode());
        assertEquals("Bad API key", error.getMessage());

        error = A2AError.taskNotFound("Task not found");
        assertEquals(A2AError.TASK_NOT_FOUND, error.getCode());

        error = A2AError.internalError("Server error");
        assertEquals(A2AError.INTERNAL_ERROR, error.getCode());
    }

    @Test
    public void testA2ASkillCreation() {
        A2ASkill skill = new A2ASkill("code-review", "Review code quality");
        assertEquals("code-review", skill.getName());
        assertEquals("Review code quality", skill.getDescription());
        assertNull(skill.getInputSchema());

        // Skill with input schema
        Map<String, Object> code = new HashMap<String, Object>();
        code.put("type", "string");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("code", code);
        Map<String, Object> schema = new HashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        skill.setInputSchema(schema);
        assertNotNull(skill.getInputSchema());
        assertEquals(schema, skill.getInputSchema());
    }

    @Test
    public void testAgentCardBackwardCompatibility() {
        AgentCard card = new AgentCard();

        // Set old field (protocolVersion)
        card.setProtocolVersion("1.0");

        // Should auto-sync to new field
        assertEquals("a2a/1.0", card.getProtocol());
        assertEquals("1.0", card.getProtocolVersion());
    }

    @Test
    public void testAgentCardEmptyCollections() {
        AgentCard card = new AgentCard();

        assertNotNull(card.getCapabilities());
        assertNotNull(card.getSkills());
        assertNotNull(card.getEndpoints());

        assertTrue(card.getCapabilities().isEmpty());
        assertTrue(card.getSkills().isEmpty());
        assertTrue(card.getEndpoints().isEmpty());
    }

    @Test
    public void testAgentCardNullSafeSetters() {
        AgentCard card = new AgentCard();

        // Test null-safe operations
        card.setCapabilities(null);
        assertNotNull(card.getCapabilities());
        assertTrue(card.getCapabilities().isEmpty());

        card.setSkills(null);
        assertNotNull(card.getSkills());
        assertTrue(card.getSkills().isEmpty());

        card.setEndpoints(null);
        assertNotNull(card.getEndpoints());
        assertTrue(card.getEndpoints().isEmpty());
    }
}