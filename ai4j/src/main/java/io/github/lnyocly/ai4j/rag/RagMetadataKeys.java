package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.constant.Constants;

public final class RagMetadataKeys {

    public static final String CONTENT = Constants.METADATA_KEY;
    public static final String DOCUMENT_ID = "documentId";
    public static final String CHUNK_ID = "chunkId";
    public static final String SOURCE_NAME = "sourceName";
    public static final String SOURCE_PATH = "sourcePath";
    public static final String SOURCE_URI = "sourceUri";
    public static final String PAGE_NUMBER = "pageNumber";
    public static final String SECTION_TITLE = "sectionTitle";
    public static final String CHUNK_INDEX = "chunkIndex";
    public static final String TENANT = "tenant";
    public static final String BIZ = "biz";
    public static final String VERSION = "version";

    private RagMetadataKeys() {
    }
}
