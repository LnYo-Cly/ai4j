package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.platform.suno.music.SunoMusicService;
import io.github.lnyocly.ai4j.service.IMusicService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class AiServiceSunoAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfigAutoConfiguration.class);

    @Test
    public void starterShouldBindSingleSunoConfig() {
        contextRunner
                .withPropertyValues(
                        "ai.suno.api-key=suno-key",
                        "ai.suno.api-host=https://api.chatfire.cn/",
                        "ai.suno.music-url=suno/submit/music",
                        "ai.suno.lyrics-url=suno/submit/lyrics",
                        "ai.suno.fetch-url=suno/fetch/{task_id}"
                )
                .run(context -> {
                    AiService aiService = context.getBean(AiService.class);
                    Assert.assertEquals("suno-key", aiService.getConfiguration().getSunoConfig().getApiKey());
                    Assert.assertEquals("https://api.chatfire.cn/", aiService.getConfiguration().getSunoConfig().getApiHost());
                    Assert.assertEquals("suno/submit/music", aiService.getConfiguration().getSunoConfig().getMusicUrl());
                    Assert.assertEquals("suno/submit/lyrics", aiService.getConfiguration().getSunoConfig().getLyricsUrl());
                    Assert.assertEquals("suno/fetch/{task_id}", aiService.getConfiguration().getSunoConfig().getFetchUrl());

                    IMusicService musicService = aiService.getMusicService(PlatformType.SUNO);
                    Assert.assertTrue(musicService instanceof SunoMusicService);
                });
    }

    @Test
    public void starterMultiInstanceShouldBindSunoMusicService() {
        contextRunner
                .withPropertyValues(
                        "ai.platforms[0].id=chatfire-suno",
                        "ai.platforms[0].platform=suno",
                        "ai.platforms[0].api-key=tenant-suno-key",
                        "ai.platforms[0].api-host=https://api.chatfire.cn/",
                        "ai.platforms[0].music-url=suno/submit/music",
                        "ai.platforms[0].lyrics-url=suno/submit/lyrics",
                        "ai.platforms[0].fetch-url=suno/fetch/{task_id}"
                )
                .run(context -> {
                    AiServiceRegistry registry = context.getBean(AiServiceRegistry.class);
                    AiService aiService = registry.getAiService("chatfire-suno");

                    Assert.assertEquals("tenant-suno-key", aiService.getConfiguration().getSunoConfig().getApiKey());
                    Assert.assertEquals("https://api.chatfire.cn/", aiService.getConfiguration().getSunoConfig().getApiHost());
                    Assert.assertEquals("suno/submit/music", aiService.getConfiguration().getSunoConfig().getMusicUrl());
                    Assert.assertEquals("suno/submit/lyrics", aiService.getConfiguration().getSunoConfig().getLyricsUrl());
                    Assert.assertEquals("suno/fetch/{task_id}", aiService.getConfiguration().getSunoConfig().getFetchUrl());
                    Assert.assertTrue(registry.getMusicService("chatfire-suno") instanceof SunoMusicService);
                });
    }
}
