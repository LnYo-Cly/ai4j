package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * File-backed Harness store with an authoritative JSON snapshot and an
 * append-only journal. The journal lets the store recover a committed update
 * if the process stops between journal append and snapshot replacement.
 */
public final class FileHarnessStore implements HarnessStore {

    private static final ConcurrentMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<Path, ReentrantLock>();

    private final Path directory;
    private final Path stateFile;
    private final Path journalFile;
    private final Path lockFile;
    private final String harnessId;
    private final long journalCompactionBytes;
    private final ReentrantLock jvmLock;

    public FileHarnessStore(FileHarnessConfig config) {
        if (config == null || config.getDirectory() == null) {
            throw new IllegalArgumentException("file harness directory is required");
        }
        this.directory = config.getDirectory().toAbsolutePath().normalize();
        this.harnessId = config.getHarnessId() == null || config.getHarnessId().trim().isEmpty()
                ? "default" : config.getHarnessId().trim();
        this.journalCompactionBytes = config.getJournalCompactionBytes();
        this.stateFile = directory.resolve("state.json");
        this.journalFile = directory.resolve("journal.jsonl");
        this.lockFile = directory.resolve(".lock");
        this.jvmLock = JVM_LOCKS.computeIfAbsent(directory, ignored -> new ReentrantLock());
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new HarnessStoreException("cannot create Harness directory: " + directory, error);
        }
    }

    @Override
    public HarnessState load() {
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<HarnessState>() {
                @Override
                public HarnessState run() throws IOException {
                    HarnessState recovered = readRecovered();
                    if (!Files.exists(stateFile) || recovered.getVersion() > readSnapshotVersion()) {
                        writeSnapshot(recovered);
                    }
                    return recovered.copy();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    @Override
    public HarnessState update(final HarnessStateMutation mutation) {
        if (mutation == null) {
            throw new IllegalArgumentException("Harness mutation is required");
        }
        jvmLock.lock();
        try {
            return withFileLock(new LockedOperation<HarnessState>() {
                @Override
                public HarnessState run() throws IOException {
                    HarnessState current = readRecovered();
                    HarnessState next = mutation.apply(current.copy());
                    if (next == null) {
                        throw new HarnessStoreException("Harness mutation returned null");
                    }
                    next.ensureCollections();
                    next.setHarnessId(harnessId);
                    next.setVersion(current.getVersion() + 1L);
                    next.setUpdatedAtEpochMs(System.currentTimeMillis());
                    appendJournal(next);
                    writeSnapshot(next);
                    compactJournalIfNeeded();
                    return next.copy();
                }
            });
        } finally {
            jvmLock.unlock();
        }
    }

    private HarnessState readRecovered() throws IOException {
        HarnessState snapshot;
        HarnessStoreException snapshotFailure = null;
        if (!Files.exists(stateFile)) {
            snapshot = HarnessState.empty(harnessId);
        } else {
            try {
                snapshot = readSnapshot();
            } catch (HarnessStoreException error) {
                snapshotFailure = error;
                // A corrupt snapshot may still be recoverable from the
                // append-only journal. Do not expose this empty state unless
                // a complete journal state is actually found below.
                snapshot = HarnessState.empty(harnessId);
            }
        }
        HarnessState latest = snapshot;
        boolean recoveredJournalState = false;
        if (Files.exists(journalFile)) {
            List<String> lines = Files.readAllLines(journalFile, StandardCharsets.UTF_8);
            int lastNonBlank = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).trim().isEmpty()) {
                    lastNonBlank = i;
                }
            }
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    JSONObject envelope = JSON.parseObject(line);
                    if (envelope == null || envelope.get("state") == null) {
                        throw new IllegalArgumentException("journal entry has no state");
                    }
                    long version = envelope.getLongValue("version");
                    Object rawState = envelope.get("state");
                    HarnessState candidate = rawState instanceof String
                            ? JSON.parseObject((String) rawState, HarnessState.class)
                            : JSON.parseObject(JSON.toJSONString(rawState), HarnessState.class);
                    if (candidate == null || candidate.getVersion() != version) {
                        throw new IllegalArgumentException("journal entry version does not match its state");
                    }
                    recoveredJournalState = true;
                    if (version > latest.getVersion()) {
                        latest = candidate;
                    }
                } catch (RuntimeException error) {
                    if (i == lastNonBlank) {
                        // Only the final non-blank line may be torn by a
                        // process crash. Earlier corruption is not safe to
                        // skip because it could hide a durable state change.
                        continue;
                    }
                    throw new HarnessStoreException("cannot recover Harness journal line " + (i + 1), error);
                }
            }
        }
        if (snapshotFailure != null && !recoveredJournalState) {
            throw new HarnessStoreException("Harness snapshot is corrupt and no recoverable journal state exists",
                    snapshotFailure);
        }
        return validateHarness(latest);
    }

    private HarnessState readSnapshot() throws IOException {
        if (!Files.exists(stateFile)) {
            return HarnessState.empty(harnessId);
        }
        try {
            String json = new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8);
            HarnessState state = JSON.parseObject(json, HarnessState.class);
            if (state == null) {
                throw new IllegalArgumentException("snapshot is empty");
            }
            return validateHarness(state);
        } catch (RuntimeException error) {
            if (error instanceof HarnessStoreException) {
                throw (HarnessStoreException) error;
            }
            throw new HarnessStoreException("cannot decode Harness snapshot: " + stateFile, error);
        }
    }

    private long readSnapshotVersion() throws IOException {
        if (!Files.exists(stateFile)) {
            return -1L;
        }
        try {
            HarnessState state = JSON.parseObject(new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8), HarnessState.class);
            return state == null ? -1L : state.getVersion();
        } catch (RuntimeException error) {
            return -1L;
        }
    }

    private HarnessState validateHarness(HarnessState state) {
        if (state == null) {
            return HarnessState.empty(harnessId);
        }
        if (state.getHarnessId() == null || state.getHarnessId().trim().isEmpty()) {
            state.setHarnessId(harnessId);
        } else if (!harnessId.equals(state.getHarnessId())) {
            throw new HarnessStoreException("Harness id mismatch: expected " + harnessId
                    + ", found " + state.getHarnessId());
        }
        state.ensureCollections();
        return state;
    }

    private void appendJournal(HarnessState state) throws IOException {
        JSONObject envelope = new JSONObject();
        envelope.put("version", state.getVersion());
        envelope.put("state", JSON.parseObject(JSON.toJSONString(state)));
        try (BufferedWriter writer = Files.newBufferedWriter(journalFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            writer.write(envelope.toJSONString());
            writer.newLine();
        }
    }

    private void writeSnapshot(HarnessState state) throws IOException {
        Path temporary = directory.resolve("state.json.tmp-" + Thread.currentThread().getId());
        Files.write(temporary, JSON.toJSONString(state).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * The journal protects the append-before-snapshot window. Once the
     * snapshot replacement succeeds it is safe to trim old entries; keeping
     * the journal bounded prevents heartbeat/tool-heavy Harnesses from
     * turning every restart into a full-history replay.
     */
    private void compactJournalIfNeeded() {
        if (journalCompactionBytes <= 0L) {
            return;
        }
        try {
            if (!Files.exists(journalFile) || Files.size(journalFile) <= journalCompactionBytes) {
                return;
            }
            Path temporary = directory.resolve("journal.jsonl.tmp-" + Thread.currentThread().getId());
            Files.write(temporary, new byte[0], StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, journalFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, journalFile, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ignored) {
            // The snapshot is already the committed state. Leaving the old
            // journal in place is safe and the next update can retry trim.
        }
    }

    private <T> T withFileLock(LockedOperation<T> operation) {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return operation.run();
        } catch (HarnessStoreException error) {
            throw error;
        } catch (Exception error) {
            throw new HarnessStoreException("file Harness store operation failed", error);
        }
    }

    private interface LockedOperation<T> {
        T run() throws Exception;
    }
}
