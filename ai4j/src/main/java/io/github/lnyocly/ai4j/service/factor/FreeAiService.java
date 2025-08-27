package io.github.lnyocly.ai4j.service.factor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import io.github.lnyocly.ai4j.config.*;
import io.github.lnyocly.ai4j.platform.baichuan.chat.BaichuanChatService;
import io.github.lnyocly.ai4j.platform.deepseek.chat.DeepSeekChatService;
import io.github.lnyocly.ai4j.platform.hunyuan.chat.HunyuanChatService;
import io.github.lnyocly.ai4j.platform.lingyi.chat.LingyiChatService;
import io.github.lnyocly.ai4j.platform.minimax.chat.MinimaxChatService;
import io.github.lnyocly.ai4j.platform.moonshot.chat.MoonshotChatService;
import io.github.lnyocly.ai4j.platform.ollama.chat.OllamaAiChatService;
import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.platform.zhipu.chat.ZhipuChatService;
import io.github.lnyocly.ai4j.service.AiConfig;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FreeAiService {
    private static final ConcurrentMap<String, IChatService> chatServiceCache = new ConcurrentHashMap<>();

    private final Configuration configuration;

    private final AiConfig aiConfig;

    public FreeAiService(Configuration configuration, AiConfig aiConfig) {
        this.configuration = configuration;
        this.aiConfig = aiConfig;
        init();
    }

    public void init() {
        if (CollUtil.isEmpty(aiConfig.getPlatforms())) return;
        for (AiPlatform platform : aiConfig.getPlatforms()) {
            IChatService chatService = createChatService(platform);
            chatServiceCache.put(platform.getId(), chatService);
        }
    }

    public static IChatService getChatService(String id) {
        return chatServiceCache.get(id);
    }

    private IChatService createChatService(AiPlatform aiPlatform) {
        PlatformType platform = PlatformType.getPlatform(aiPlatform.getPlatform());
        switch (platform) {
            case OPENAI:
                OpenAiConfig openAiConfig = new OpenAiConfig();
                BeanUtil.copyProperties(aiPlatform, openAiConfig, CopyOptions.create().ignoreNullValue());
                return new OpenAiChatService(configuration, openAiConfig);
            case ZHIPU:
                ZhipuConfig zhipuConfig = new ZhipuConfig();
                BeanUtil.copyProperties(aiPlatform, zhipuConfig, CopyOptions.create().ignoreNullValue());
                return new ZhipuChatService(configuration, zhipuConfig);
            case DEEPSEEK:
                DeepSeekConfig deepSeekConfig = new DeepSeekConfig();
                BeanUtil.copyProperties(aiPlatform, deepSeekConfig, CopyOptions.create().ignoreNullValue());
                return new DeepSeekChatService(configuration, deepSeekConfig);
            case MOONSHOT:
                MoonshotConfig moonshotConfig = new MoonshotConfig();
                BeanUtil.copyProperties(aiPlatform, moonshotConfig, CopyOptions.create().ignoreNullValue());
                return new MoonshotChatService(configuration, moonshotConfig);
            case HUNYUAN:
                HunyuanConfig hunyuanConfig = new HunyuanConfig();
                BeanUtil.copyProperties(aiPlatform, hunyuanConfig, CopyOptions.create().ignoreNullValue());
                return new HunyuanChatService(configuration, hunyuanConfig);
            case LINGYI:
                LingyiConfig lingyiConfig = new LingyiConfig();
                BeanUtil.copyProperties(aiPlatform, lingyiConfig, CopyOptions.create().ignoreNullValue());
                return new LingyiChatService(configuration, lingyiConfig);
            case OLLAMA:
                OllamaConfig ollamaConfig = new OllamaConfig();
                BeanUtil.copyProperties(aiPlatform, ollamaConfig, CopyOptions.create().ignoreNullValue());
                return new OllamaAiChatService(configuration, ollamaConfig);
            case MINIMAX:
                MinimaxConfig minimaxConfig = new MinimaxConfig();
                BeanUtil.copyProperties(aiPlatform, minimaxConfig, CopyOptions.create().ignoreNullValue());
                return new MinimaxChatService(configuration, minimaxConfig);
            case BAICHUAN:
                BaichuanConfig baichuanConfig = new BaichuanConfig();
                BeanUtil.copyProperties(aiPlatform, baichuanConfig, CopyOptions.create().ignoreNullValue());
                return new BaichuanChatService(configuration, baichuanConfig);
            default:
                throw new IllegalArgumentException("Unknown platform: " + platform);
        }
    }

}
