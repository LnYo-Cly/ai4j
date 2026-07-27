package io.github.lnyocly.ai4j.agent.a2a;

import java.io.IOException;

/**
 * A2A Client使用示例 - 调用外部A2A代理
 *
 * <p>此示例展示如何:</p>
 * <ul>
 *   <li>发现外部A2A代理能力</li>
 *   <li>提交任务到外部代理</li>
 *   <li>处理任务响应和错误</li>
 *   <li>使用API Key认证</li>
 * </ul>
 */
public class A2AClientExample {

    /**
     * 基础客户端示例 - 发现代理能力
     */
    public static void basicClientExample() throws IOException {
        // 1. 创建A2A客户端（无需认证）
        A2AClient client = new A2AClient();

        // 2. 发现外部代理能力
        String baseUrl = "http://external-agent.com";
        AgentCard card = client.discover(baseUrl);

        System.out.println("发现代理:");
        System.out.println("- 名称: " + card.getName());
        System.out.println("- 描述: " + card.getDescription());
        System.out.println("- 版本: " + card.getVersion());
        System.out.println("- 协议: " + card.getProtocol());
        System.out.println("- URI: " + card.getAgentUri());

        if (!card.getCapabilities().isEmpty()) {
            System.out.println("- 能力: " + card.getCapabilities());
        }

        if (!card.getSkills().isEmpty()) {
            System.out.println("- 技能数量: " + card.getSkills().size());
        }
    }

    /**
     * 提交任务示例
     */
    public static void submitTaskExample() throws IOException {
        A2AClient client = new A2AClient();
        String baseUrl = "http://external-agent.com";

        // 提交任务到外部代理
        String userMessage = "分析这段代码的性能瓶颈";
        String response = client.sendTask(baseUrl, userMessage);

        System.out.println("任务响应: " + response);
    }

    /**
     * 带认证的客户端示例
     */
    public static void authenticatedClientExample() throws IOException {
        // 创建带API Key的客户端
        String apiKey = "my-api-key";
        A2AClient client = new A2AClient(apiKey);

        String baseUrl = "http://secure-agent.com";

        // 发现代理（会自动发送API Key）
        AgentCard card = client.discover(baseUrl);

        // 检查认证要求
        if (card.getAuthentication() != null) {
            System.out.println("认证信息:");
            System.out.println("- 类型: " + card.getAuthentication().getType());
            System.out.println("- 头: " + card.getAuthentication().getHeader());
            System.out.println("- 获取地址: " + card.getAuthentication().getObtainAt());
        }

        // 提交任务（自动发送API Key）
        String response = client.sendTask(baseUrl, "执行任务");
        System.out.println("响应: " + response);
    }

    /**
     * 错误处理示例
     */
    public static void errorHandlingExample() {
        A2AClient client = new A2AClient();
        String baseUrl = "http://unavailable-agent.com";

        try {
            // 尝试发现不存在的代理
            AgentCard card = client.discover(baseUrl);
            System.out.println("代理发现成功: " + card.getName());

        } catch (IOException e) {
            System.err.println("代理发现失败: " + e.getMessage());

            // 实际应用中应该进行重试或回退处理
            // retryWithFallbackAgent();
        }
    }

    /**
     * 高级配置示例
     */
    public static void advancedConfigExample() throws IOException {
        // 创建自定义超时的客户端
        A2AClient client = new A2AClient(
            "api-key",           // API Key
            10000,              // 连接超时10秒
            120000              // 读取超时120秒
        );

        String baseUrl = "http://slow-agent.com";

        // 提交长任务
        String response = client.sendTask(baseUrl, "执行复杂分析任务");
        System.out.println("长任务响应: " + response);
    }

    /**
     * 完整工作流示例
     */
    public static void completeWorkflowExample() throws IOException {
        // 1. 创建客户端
        A2AClient client = new A2AClient("your-api-key");

        // 2. 发现代理
        String baseUrl = "http://code-analyzer.com";
        AgentCard card = client.discover(baseUrl);

        // 3. 检查代理能力
        if (!card.getCapabilities().contains("code-analysis")) {
            System.err.println("代理不支持代码分析能力");
            return;
        }

        // 4. 检查特定技能
        boolean hasPerformanceSkill = false;
        for (A2ASkill skill : card.getSkills()) {
            if ("performance-analysis".equals(skill.getName())) {
                hasPerformanceSkill = true;
                break;
            }
        }

        if (!hasPerformanceSkill) {
            System.err.println("代理缺少性能分析技能");
            return;
        }

        // 5. 提交任务
        String code = "public void slowMethod() { /* ... */ }";
        String task = "分析以下代码的性能: " + code;
        String response = client.sendTask(baseUrl, task);

        // 6. 处理响应
        System.out.println("分析结果: " + response);
    }

    /**
     * 与ai4j Agent集成示例
     */
    public static void integrationWithAi4jExample() throws IOException {
        // 假设我们有一个ai4j A2A服务器运行在本地8080端口
        String localAgentUrl = "http://localhost:8080";

        // 创建客户端调用本地Agent
        A2AClient client = new A2AClient("test-api-key");

        // 发现本地Agent
        AgentCard card = client.discover(localAgentUrl);
        System.out.println("本地Agent: " + card.getName());

        // 调用本地Agent
        String response = client.sendTask(localAgentUrl, "帮我分析这个项目");
        System.out.println("本地Agent响应: " + response);
    }

    /**
     * 多代理协作示例
     */
    public static void multiAgentCollaborationExample() throws IOException {
        A2AClient client = new A2AClient("api-key");

        // 定义多个专门的代理
        String codeAgent = "http://code-analyzer.com";
        String testAgent = "http://test-generator.com";
        String docAgent = "http://doc-writer.com";

        // 1. 代码分析
        String codeAnalysis = client.sendTask(codeAgent, "分析代码质量");
        System.out.println("代码分析: " + codeAnalysis);

        // 2. 测试生成（基于代码分析结果）
        String testRequest = "基于以下分析生成测试: " + codeAnalysis;
        String tests = client.sendTask(testAgent, testRequest);
        System.out.println("生成的测试: " + tests);

        // 3. 文档生成
        String docRequest = "为以下代码和测试编写文档: " + codeAnalysis + "\n" + tests;
        String docs = client.sendTask(docAgent, docRequest);
        System.out.println("生成的文档: " + docs);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== A2A客户端使用示例 ===\n");

        System.out.println("1. 基础客户端:");
        try {
            basicClientExample();
        } catch (IOException e) {
            System.out.println("   (需要实际运行的A2A服务器)");
        }
        System.out.println();

        System.out.println("2. 带认证的客户端:");
        try {
            authenticatedClientExample();
        } catch (IOException e) {
            System.out.println("   (需要实际运行的A2A服务器)");
        }
        System.out.println();

        System.out.println("3. 错误处理:");
        errorHandlingExample();
        System.out.println();

        System.out.println("4. 完整工作流:");
        try {
            completeWorkflowExample();
        } catch (IOException e) {
            System.out.println("   (需要实际运行的A2A服务器)");
        }
        System.out.println();

        System.out.println("5. 多代理协作:");
        try {
            multiAgentCollaborationExample();
        } catch (IOException e) {
            System.out.println("   (需要实际运行的A2A服务器)");
        }
    }
}