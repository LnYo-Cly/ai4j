package io.github.lnyocly.ai4j.rag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.IChatService;

public class ChatRagJudge implements RagJudge {

    private static final String SYSTEM_PROMPT = "You are a strict RAG evaluator. Return only JSON with "
            + "faithfulnessScore, contextRelevanceScore, answerRelevanceScore, reason. Scores must be numbers from 0 to 1.";

    private final IChatService chatService;
    private final String model;

    public ChatRagJudge(IChatService chatService, String model) {
        if (chatService == null) {
            throw new IllegalArgumentException("chatService is required");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model is required");
        }
        this.chatService = chatService;
        this.model = model;
    }

    @Override
    public RagJudgeEvaluation judge(RagJudgeRequest request) throws Exception {
        ChatCompletionResponse response = chatService.chatCompletion(ChatCompletion.builder()
                .model(model)
                .message(ChatMessage.withSystem(SYSTEM_PROMPT))
                .message(ChatMessage.withUser(userPrompt(request)))
                .temperature(0.0f)
                .responseFormat(JSON.parseObject("{\"type\":\"json_object\"}"))
                .build());
        String output = firstText(response);
        JSONObject json = JSON.parseObject(jsonObjectText(output));
        return RagJudgeEvaluation.builder()
                .faithfulnessScore(score(json, "faithfulnessScore"))
                .contextRelevanceScore(score(json, "contextRelevanceScore"))
                .answerRelevanceScore(score(json, "answerRelevanceScore"))
                .reason(json.getString("reason"))
                .rawOutput(output)
                .build();
    }

    private String userPrompt(RagJudgeRequest request) {
        return "Question:\n" + safe(request == null ? null : request.getQuery())
                + "\n\nAnswer:\n" + safe(request == null ? null : request.getAnswer())
                + "\n\nRetrieved context:\n" + safe(request == null ? null : request.getContext())
                + "\n\nJudge:\n"
                + "- faithfulnessScore: answer is supported by retrieved context.\n"
                + "- contextRelevanceScore: retrieved context is relevant to the question.\n"
                + "- answerRelevanceScore: answer directly addresses the question.";
    }

    private String firstText(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null || choice.getMessage().getContent() == null) {
            return null;
        }
        return choice.getMessage().getContent().getText();
    }

    private Double score(JSONObject json, String key) {
        if (json == null || !json.containsKey(key)) {
            return null;
        }
        double value = json.getDoubleValue(key);
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String jsonObjectText(String output) {
        if (output == null) {
            return "{}";
        }
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return output.substring(start, end + 1);
        }
        return "{}";
    }
}
