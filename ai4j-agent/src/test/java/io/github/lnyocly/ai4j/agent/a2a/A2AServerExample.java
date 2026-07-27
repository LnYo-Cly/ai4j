package io.github.lnyocly.ai4j.agent.a2a;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;

/**
 * A2A Server使用示例 - 暴露ai4j Agent为A2A服务
 *
 * <p>此示例展示如何:</p>
 * <ul>
 *   <li>创建A2A服务器</li>
 *   <li>配置AgentCard和能力</li>
 *   <li>处理A2A任务请求</li>
 *   <li>集成ai4j Skills系统</li>
 * </ul>
 */
public class A2AServerExample {

    /**
     * 基础A2A服务器示例
     */
    public static void basicServerExample() throws Exception {
        // 1. 创建ai4j Agent
        Agent agent = createSimpleAgent();

        // 2. 启动A2A服务器
        A2AServer server = new A2AServer(
            agent,
            0,              // 0表示自动分配端口
            "code-helper",  // Agent名称
            "Java代码助手"   // 描述
        );

        System.out.println("A2A服务器已启动:");
        System.out.println("- Agent Card: " + server.getBaseUrl() + "/.well-known/agent.json");
        System.out.println("- 任务端点: " + server.getBaseUrl() + "/tasks/send");
        System.out.println("- 端口: " + server.getPort());

        // 3. 服务器现在可以接收任务请求
        // 测试: curl http://localhost:PORT/.well-known/agent.json

        // 4. 使用完毕后关闭
        // server.close();
    }

    /**
     * 完整配置的A2A服务器示例
     */
    public static void fullFeaturedServerExample() throws Exception {
        Agent agent = createSimpleAgent();

        A2AServer server = new A2AServer(
            agent,
            0,
            "full-stack-agent",
            "全栈开发助手",
            "your-api-key"  // 启用API Key认证
        );

        // 配置AgentCard
        server
            .withCapability("code-analysis")
            .withCapability("bug-detection")
            .withCapability("performance-optimization")
            .withSkill("code-review", "代码审查：检查代码质量、bug和风格")
            .withSkill("refactor", "代码重构：提供改进建议")
            .withSkill("debug", "调试：帮助定位和修复问题")
            .withEndpoint("analyze", "POST /tasks/send")
            .withEndpoint("review", "POST /tasks/send");

        System.out.println("完整A2A服务器已启动:");
        System.out.println("- 端口: " + server.getPort());
        System.out.println("- 认证: X-API-Key");

        // 测试Agent Card
        AgentCard card = server.getAgentCard();
        System.out.println("能力数量: " + card.getCapabilities().size());
        System.out.println("技能数量: " + card.getSkills().size());

        // server.close();
    }

    /**
     * 与ai4j Skills集成示例
     */
    public static void withSkillsIntegrationExample() throws Exception {
        Agent agent = createSimpleAgent();

        A2AServer server = new A2AServer(
            agent,
            0,
            "skilled-agent",
            "具备技能的代理"
        );

        // 假设我们有ai4j Skills
        // List<SkillDescriptor> ai4jSkills = Skills.discoverDefault(workspacePath).getSkills();
        // A2ASkillMapper.addSkillsToServer(server, ai4jSkills);

        // 手动添加技能
        server
            .withSkill("sql-review", "SQL查询审查和优化")
            .withSkill("api-design", "API设计建议")
            .withCapability("database");
    }

    /**
     * 带认证的服务器示例
     */
    public static void authenticatedServerExample() throws Exception {
        Agent agent = createSimpleAgent();

        // 使用API Key认证
        A2AServer server = new A2AServer(
            agent,
            0,
            "secure-agent",
            "安全的代理",
            "my-secret-api-key"  // 期望客户端提供此API Key
        );

        // 自定义认证元数据
        server.withAuthentication(
            "api-key",                    // 认证类型
            "X-API-Key",                  // HTTP头
            "https://example.com/get-key" // 获取API Key的地址
        );

        System.out.println("认证服务器已启动，需要X-API-Key头");
    }

    /**
     * 创建简单的Agent（实际使用时配置你的Agent）
     */
    private static Agent createSimpleAgent() {
        return Agents.react()
                .modelClient(new FixedModelClient("A2A example response"))
                .model("example-model")
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

    /**
     * 测试A2A端点
     */
    public static void testA2AEndpoints() throws Exception {
        A2AServer server = new A2AServer(
            createSimpleAgent(),
            0,
            "test-agent",
            "测试代理"
        );

        String baseUrl = server.getBaseUrl();

        System.out.println("测试A2A端点:");
        System.out.println("1. Agent Card:");
        System.out.println("   curl " + baseUrl + "/.well-known/agent.json");

        System.out.println("2. 提交任务:");
        System.out.println("   curl -X POST " + baseUrl + "/tasks/send \\");
        System.out.println("     -H 'Content-Type: application/json' \\");
        System.out.println("     -d '{\"jsonrpc\":\"2.0\",\"method\":\"tasks/send\",");
        System.out.println("         \"params\":{\"id\":\"test-123\",");
        System.out.println("           \"message\":{\"role\":\"user\",");
        System.out.println("             \"parts\":[{\"type\":\"text\",\"text\":\"Hello\"}]}},\"id\":1}'");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== A2A服务器使用示例 ===\n");

        System.out.println("1. 基础服务器:");
        basicServerExample();
        System.out.println();

        System.out.println("2. 完整配置服务器:");
        fullFeaturedServerExample();
        System.out.println();

        System.out.println("3. 认证服务器:");
        authenticatedServerExample();
        System.out.println();

        System.out.println("4. 测试端点:");
        testA2AEndpoints();
    }
}