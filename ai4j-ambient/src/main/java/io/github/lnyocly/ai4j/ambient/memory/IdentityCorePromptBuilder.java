package io.github.lnyocly.ai4j.ambient.memory;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, prefix-cache-stable assembler for the ambient agent's immutable
 * identity core ({@code SOUL.md}, {@code USER.md}, {@code BOUNDARIES.md}) separated
 * cleanly from per-turn dynamic tail sections.
 */
public final class IdentityCorePromptBuilder {

    public static final String SOUL_FILENAME = "SOUL.md";
    public static final String USER_FILENAME = "USER.md";
    public static final String BOUNDARIES_FILENAME = "BOUNDARIES.md";

    private String soul = "";
    private String user = "";
    private String boundaries = "";
    private final Map<String, String> dynamicSections = new LinkedHashMap<String, String>();
    private String dynamicTail = "";

    public IdentityCorePromptBuilder() {
    }

    public static IdentityCorePromptBuilder builder() {
        return new IdentityCorePromptBuilder();
    }

    /**
     * Loads {@code SOUL.md}, {@code USER.md}, and {@code BOUNDARIES.md} from the supplied
     * workspace directory. Missing files resolve to empty sections.
     */
    public static IdentityCorePromptBuilder fromDirectory(Path directory) throws IOException {
        return fromDirectory(directory, false);
    }

    /**
     * Loads {@code SOUL.md}, {@code USER.md}, and {@code BOUNDARIES.md} from the supplied
     * workspace directory, optionally failing when any core file is absent.
     */
    public static IdentityCorePromptBuilder fromDirectory(Path directory, boolean requireAllFiles) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("identity core directory must not be null");
        }
        IdentityCorePromptBuilder builder = new IdentityCorePromptBuilder();
        builder.soul(readMarkdownFile(directory.resolve(SOUL_FILENAME), requireAllFiles));
        builder.user(readMarkdownFile(directory.resolve(USER_FILENAME), requireAllFiles));
        builder.boundaries(readMarkdownFile(directory.resolve(BOUNDARIES_FILENAME), requireAllFiles));
        return builder;
    }

    private static String readMarkdownFile(Path file, boolean required) throws IOException {
        if (!Files.exists(file)) {
            if (required) {
                throw new IllegalArgumentException("missing required identity core file: " + file);
            }
            return "";
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    public IdentityCorePromptBuilder soul(String soulMarkdown) {
        this.soul = AmbientSupport.normalizeMarkdown(soulMarkdown);
        return this;
    }

    public IdentityCorePromptBuilder user(String userMarkdown) {
        this.user = AmbientSupport.normalizeMarkdown(userMarkdown);
        return this;
    }

    public IdentityCorePromptBuilder boundaries(String boundariesMarkdown) {
        this.boundaries = AmbientSupport.normalizeMarkdown(boundariesMarkdown);
        return this;
    }

    public IdentityCorePromptBuilder dynamicSection(String sectionName, String content) {
        String key = AmbientSupport.requireNonBlank(sectionName, "dynamic section name");
        String normalized = AmbientSupport.normalizeMarkdown(content);
        if (normalized.isEmpty()) {
            this.dynamicSections.remove(key);
        } else {
            this.dynamicSections.put(key, normalized);
        }
        return this;
    }

    public IdentityCorePromptBuilder dynamicSections(Map<String, String> sections) {
        if (sections != null) {
            for (Map.Entry<String, String> entry : sections.entrySet()) {
                if (entry != null && entry.getKey() != null) {
                    dynamicSection(entry.getKey(), entry.getValue());
                }
            }
        }
        return this;
    }

    public IdentityCorePromptBuilder clearDynamicSections() {
        this.dynamicSections.clear();
        this.dynamicTail = "";
        return this;
    }

    public IdentityCorePromptBuilder dynamicTail(String rawTail) {
        this.dynamicTail = AmbientSupport.normalizeMarkdown(rawTail);
        return this;
    }

    public AssembledPrompt build() {
        String staticPrefix = renderStaticPrefix(soul, user, boundaries);
        String renderedTail = renderDynamicTail(dynamicSections, dynamicTail);
        String soulDigest = AmbientSupport.sha256Hex(soul);
        String userDigest = AmbientSupport.sha256Hex(user);
        String boundariesDigest = AmbientSupport.sha256Hex(boundaries);
        String prefixDigest = AmbientSupport.sha256Hex(staticPrefix);
        return new AssembledPrompt(
                staticPrefix,
                renderedTail,
                prefixDigest,
                soulDigest,
                userDigest,
                boundariesDigest,
                dynamicSections,
                dynamicTail
        );
    }

    private static String renderStaticPrefix(String soul, String user, String boundaries) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("<identity_core version=\"1\">\n");
        appendBlock(sb, "soul", soul);
        appendBlock(sb, "user", user);
        appendBlock(sb, "boundaries", boundaries);
        sb.append("</identity_core>");
        return sb.toString();
    }

    private static String renderDynamicTail(Map<String, String> sections, String rawTail) {
        boolean hasSections = sections != null && !sections.isEmpty();
        boolean hasTail = rawTail != null && !rawTail.isEmpty();
        if (!hasSections && !hasTail) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("<dynamic_context>\n");
        if (hasSections) {
            for (Map.Entry<String, String> entry : sections.entrySet()) {
                String tag = sanitizeTag(entry.getKey());
                appendBlock(sb, tag, entry.getValue());
            }
        }
        if (hasTail) {
            sb.append(rawTail).append('\n');
        }
        sb.append("</dynamic_context>");
        return sb.toString();
    }

    private static void appendBlock(StringBuilder sb, String tag, String content) {
        sb.append('<').append(tag).append(">\n");
        if (content != null && !content.isEmpty()) {
            sb.append(content).append('\n');
        }
        sb.append("</").append(tag).append(">\n");
    }

    private static String sanitizeTag(String key) {
        StringBuilder sb = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.length() == 0 ? "section" : sb.toString();
    }

    /**
     * Immutable assembled prompt split into a prefix-cache-stable system prefix
     * and a per-turn dynamic tail.
     */
    public static final class AssembledPrompt {
        private final String staticPrefix;
        private final String dynamicTail;
        private final String fullPrompt;
        private final String prefixDigest;
        private final String fullDigest;
        private final String soulDigest;
        private final String userDigest;
        private final String boundariesDigest;
        private final Map<String, String> dynamicSections;
        private final String rawDynamicTail;

        private AssembledPrompt(String staticPrefix,
                                String dynamicTail,
                                String prefixDigest,
                                String soulDigest,
                                String userDigest,
                                String boundariesDigest,
                                Map<String, String> dynamicSections,
                                String rawDynamicTail) {
            this.staticPrefix = staticPrefix;
            this.dynamicTail = dynamicTail == null ? "" : dynamicTail;
            this.fullPrompt = this.dynamicTail.isEmpty()
                    ? staticPrefix
                    : staticPrefix + "\n\n" + this.dynamicTail;
            this.prefixDigest = prefixDigest;
            this.fullDigest = AmbientSupport.sha256Hex(this.fullPrompt);
            this.soulDigest = soulDigest;
            this.userDigest = userDigest;
            this.boundariesDigest = boundariesDigest;
            this.dynamicSections = Collections.unmodifiableMap(new LinkedHashMap<String, String>(dynamicSections));
            this.rawDynamicTail = rawDynamicTail == null ? "" : rawDynamicTail;
        }

        public String getStaticPrefix() {
            return staticPrefix;
        }

        public String getDynamicTail() {
            return dynamicTail;
        }

        public String getFullPrompt() {
            return fullPrompt;
        }

        public String getPrefixDigest() {
            return prefixDigest;
        }

        public String getFullDigest() {
            return fullDigest;
        }

        public String getSoulDigest() {
            return soulDigest;
        }

        public String getUserDigest() {
            return userDigest;
        }

        public String getBoundariesDigest() {
            return boundariesDigest;
        }

        public Map<String, String> getDynamicSections() {
            return dynamicSections;
        }

        public boolean hasSameCachePrefix(AssembledPrompt other) {
            return other != null && prefixDigest.equals(other.prefixDigest);
        }

        /**
         * Returns the static prefix followed by the dynamic tail (when non-empty) so
         * providers supporting explicit prefix caching breakpoints can pin index 0.
         */
        public List<String> toCacheableSections() {
            if (dynamicTail.isEmpty()) {
                return Collections.singletonList(staticPrefix);
            }
            List<String> sections = new ArrayList<String>(2);
            sections.add(staticPrefix);
            sections.add(dynamicTail);
            return Collections.unmodifiableList(sections);
        }

        /**
         * Produces a derived prompt with an updated dynamic section while guaranteeing
         * the static prefix and {@link #getPrefixDigest()} remain unchanged.
         */
        public AssembledPrompt withDynamicSection(String sectionName, String content) {
            String key = AmbientSupport.requireNonBlank(sectionName, "dynamic section name");
            Map<String, String> updated = new LinkedHashMap<String, String>(dynamicSections);
            String normalized = AmbientSupport.normalizeMarkdown(content);
            if (normalized.isEmpty()) {
                updated.remove(key);
            } else {
                updated.put(key, normalized);
            }
            return new AssembledPrompt(
                    staticPrefix,
                    renderDynamicTail(updated, rawDynamicTail),
                    prefixDigest,
                    soulDigest,
                    userDigest,
                    boundariesDigest,
                    updated,
                    rawDynamicTail
            );
        }

        /**
         * Produces a derived prompt with a new raw dynamic tail while keeping the
         * immutable identity prefix byte-for-byte identical.
         */
        public AssembledPrompt withDynamicTail(String rawTail) {
            String normalized = AmbientSupport.normalizeMarkdown(rawTail);
            return new AssembledPrompt(
                    staticPrefix,
                    renderDynamicTail(dynamicSections, normalized),
                    prefixDigest,
                    soulDigest,
                    userDigest,
                    boundariesDigest,
                    dynamicSections,
                    normalized
            );
        }
    }
}
