package io.github.lnyocly.ai4j.harness;

import javax.sql.DataSource;
import java.nio.file.Path;

/** Factory and lifecycle wrapper for a durable Harness store. */
public final class HarnessPersistence implements AutoCloseable {

    private final HarnessStore store;
    private final String harnessId;

    private HarnessPersistence(HarnessStore store, String harnessId) {
        this.store = store;
        this.harnessId = harnessId;
    }

    public static HarnessPersistence file(Path directory) {
        return file(FileHarnessConfig.builder().directory(directory).build());
    }

    public static HarnessPersistence file(FileHarnessConfig config) {
        if (config == null || config.getDirectory() == null) {
            throw new IllegalArgumentException("file harness directory is required");
        }
        String harnessId = normalizeHarnessId(config.getHarnessId());
        return new HarnessPersistence(new FileHarnessStore(config.toBuilder().harnessId(harnessId).build()), harnessId);
    }

    public static HarnessPersistence jdbc(DataSource dataSource, String harnessId) {
        String normalized = normalizeHarnessId(harnessId);
        return new HarnessPersistence(new JdbcHarnessStore(dataSource, normalized), normalized);
    }

    public static HarnessPersistence jdbc(DataSource dataSource,
                                          String harnessId,
                                          int journalRetentionVersions) {
        String normalized = normalizeHarnessId(harnessId);
        return new HarnessPersistence(new JdbcHarnessStore(dataSource, normalized,
                journalRetentionVersions), normalized);
    }

    public HarnessStore getStore() {
        return store;
    }

    public String getHarnessId() {
        return harnessId;
    }

    @Override
    public void close() {
        store.close();
    }

    private static String normalizeHarnessId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "default";
        }
        return value.trim();
    }
}
