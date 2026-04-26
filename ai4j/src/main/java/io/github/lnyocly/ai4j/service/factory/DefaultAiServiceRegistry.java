package io.github.lnyocly.ai4j.service.factory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import io.github.lnyocly.ai4j.config.AiPlatform;
import io.github.lnyocly.ai4j.config.BaichuanConfig;
import io.github.lnyocly.ai4j.config.DashScopeConfig;
import io.github.lnyocly.ai4j.config.DeepSeekConfig;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.config.HunyuanConfig;
import io.github.lnyocly.ai4j.config.JinaConfig;
import io.github.lnyocly.ai4j.config.LingyiConfig;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.config.MoonshotConfig;
import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.service.AiConfig;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 默认的多实例 {@link AiService} 注册表实现。
 */
public class DefaultAiServiceRegistry implements AiServiceRegistry {

    private final Map<String, AiServiceRegistration> registrations;

    public DefaultAiServiceRegistry(Map<String, AiServiceRegistration> registrations) {
        this.registrations = Collections.unmodifiableMap(new LinkedHashMap<String, AiServiceRegistration>(registrations));
    }

    public static DefaultAiServiceRegistry empty() {
        return new DefaultAiServiceRegistry(Collections.<String, AiServiceRegistration>emptyMap());
    }

    public static DefaultAiServiceRegistry from(Configuration configuration, AiConfig aiConfig) {
        return from(configuration, aiConfig, new DefaultAiServiceFactory());
    }

    public static DefaultAiServiceRegistry from(Configuration configuration, AiConfig aiConfig, AiServiceFactory aiServiceFactory) {
        if (configuration == null || aiConfig == null || CollUtil.isEmpty(aiConfig.getPlatforms())) {
            return empty();
        }

        Map<String, AiServiceRegistration> registrations = new LinkedHashMap<String, AiServiceRegistration>();
        for (AiPlatform aiPlatform : aiConfig.getPlatforms()) {
            if (aiPlatform == null) {
                continue;
            }
            String id = aiPlatform.getId();
            if (id == null || "".equals(id.trim())) {
                throw new IllegalArgumentException("Ai platform id must not be blank");
            }
            PlatformType platformType = resolvePlatformType(aiPlatform.getPlatform(), id);
            Configuration scopedConfiguration = createScopedConfiguration(configuration, aiPlatform, platformType);
            registrations.put(id, new AiServiceRegistration(id, platformType, aiServiceFactory.create(scopedConfiguration)));
        }
        return new DefaultAiServiceRegistry(registrations);
    }

    @Override
    public AiServiceRegistration find(String id) {
        return registrations.get(id);
    }

    @Override
    public Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(registrations.keySet()));
    }

    private static Configuration createScopedConfiguration(Configuration source, AiPlatform aiPlatform, PlatformType platformType) {
        Configuration target = new Configuration();
        BeanUtil.copyProperties(source, target, CopyOptions.create());
        applyPlatformConfig(target, aiPlatform, platformType);
        return target;
    }

    private static PlatformType resolvePlatformType(String rawPlatform, String id) {
        if (rawPlatform == null || "".equals(rawPlatform.trim())) {
            throw new IllegalArgumentException("Ai platform '" + id + "' platform must not be blank");
        }

        String target = rawPlatform.trim();
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType.getPlatform().equalsIgnoreCase(target)) {
                return platformType;
            }
        }
        throw new IllegalArgumentException("Unsupported ai platform '" + rawPlatform + "' for id '" + id + "'");
    }

    private static void applyPlatformConfig(Configuration target, AiPlatform aiPlatform, PlatformType platformType) {
        switch (platformType) {
            case OPENAI:
                target.setOpenAiConfig(copy(aiPlatform, OpenAiConfig.class));
                break;
            case ZHIPU:
                target.setZhipuConfig(copy(aiPlatform, ZhipuConfig.class));
                break;
            case DEEPSEEK:
                target.setDeepSeekConfig(copy(aiPlatform, DeepSeekConfig.class));
                break;
            case MOONSHOT:
                target.setMoonshotConfig(copy(aiPlatform, MoonshotConfig.class));
                break;
            case HUNYUAN:
                target.setHunyuanConfig(copy(aiPlatform, HunyuanConfig.class));
                break;
            case LINGYI:
                target.setLingyiConfig(copy(aiPlatform, LingyiConfig.class));
                break;
            case OLLAMA:
                target.setOllamaConfig(copy(aiPlatform, OllamaConfig.class));
                break;
            case MINIMAX:
                target.setMinimaxConfig(copy(aiPlatform, MinimaxConfig.class));
                break;
            case BAICHUAN:
                target.setBaichuanConfig(copy(aiPlatform, BaichuanConfig.class));
                break;
            case DASHSCOPE:
                target.setDashScopeConfig(copy(aiPlatform, DashScopeConfig.class));
                break;
            case DOUBAO:
                target.setDoubaoConfig(copy(aiPlatform, DoubaoConfig.class));
                break;
            case JINA:
                target.setJinaConfig(copy(aiPlatform, JinaConfig.class));
                break;
            default:
                throw new IllegalArgumentException("Unsupported platform type: " + platformType);
        }
    }

    private static <T> T copy(AiPlatform aiPlatform, Class<T> type) {
        try {
            T target = type.getDeclaredConstructor().newInstance();
            BeanUtil.copyProperties(aiPlatform, target, CopyOptions.create().ignoreNullValue());
            return target;
        } catch (InstantiationException e) {
            throw new IllegalStateException("Cannot instantiate config type: " + type.getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access config type: " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Cannot invoke config constructor: " + type.getName(), e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Missing no-args constructor for config type: " + type.getName(), e);
        }
    }
}

