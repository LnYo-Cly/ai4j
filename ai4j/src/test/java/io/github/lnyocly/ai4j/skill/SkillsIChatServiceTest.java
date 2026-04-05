package io.github.lnyocly.ai4j.skill;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.tool.BuiltInTools;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SkillsIChatServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldDiscoverSkillsAndAssemblePromptForBasicChatUsage() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("skills-basic-chat").toPath();
        Path skillFile = writeSkill(
                workspaceRoot.resolve(".ai4j").resolve("skills").resolve("planner").resolve("SKILL.md"),
                "---\nname: planner\ndescription: Plan implementation steps.\n---\n"
        );

        Skills.DiscoveryResult discovery = Skills.discoverDefault(workspaceRoot);
        Assert.assertEquals(1, discovery.getSkills().size());
        Assert.assertEquals("planner", discovery.getSkills().get(0).getName());
        Assert.assertEquals("workspace", discovery.getSkills().get(0).getSource());
        Assert.assertTrue(discovery.getAllowedReadRoots().contains(skillFile.getParent().getParent().toString()));

        String systemPrompt = Skills.appendAvailableSkillsPrompt("Base prompt.", discovery.getSkills());
        Assert.assertTrue(systemPrompt.contains("<available_skills>"));
        Assert.assertTrue(systemPrompt.contains(skillFile.toAbsolutePath().normalize().toString()));
        Assert.assertEquals(4, BuiltInTools.codingTools().size());
    }

    @Test
    public void shouldAllowBasicIChatServiceToMountSkillsAndBuiltInTools() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("skills-chat-request").toPath();
        Path skillFile = writeSkill(
                workspaceRoot.resolve(".ai4j").resolve("skills").resolve("reviewer").resolve("SKILL.md"),
                "# reviewer\nReview code changes for risks.\n"
        );
        Skills.DiscoveryResult discovery = Skills.discoverDefault(workspaceRoot);
        String systemPrompt = Skills.appendAvailableSkillsPrompt("You are a helpful assistant.", discovery.getSkills());

        AtomicReference<String> requestJson = new AtomicReference<String>();
        IChatService chatService = new OpenAiChatService(configurationWithJsonResponse(
                "{"
                        + "\"id\":\"resp_skill_1\","
                        + "\"object\":\"chat.completion\","
                        + "\"created\":1710000000,"
                        + "\"model\":\"gpt-test\","
                        + "\"choices\":[{"
                        + "\"index\":0,"
                        + "\"message\":{"
                        + "\"role\":\"assistant\","
                        + "\"content\":\"\","
                        + "\"tool_calls\":[{"
                        + "\"id\":\"call_skill_1\","
                        + "\"type\":\"function\","
                        + "\"function\":{"
                        + "\"name\":\"read_file\","
                        + "\"arguments\":\"{\\\"path\\\":\\\"" + escapeJson(skillFile.toAbsolutePath().normalize().toString()) + "\\\"}\""
                        + "}"
                        + "}]"
                        + "},"
                        + "\"finish_reason\":\"tool_calls\""
                        + "}],"
                        + "\"usage\":{"
                        + "\"prompt_tokens\":10,"
                        + "\"completion_tokens\":5,"
                        + "\"total_tokens\":15"
                        + "}"
                        + "}",
                requestJson
        ));

        ChatCompletion completion = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Arrays.asList(
                        ChatMessage.withSystem(systemPrompt),
                        ChatMessage.withUser("Use the most relevant installed skill.")
                ))
                .tools(BuiltInTools.codingTools())
                .build();
        completion.setPassThroughToolCalls(Boolean.TRUE);

        ChatCompletionResponse response = chatService.chatCompletion(completion);

        Assert.assertNotNull(response);
        Assert.assertEquals("tool_calls", response.getChoices().get(0).getFinishReason());
        Assert.assertEquals("read_file", response.getChoices().get(0).getMessage().getToolCalls().get(0).getFunction().getName());
        Assert.assertTrue(requestJson.get().contains("<available_skills>"));
        Assert.assertTrue(requestJson.get().contains("reviewer"));
        Assert.assertTrue(requestJson.get().contains(escapeJson(skillFile.toAbsolutePath().normalize().toString())));
        Assert.assertTrue(requestJson.get().contains("\"name\":\"read_file\""));
    }

    @Test
    public void shouldAutoInvokeBuiltInReadFileToolWhenContextConfigured() throws Exception {
        Path workspaceRoot = temporaryFolder.newFolder("skills-auto-loop").toPath();
        Path skillFile = writeSkill(
                workspaceRoot.resolve(".ai4j").resolve("skills").resolve("reviewer").resolve("SKILL.md"),
                "# reviewer\nReview code changes for risks.\n"
        );
        Skills.DiscoveryResult discovery = Skills.discoverDefault(workspaceRoot);
        String systemPrompt = Skills.appendAvailableSkillsPrompt("You are a helpful assistant.", discovery.getSkills());

        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> secondRequestBody = new AtomicReference<String>();
        String skillRelativePath = ".ai4j/skills/reviewer/SKILL.md";
        IChatService chatService = new OpenAiChatService(configurationWithJsonResponses(
                requestCount,
                secondRequestBody,
                "{"
                        + "\"id\":\"resp_skill_auto_1\","
                        + "\"object\":\"chat.completion\","
                        + "\"created\":1710000000,"
                        + "\"model\":\"gpt-test\","
                        + "\"choices\":[{"
                        + "\"index\":0,"
                        + "\"message\":{"
                        + "\"role\":\"assistant\","
                        + "\"content\":\"\","
                        + "\"tool_calls\":[{"
                        + "\"id\":\"call_skill_auto_1\","
                        + "\"type\":\"function\","
                        + "\"function\":{"
                        + "\"name\":\"read_file\","
                        + "\"arguments\":\"{\\\"path\\\":\\\"" + skillRelativePath + "\\\"}\""
                        + "}"
                        + "}]"
                        + "},"
                        + "\"finish_reason\":\"tool_calls\""
                        + "}],"
                        + "\"usage\":{"
                        + "\"prompt_tokens\":10,"
                        + "\"completion_tokens\":5,"
                        + "\"total_tokens\":15"
                        + "}"
                        + "}",
                "{"
                        + "\"id\":\"resp_skill_auto_2\","
                        + "\"object\":\"chat.completion\","
                        + "\"created\":1710000001,"
                        + "\"model\":\"gpt-test\","
                        + "\"choices\":[{"
                        + "\"index\":0,"
                        + "\"message\":{"
                        + "\"role\":\"assistant\","
                        + "\"content\":\"Skill loaded successfully.\""
                        + "},"
                        + "\"finish_reason\":\"stop\""
                        + "}],"
                        + "\"usage\":{"
                        + "\"prompt_tokens\":12,"
                        + "\"completion_tokens\":7,"
                        + "\"total_tokens\":19"
                        + "}"
                        + "}"
        ));

        ChatCompletion completion = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Arrays.asList(
                        ChatMessage.withSystem(systemPrompt),
                        ChatMessage.withUser("Use the most relevant installed skill.")
                ))
                .tools(BuiltInTools.codingTools())
                .builtInToolContext(Skills.createToolContext(workspaceRoot, discovery))
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(completion);

        Assert.assertNotNull(response);
        Assert.assertEquals(2, requestCount.get());
        Assert.assertEquals("stop", response.getChoices().get(0).getFinishReason());
        Assert.assertEquals("Skill loaded successfully.", response.getChoices().get(0).getMessage().getContent().getText());
        Assert.assertTrue(secondRequestBody.get().contains("\"role\":\"tool\""));
        Assert.assertTrue(secondRequestBody.get().contains("Review code changes for risks."));
    }

    @Test
    public void shouldAllowLegacyFunctionsApiToUseBuiltInReadFile() throws Exception {
        Path repoRoot = Paths.get(".").toAbsolutePath().normalize();
        Path workspaceRoot = repoRoot.resolve("target").resolve("skills-functions-chat");
        Path skillFile = writeSkill(
                workspaceRoot.resolve(".ai4j").resolve("skills").resolve("reviewer").resolve("SKILL.md"),
                "# reviewer\nReview code changes for risks.\n"
        );
        String skillRelativePath = repoRoot.relativize(skillFile.toAbsolutePath().normalize()).toString().replace('\\', '/');
        Skills.DiscoveryResult discovery = Skills.discoverDefault(repoRoot, Arrays.asList(repoRoot.relativize(workspaceRoot.resolve(".ai4j").resolve("skills")).toString()));
        String systemPrompt = Skills.appendAvailableSkillsPrompt("You are a helpful assistant.", discovery.getSkills());

        AtomicInteger requestCount = new AtomicInteger();
        AtomicReference<String> secondRequestBody = new AtomicReference<String>();
        IChatService chatService = new OpenAiChatService(configurationWithJsonResponses(
                requestCount,
                secondRequestBody,
                "{"
                        + "\"id\":\"resp_skill_fn_1\","
                        + "\"object\":\"chat.completion\","
                        + "\"created\":1710000000,"
                        + "\"model\":\"gpt-test\","
                        + "\"choices\":[{"
                        + "\"index\":0,"
                        + "\"message\":{"
                        + "\"role\":\"assistant\","
                        + "\"content\":\"\","
                        + "\"tool_calls\":[{"
                        + "\"id\":\"call_skill_fn_1\","
                        + "\"type\":\"function\","
                        + "\"function\":{"
                        + "\"name\":\"read_file\","
                        + "\"arguments\":\"{\\\"path\\\":\\\"" + skillRelativePath + "\\\"}\""
                        + "}"
                        + "}]"
                        + "},"
                        + "\"finish_reason\":\"tool_calls\""
                        + "}],"
                        + "\"usage\":{"
                        + "\"prompt_tokens\":10,"
                        + "\"completion_tokens\":5,"
                        + "\"total_tokens\":15"
                        + "}"
                        + "}",
                "{"
                        + "\"id\":\"resp_skill_fn_2\","
                        + "\"object\":\"chat.completion\","
                        + "\"created\":1710000001,"
                        + "\"model\":\"gpt-test\","
                        + "\"choices\":[{"
                        + "\"index\":0,"
                        + "\"message\":{"
                        + "\"role\":\"assistant\","
                        + "\"content\":\"Legacy functions API still works.\""
                        + "},"
                        + "\"finish_reason\":\"stop\""
                        + "}],"
                        + "\"usage\":{"
                        + "\"prompt_tokens\":12,"
                        + "\"completion_tokens\":7,"
                        + "\"total_tokens\":19"
                        + "}"
                        + "}"
        ));

        ChatCompletion completion = ChatCompletion.builder()
                .model("gpt-test")
                .messages(Arrays.asList(
                        ChatMessage.withSystem(systemPrompt),
                        ChatMessage.withUser("Use the most relevant installed skill.")
                ))
                .functions("read_file")
                .build();

        ChatCompletionResponse response = chatService.chatCompletion(completion);

        Assert.assertNotNull(response);
        Assert.assertEquals(2, requestCount.get());
        Assert.assertEquals("stop", response.getChoices().get(0).getFinishReason());
        Assert.assertEquals("Legacy functions API still works.", response.getChoices().get(0).getMessage().getContent().getText());
        Assert.assertTrue(secondRequestBody.get().contains("\"tool_call_id\":\"call_skill_fn_1\""));
        Assert.assertTrue(secondRequestBody.get().contains("Review code changes for risks."));
    }

    private static Path writeSkill(Path skillFile, String content) throws Exception {
        Files.createDirectories(skillFile.getParent());
        Files.write(skillFile, content.getBytes(StandardCharsets.UTF_8));
        return skillFile;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }

    private static Configuration configurationWithJsonResponse(String responseJson, AtomicReference<String> requestJson) {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost("https://unit.test/");
        openAiConfig.setApiKey("config-api-key");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    if (requestJson != null) {
                        requestJson.set(readRequestBody(chain.request().body()));
                    }
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(responseJson, MediaType.get("application/json")))
                            .build();
                })
                .build();

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        configuration.setOkHttpClient(okHttpClient);
        return configuration;
    }

    private static Configuration configurationWithJsonResponses(AtomicInteger requestCount,
                                                                AtomicReference<String> secondRequestBody,
                                                                String firstResponseJson,
                                                                String secondResponseJson) {
        OpenAiConfig openAiConfig = new OpenAiConfig();
        openAiConfig.setApiHost("https://unit.test/");
        openAiConfig.setApiKey("config-api-key");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    int current = requestCount.incrementAndGet();
                    if (current == 2 && secondRequestBody != null) {
                        secondRequestBody.set(readRequestBody(chain.request().body()));
                    }
                    String responseJson = current == 1 ? firstResponseJson : secondResponseJson;
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(responseJson, MediaType.get("application/json")))
                            .build();
                })
                .build();

        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(openAiConfig);
        configuration.setOkHttpClient(okHttpClient);
        return configuration;
    }

    private static String readRequestBody(RequestBody body) {
        if (body == null) {
            return "";
        }
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read request body", ex);
        }
    }
}
