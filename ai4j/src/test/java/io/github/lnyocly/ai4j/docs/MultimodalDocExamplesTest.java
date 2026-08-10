package io.github.lnyocly.ai4j.docs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Executable source of truth for the snippets embedded in
 * {@code docs/core-sdk/model-access/multimodal.md}.
 *
 * <p>The dual-projection and wire-shape checks need no key and pin the
 * serialization contract; they run in normal CI. Live multimodal calls live
 * in {@code ChatDocExamplesLiveTest#multiModalUserMessageWithImage}.
 */
public class MultimodalDocExamplesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 8x8 纯红 PNG，避免示例依赖外部图床。 */
    private static final String RED_PNG_DATA_URL =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEklEQVR4nGP4z8CAFWEXHbQSACj/P8Fu7N9hAAAAAElFTkSuQmCC";

    // ---- §3 Chat 投影：ChatMemoryItem → ChatMessage ----

    @Test
    public void memoryItemWithImageProjectsToChatMultiModal() throws Exception {
        ChatMemoryItem item = ChatMemoryItem.user("这张图是什么颜色？", RED_PNG_DATA_URL);

        ChatMessage message = item.toChatMessage();

        Assert.assertEquals("user", message.getRole());
        Assert.assertNotNull("应有 multiModals", message.getContent().getMultiModals());

        List<Content.MultiModal> parts = message.getContent().getMultiModals();
        // 第一个 part 是文本，后续是 image_url
        Assert.assertEquals("text", parts.get(0).getType());
        Assert.assertEquals("这张图是什么颜色？", parts.get(0).getText());
        Assert.assertEquals("image_url", parts.get(1).getType());
        Assert.assertEquals(RED_PNG_DATA_URL, parts.get(1).getImageUrl().getUrl());
    }

    // ---- §4 Responses 投影：ChatMemoryItem → input_text/input_image ----

    @Test
    public void memoryItemWithImageProjectsToResponsesInputTextAndImage() throws Exception {
        ChatMemoryItem item = ChatMemoryItem.user("这张图是什么颜色？", RED_PNG_DATA_URL);

        Object projected = item.toResponsesInput();

        JsonNode node = mapper.valueToTree(projected);
        Assert.assertEquals("message", node.path("type").asText());
        Assert.assertEquals("user", node.path("role").asText());

        JsonNode content = node.path("content");
        Assert.assertTrue("应有 content 数组", content.isArray());
        Assert.assertEquals("input_text", content.get(0).path("type").asText());
        Assert.assertEquals("这张图是什么颜色？", content.get(0).path("text").asText());
        Assert.assertEquals("input_image", content.get(1).path("type").asText());
        Assert.assertEquals(RED_PNG_DATA_URL, content.get(1).path("image_url").path("url").asText());
    }

    // ---- §5 双投影：同一份会话事实两种输出 ----

    @Test
    public void sameMemoryFactProducesBothChatAndResponsesShapes() {
        ChatMemoryItem item = ChatMemoryItem.user("描述这张图", RED_PNG_DATA_URL);

        ChatMessage chat = item.toChatMessage();
        Object responses = item.toResponsesInput();

        // Chat 形状：image_url part
        Assert.assertEquals("image_url", chat.getContent().getMultiModals().get(1).getType());
        // Responses 形状：input_image part
        JsonNode r = mapper.valueToTree(responses);
        Assert.assertEquals("input_image", r.path("content").get(1).path("type").asText());
    }

    // ---- 直接构造（不经 Memory）：image_url ----

    @Test
    public void directChatMessageWithImageSerializesCorrectly() throws Exception {
        ChatMessage message = ChatMessage.withUser("这张图是什么颜色？", RED_PNG_DATA_URL);

        JsonNode content = mapper.valueToTree(message.getContent().getMultiModals());
        Assert.assertEquals("text", content.get(0).path("type").asText());
        Assert.assertEquals("image_url", content.get(1).path("type").asText());
        Assert.assertEquals(RED_PNG_DATA_URL, content.get(1).path("image_url").path("url").asText());
    }

    // ---- video_url（Kimi/Moonshot 扩展） ----

    @Test
    public void videoUrlMultiModalSerializes() {
        Content.MultiModal video = Content.MultiModal.builder()
                .type(Content.MultiModal.Type.VIDEO_URL.getType())
                .videoUrl(new Content.MultiModal.VideoUrl("data:video/mp4;base64,AAAA"))
                .build();

        Assert.assertEquals("video_url", video.getType());
        Assert.assertEquals("data:video/mp4;base64,AAAA", video.getVideoUrl().getUrl());
    }
}
