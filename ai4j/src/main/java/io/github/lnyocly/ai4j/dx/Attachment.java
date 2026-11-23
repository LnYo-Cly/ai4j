package io.github.lnyocly.ai4j.dx;

import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.AudioBlock;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.DocumentBlock;
import io.github.lnyocly.ai4j.model.ImageBlock;
import io.github.lnyocly.ai4j.model.MediaRef;
import io.github.lnyocly.ai4j.model.VideoBlock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

/**
 * Multimodal attachment helper converting files, paths, URIs, streams, and byte arrays
 * into kernel {@link Block} instances.
 */
public final class Attachment {

    private final Block block;

    private Attachment(Block block) {
        this.block = Objects.requireNonNull(block, "block is required");
    }

    public static Attachment fromPath(Path path) {
        Objects.requireNonNull(path, "path is required");
        Path fileName = path.getFileName();
        return fromPath(path, inferMimeType(fileName == null ? null : fileName.toString()));
    }

    public static Attachment fromPath(Path path, String mimeType) {
        Objects.requireNonNull(path, "path is required");
        try {
            byte[] bytes = Files.readAllBytes(path);
            Path fileNamePath = path.getFileName();
            String fileName = fileNamePath == null ? null : fileNamePath.toString();
            String resolvedMime = (mimeType == null || mimeType.trim().isEmpty())
                    ? inferMimeType(fileName) : mimeType;
            return fromBytes(bytes, resolvedMime, fileName);
        } catch (IOException e) {
            throw new Ai4jException("Failed to read attachment path: " + path, e);
        }
    }

    public static Attachment fromFile(File file) {
        Objects.requireNonNull(file, "file is required");
        return fromPath(file.toPath());
    }

    public static Attachment fromFile(File file, String mimeType) {
        Objects.requireNonNull(file, "file is required");
        return fromPath(file.toPath(), mimeType);
    }

    public static Attachment fromBytes(byte[] bytes, String mimeType) {
        return fromBytes(bytes, mimeType, null);
    }

    public static Attachment fromBytes(byte[] bytes, String mimeType, String title) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        String resolvedMime = requireMimeType(mimeType);
        String base64 = Base64.getEncoder().encodeToString(bytes);
        return fromMediaRef(MediaRef.inlineBase64(base64, resolvedMime), resolvedMime, title);
    }

    public static Attachment fromInputStream(InputStream stream, String mimeType) {
        return fromInputStream(stream, mimeType, null);
    }

    public static Attachment fromInputStream(InputStream stream, String mimeType, String title) {
        Objects.requireNonNull(stream, "stream is required");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = stream.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return fromBytes(out.toByteArray(), mimeType, title);
        } catch (IOException e) {
            throw new Ai4jException("Failed to read attachment stream", e);
        }
    }

    public static Attachment fromBase64(String base64, String mimeType) {
        Objects.requireNonNull(base64, "base64 is required");
        String resolvedMime = requireMimeType(mimeType);
        return fromMediaRef(MediaRef.inlineBase64(base64, resolvedMime), resolvedMime, null);
    }

    public static Attachment fromUri(URI uri, String mimeType) {
        Objects.requireNonNull(uri, "uri is required");
        String resolvedMime = (mimeType == null || mimeType.trim().isEmpty())
                ? inferMimeType(uri.getPath()) : mimeType;
        return fromMediaRef(MediaRef.url(uri.toString(), resolvedMime), resolvedMime, null);
    }

    public static Attachment fromUrl(String url, String mimeType) {
        Objects.requireNonNull(url, "url is required");
        String resolvedMime = (mimeType == null || mimeType.trim().isEmpty())
                ? inferMimeType(url) : mimeType;
        return fromMediaRef(MediaRef.url(url, resolvedMime), resolvedMime, null);
    }

    public static Attachment fromProviderFileId(String fileId, String mimeType) {
        Objects.requireNonNull(fileId, "fileId is required");
        String resolvedMime = requireMimeType(mimeType);
        return fromMediaRef(MediaRef.providerFileId(fileId, resolvedMime), resolvedMime, null);
    }

    public Block toBlock() {
        return block;
    }

    private static Attachment fromMediaRef(MediaRef media, String mimeType, String title) {
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("image/")) {
            return new Attachment(ImageBlock.of(media));
        }
        if (lower.startsWith("audio/")) {
            return new Attachment(AudioBlock.of(media));
        }
        if (lower.startsWith("video/")) {
            return new Attachment(VideoBlock.of(media));
        }
        return new Attachment(DocumentBlock.of(media, mimeType, title, null, null, null));
    }

    private static String requireMimeType(String mimeType) {
        if (mimeType == null || mimeType.trim().isEmpty()) {
            throw new IllegalArgumentException("mimeType is required");
        }
        return mimeType.trim();
    }

    static String inferMimeType(String nameOrPath) {
        if (nameOrPath == null) {
            return "application/octet-stream";
        }
        String lower = nameOrPath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }
}
