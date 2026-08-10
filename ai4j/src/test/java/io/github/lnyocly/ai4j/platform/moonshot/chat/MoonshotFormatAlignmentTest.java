package io.github.lnyocly.ai4j.platform.moonshot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.platform.moonshot.chat.entity.MoonshotChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.moonshot.chat.entity.MoonshotUsage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * Kimi/Moonshot 与 OpenAI 标准格式的对齐测试（无需密钥）。
 */
public class MoonshotFormatAlignmentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Kimi 把 cached_tokens 放在 usage 顶层，必须能被反序列化捕获。
     */
    @Test
    public void kimiTopLevelCachedTokensIsDeserialized() throws Exception {
        String json = "{\"prompt_tokens\":19,\"completion_tokens\":21,\"total_tokens\":40,\"cached_tokens\":10}";

        MoonshotUsage usage = objectMapper.readValue(json, MoonshotUsage.class);

        Assert.assertEquals(19L, usage.getPromptTokens());
        Assert.assertEquals(21L, usage.getCompletionTokens());
        Assert.assertEquals(40L, usage.getTotalTokens());
        Assert.assertEquals(Long.valueOf(10L), usage.getCachedTokens());
    }

    /**
     * 顶层 cached_tokens 归一化到 OpenAI 标准位置 prompt_tokens_details.cached_tokens。
     */
    @Test
    public void topLevelCachedTokensIsNormalizedToPromptTokensDetails() throws Exception {
        String json = "{\"prompt_tokens\":19,\"completion_tokens\":21,\"total_tokens\":40,\"cached_tokens\":10}";
        MoonshotUsage moonshotUsage = objectMapper.readValue(json, MoonshotUsage.class);

        Usage standard = moonshotUsage.toStandardUsage();

        Assert.assertNotNull("prompt_tokens_details 应被创建", standard.getPromptTokensDetails());
        Assert.assertEquals("顶层 cached_tokens 应归一化到 OpenAI 标准位置",
                Long.valueOf(10L), standard.getPromptTokensDetails().getCachedTokens());
        Assert.assertEquals(19L, standard.getPromptTokens());
    }

    /**
     * 若 provider 已按 OpenAI 标准返回嵌套 cached_tokens，不应被顶层值覆盖。
     */
    @Test
    public void nestedCachedTokensTakesPrecedenceOverTopLevel() throws Exception {
        String json = "{\"prompt_tokens\":19,\"completion_tokens\":21,\"total_tokens\":40,"
                + "\"cached_tokens\":10,\"prompt_tokens_details\":{\"cached_tokens\":7}}";
        MoonshotUsage moonshotUsage = objectMapper.readValue(json, MoonshotUsage.class);

        Usage standard = moonshotUsage.toStandardUsage();

        Assert.assertEquals("已有嵌套值时不应被顶层覆盖",
                Long.valueOf(7L), standard.getPromptTokensDetails().getCachedTokens());
    }

    /**
     * 没有 cached_tokens 时不应崩溃，且不产生虚假值。
     */
    @Test
    public void missingCachedTokensYieldsNullNotZero() throws Exception {
        String json = "{\"prompt_tokens\":19,\"completion_tokens\":21,\"total_tokens\":40}";
        MoonshotUsage moonshotUsage = objectMapper.readValue(json, MoonshotUsage.class);

        Usage standard = moonshotUsage.toStandardUsage();

        Assert.assertNull("无 cached_tokens 时不应捏造 0",
                standard.getPromptTokensDetails().getCachedTokens());
    }

    /**
     * 完整 Kimi 响应（顶层 cached_tokens + reasoning_content）应能反序列化。
     */
    @Test
    public void fullKimiResponseWithReasoningContentIsDeserialized() throws Exception {
        String json = "{\"id\":\"cmpl-1\",\"object\":\"chat.completion\",\"created\":1698999496,"
                + "\"model\":\"kimi-k2.6\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":\"1+1 等于 2。\",\"reasoning_content\":\"用户问基础数学题。\"},"
                + "\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":19,\"completion_tokens\":21,\"total_tokens\":40,\"cached_tokens\":10}}";

        MoonshotChatCompletionResponse response =
                objectMapper.readValue(json, MoonshotChatCompletionResponse.class);

        Assert.assertEquals("kimi-k2.6", response.getModel());
        Assert.assertEquals("用户问基础数学题。",
                response.getChoices().get(0).getMessage().getReasoningContent());
        Assert.assertEquals(Long.valueOf(10L), response.getUsage().getCachedTokens());
    }

    /**
     * reasoning_effort 是 OpenAI 标准字段，应序列化为 snake_case；未设置时不出现。
     */
    @Test
    public void reasoningEffortSerializesAsSnakeCaseAndIsOmittedWhenNull() throws Exception {
        ChatCompletion withEffort = ChatCompletion.builder()
                .model("kimi-k3")
                .messages(Collections.singletonList(ChatMessage.withUser("hi")))
                .reasoningEffort("max")
                .build();

        String json = objectMapper.writeValueAsString(withEffort);
        Assert.assertTrue("应序列化为 reasoning_effort", json.contains("\"reasoning_effort\":\"max\""));

        ChatCompletion withoutEffort = ChatCompletion.builder()
                .model("kimi-k3")
                .messages(Collections.singletonList(ChatMessage.withUser("hi")))
                .build();

        String jsonWithout = objectMapper.writeValueAsString(withoutEffort);
        Assert.assertFalse("未设置时不应出现该字段", jsonWithout.contains("reasoning_effort"));
    }

    /**
     * video_url 多模态内容应序列化为 Kimi/OpenAI 兼容结构。
     */
    @Test
    public void videoUrlMultiModalSerializes() throws Exception {
        Content.MultiModal video = Content.MultiModal.builder()
                .type(Content.MultiModal.Type.VIDEO_URL.getType())
                .videoUrl(new Content.MultiModal.VideoUrl("data:video/mp4;base64,AAAA"))
                .build();

        String json = objectMapper.writeValueAsString(video);

        Assert.assertTrue(json.contains("\"type\":\"video_url\""));
        Assert.assertTrue(json.contains("\"video_url\":{\"url\":\"data:video/mp4;base64,AAAA\"}"));
        Assert.assertFalse("未设置的 image_url 不应出现", json.contains("image_url"));
    }

    /**
     * 三参构造器保持向后兼容（既有调用方不受 videoUrl 新增影响）。
     */
    @Test
    public void legacyThreeArgConstructorStillWorks() {
        Content.MultiModal image = new Content.MultiModal(
                Content.MultiModal.Type.IMAGE_URL.getType(),
                null,
                new Content.MultiModal.ImageUrl("https://example.com/a.png"));

        Assert.assertEquals("image_url", image.getType());
        Assert.assertNull(image.getVideoUrl());
    }
}
