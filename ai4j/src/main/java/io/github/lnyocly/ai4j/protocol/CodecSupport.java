package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.MediaKind;
import io.github.lnyocly.ai4j.model.MediaRef;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared codec helpers. Kept protocol-neutral: only pure conversions over the
 * semantic model live here.
 */
public final class CodecSupport {

    private CodecSupport() {
    }

    /**
     * Map an image block onto the OpenAI-style {@code image_url} content part.
     *
     * @return the part map, or {@code null} when the media identity cannot be
     *         expressed by this shape (the caller records an explicit warning)
     */
    public static Map<String, Object> openAiImagePart(ImageBlock block) {
        MediaRef media = block.getMedia();
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "image_url");
        Map<String, Object> url = new LinkedHashMap<String, Object>();
        if (media.getKind() == MediaKind.URL) {
            url.put("url", media.getValue());
        } else if (media.getKind() == MediaKind.INLINE_BASE64) {
            if (media.getMediaType() == null) {
                return null;
            }
            url.put("url", "data:" + media.getMediaType() + ";base64," + media.getValue());
        } else {
            return null;
        }
        part.put("image_url", url);
        return part;
    }

    /** Flatten an item list into messages for protocols with a flat message array. */
    public static java.util.List<Message> messagesOf(List<ModelItem> items) {
        java.util.List<Message> messages = new java.util.ArrayList<Message>();
        for (ModelItem item : items) {
            if (item instanceof Message) {
                messages.add((Message) item);
            }
        }
        return messages;
    }
}
