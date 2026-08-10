package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.memory.JdbcChatMemory;
import io.github.lnyocly.ai4j.memory.JdbcChatMemoryConfig;
import io.github.lnyocly.ai4j.memory.MessageWindowChatMemoryPolicy;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Executable source of truth for the snippet in
 * {@code docs/solutions/springboot-mysql-chat-memory.md}.
 *
 * <p>Proves the solution's core claim — the same session recovers across
 * separate {@link JdbcChatMemory} instances — using an embedded H2 database
 * (production uses MySQL with the same {@code jdbcUrl}). No key, no network.
 */
public class JdbcMemorySolutionDocTest {

    private static String h2Url(String name) {
        try {
            Path dir = Files.createTempDirectory("ai4j-jdbc-doc-");
            return "jdbc:h2:" + dir.resolve(name) + ";USER=sa;DB_CLOSE_DELAY=-1";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- 这条方案的核心价值：同一会话跨实例恢复 ----

    @Test
    public void sameSessionRecoversAcrossInstances() {
        String jdbcUrl = h2Url("recover");

        // 第一个实例（如请求 A 所在 Pod）写入会话
        JdbcChatMemory first = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("chat-1")
                .build());
        first.addSystem("You are helpful");
        first.addUser("Hello");
        first.addAssistant("Hi");

        // 第二个实例（如请求 B 所在 Pod，或重启后）用同一个 sessionId 读回
        JdbcChatMemory second = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("chat-1")
                .build());

        List<ChatMemoryItem> items = second.getItems();
        Assert.assertEquals(3, items.size());
        Assert.assertEquals("system", items.get(0).getRole());
        Assert.assertEquals("Hello", items.get(1).getText());
        Assert.assertEquals("Hi", items.get(2).getText());
    }

    // ---- 持久化 memory 同样支持窗口裁剪 ----

    @Test
    public void jdbcMemoryAppliesWindowPolicy() {
        JdbcChatMemory memory = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(h2Url("policy"))
                .sessionId("chat-2")
                .policy(new MessageWindowChatMemoryPolicy(2))
                .build());

        memory.addSystem("system");
        memory.addUser("u1");
        memory.addAssistant("a1");
        memory.addUser("u2");

        List<ChatMemoryItem> items = memory.getItems();
        Assert.assertEquals(3, items.size());
        Assert.assertEquals("system", items.get(0).getRole());
        Assert.assertEquals("u2", items.get(2).getText());
    }

    // ---- 不同 sessionId 互相隔离 ----

    @Test
    public void differentSessionsAreIsolated() {
        String jdbcUrl = h2Url("isolation");

        new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl).sessionId("alice").build())
                .addUser("alice's message");

        new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl).sessionId("bob").build())
                .addUser("bob's message");

        List<ChatMemoryItem> alice = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl).sessionId("alice").build()).getItems();
        List<ChatMemoryItem> bob = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl).sessionId("bob").build()).getItems();

        Assert.assertEquals(1, alice.size());
        Assert.assertEquals("alice's message", alice.get(0).getText());
        Assert.assertEquals(1, bob.size());
        Assert.assertEquals("bob's message", bob.get(0).getText());
    }
}
