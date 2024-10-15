package io.github.lnyocly.ai4j.platform.openai.chat.entity;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/3 18:14
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    private String content;
    private String role;
    private String name;
    private String refusal;

    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    public ChatMessage(String userMessage) {
        this.role = ChatMessageType.USER.getRole();
        this.content = userMessage;
    }
    public ChatMessage(ChatMessageType role, String message) {
        this.role = role.getRole();
        this.content = message;
    }
    public ChatMessage(String role, String message) {
        this.role = role;
        this.content = message;
    }

    public static ChatMessage withSystem(String content) {
        return new ChatMessage(ChatMessageType.SYSTEM, content);
    }

    public static ChatMessage withUser(String content) {
        return new ChatMessage(ChatMessageType.USER, content);
    }
    public static ChatMessage withUser(String content, String ...images) {
        return ChatMessage.builder()
                .role(ChatMessageType.USER.getRole())
                .content(ChatMessage.MultiModal.withMultiModal(content, images))
                .build();
    }

    public static ChatMessage withAssistant(String content) {
        return new ChatMessage(ChatMessageType.ASSISTANT, content);
    }
    public static ChatMessage withAssistant(List<ToolCall> toolCalls) {
        return ChatMessage.builder()
                .role(ChatMessageType.ASSISTANT.getRole())
                .toolCalls(toolCalls)
                .build();
    }

    public static ChatMessage withTool(String content, String toolCallId) {
        return ChatMessage.builder()
                .role(ChatMessageType.TOOL.getRole())
                .content(content)
                .toolCallId(toolCallId)
                .build();
    }


    public static class ChatMessageBuilder {
        private String content;

        /**
         * 多模态消息内容
         *
         * @param content 多模态消息内容
         * @return
         */
        public ChatMessageBuilder content(List<MultiModal> content){
            this.content = JSON.toJSONString(content);
            return this;
        }
        public ChatMessageBuilder content(String content){
            this.content = content;
            return this;
        }


    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MultiModal {
        private String type = Type.TEXT.type;
        private String text;
        @JsonProperty("image_url")
        private ImageUrl imageUrl;


        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ImageUrl {
            private String url;
        }

        @Getter
        @AllArgsConstructor
        public enum Type {
            TEXT("text", "文本类型"),
            IMAGE_URL("image_url", "图片类型，可以为url或者base64"),
            ;
            private final String type;
            private final String info;
        }

        public static List<MultiModal> withMultiModal(String text, String... imageUrl) {
            List<MultiModal> messages = new ArrayList<>();
            messages.add(new MultiModal(Type.TEXT.getType(), text, null));
            for (String url : imageUrl) {
                messages.add(new MultiModal(Type.IMAGE_URL.getType(), null, new ImageUrl(url)));
            }
            return messages;
        }

    }





}
