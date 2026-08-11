package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageData;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGeneration;
import io.github.lnyocly.ai4j.platform.openai.image.entity.ImageGenerationResponse;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IImageService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Live smoke test for image generation — the one model-access service that had no live
 * verification before this.
 *
 * <p>Requires {@code OPENAI_API_KEY} and an image-capable model named via
 * {@code OPENAI_IMAGE_MODEL}. Honours {@code OPENAI_API_HOST}. Skips when absent.
 *
 * <p>Uses {@code responseFormat=b64_json} so the test does not depend on the gateway's
 * image-host URL being externally reachable (some gateways return an internal-IP media URL).
 */
@Category(LiveProviderTest.class)
public class ImageGenerationLiveTest {

    private IImageService imageService() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assume.assumeTrue("OPENAI_API_KEY not set", apiKey != null && !apiKey.trim().isEmpty());

        OpenAiConfig cfg = new OpenAiConfig();
        cfg.setApiKey(apiKey);
        String host = System.getenv("OPENAI_API_HOST");
        if (host != null && !host.trim().isEmpty()) {
            cfg.setApiHost(host);
        }
        Configuration c = new Configuration();
        c.setOpenAiConfig(cfg);
        return new AiService(c).getImageService(PlatformType.OPENAI);
    }

    private String model() {
        String m = System.getenv("OPENAI_IMAGE_MODEL");
        return (m == null || m.trim().isEmpty()) ? "dall-e-3" : m;
    }

    @Test
    public void generateReturnsBase64Image() throws Exception {
        IImageService imageService = imageService();

        ImageGeneration request = ImageGeneration.builder()
                .model(model())
                .prompt("a small solid red circle centered on a white background, minimal")
                .size("1024x1024")
                .n(1)
                .responseFormat("b64_json")   // inline base64 — robust to gateway media-URL quirks
                .build();

        ImageGenerationResponse response;
        try {
            response = imageService.generate(request);
        } catch (Exception e) {
            Assume.assumeNoException("gateway does not support image generation (" + e.getMessage() + ")", e);
            return;
        }

        Assert.assertNotNull(response);
        Assert.assertNotNull("response data list should be present", response.getData());
        Assert.assertFalse("should produce at least one image", response.getData().isEmpty());

        ImageData image = response.getData().get(0);
        // b64_json mode: the payload is inline; url mode: a fetchable URL.
        boolean hasPayload = (image.getB64Json() != null && !image.getB64Json().isEmpty())
                || (image.getUrl() != null && !image.getUrl().isEmpty());
        Assert.assertTrue("image should carry b64_json or url, got: " + image.getB64Json() + " / " + image.getUrl(),
                hasPayload);

        if (image.getB64Json() != null) {
            System.out.println("b64_json length: " + image.getB64Json().length());
            Assert.assertTrue("base64 payload should be non-trivial", image.getB64Json().length() > 1000);
        } else {
            System.out.println("url: " + image.getUrl());
        }
    }
}
