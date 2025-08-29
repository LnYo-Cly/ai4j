package io.github.lnyocly.ai4j.platform.dashscope.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.common.*;
import com.alibaba.dashscope.tools.ToolCallFunction;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;

import java.util.List;
import java.util.stream.Collectors;

public class MessageUtil {

    public static ChatMessage convert(Message message) {
        ChatMessage chatMessage = BeanUtil.copyProperties(message, ChatMessage.class, "content", "toolCalls");
        if (StrUtil.isNotBlank(message.getContent())) {
            chatMessage.setContent(Content.ofText(message.getContent()));
        }
        if (CollUtil.isNotEmpty(message.getContents())) {
            List<Content.MultiModal> list = message.getContents().stream().map(content -> {
                if ("image_url".equals(content.getType())) {
                    return Content.MultiModal.builder().type(Content.MultiModal.Type.IMAGE_URL.getType()).imageUrl(new Content.MultiModal.ImageUrl(((MessageContentImageURL) content).getImageURL().getUrl())).build();
                } else {
                    return Content.MultiModal.builder().type(Content.MultiModal.Type.TEXT.getType()).text(((MessageContentText) content).getText()).build();
                }
            }).collect(Collectors.toList());
            chatMessage.setContent(Content.ofMultiModals(list));
        }
        if (CollUtil.isNotEmpty(message.getToolCalls())) {
            chatMessage.setToolCalls(message.getToolCalls().stream().map(toolCall -> {
                if ("function".equals(toolCall.getType())) {
                    ToolCallFunction.CallFunction src = ((ToolCallFunction) toolCall).getFunction();


                    ToolCall tool = new ToolCall();
                    tool.setId(toolCall.getId());
                    tool.setType("function");
                    tool.setFunction(new ToolCall.Function(src.getName(), src.getArguments()));
                    return tool;
                } else {
                    return null;
                }
            }).filter(ObjUtil::isNotNull).collect(Collectors.toList()));
        }
        return chatMessage;
    }

    public static List<ChatMessage> convertToChatMessage(List<Message> messageList) {
        return messageList.stream().map(MessageUtil::convert).collect(Collectors.toList());
    }

    public static Message convert(ChatMessage message) {
        Message target = BeanUtil.copyProperties(message, Message.class, "content", "toolCalls");
        if (ObjUtil.isNotNull(message.getContent())) {
            if (StrUtil.isNotBlank(message.getContent().getText())) {
                target.setContent(message.getContent().getText());
            }
            if (CollUtil.isNotEmpty(message.getContent().getMultiModals())) {
                target.setContents(message.getContent().getMultiModals().stream().map(multiModal -> {
                    if (Content.MultiModal.Type.IMAGE_URL.getType().equals(multiModal.getType())) {
                        return MessageContentImageURL.builder().imageURL(ImageURL.builder().url(multiModal.getImageUrl().getUrl()).build()).build();
                    } else {
                        return MessageContentText.builder().text(multiModal.getText()).build();
                    }
                }).collect(Collectors.toList()));
            }
        }
        if (CollUtil.isNotEmpty(message.getToolCalls())) {
            target.setToolCalls(message.getToolCalls().stream().map(toolCall -> {
                if ("function".equals(toolCall.getType())) {
                    ToolCall.Function src = toolCall.getFunction();

                    ToolCallFunction toolCallFunction = new ToolCallFunction();
                    ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
                    callFunction.setName(src.getName());
                    callFunction.setArguments(src.getArguments());
                    toolCallFunction.setFunction(callFunction);
                    toolCallFunction.setId(toolCall.getId());
                    return toolCallFunction;
                } else {
                    return null;
                }
            }).filter(ObjUtil::isNotNull).collect(Collectors.toList()));
        }
        return target;
    }

    public static List<Message> convertToMessage(List<ChatMessage> messageList) {
        return messageList.stream().map(MessageUtil::convert).collect(Collectors.toList());
    }
}
