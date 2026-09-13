package io.github.lnyocly.ai4j.harness;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Adversarial reliability lab for {@link FileHarnessStore}. Not a CI test: it
 * is a standalone driver for manual experiments, kept under src/test so it
 * ships with the module but never runs in the default suite.
 *
 * Subcommands:
 *   writer  &lt;dir&gt; &lt;harnessId&gt; &lt;updates&gt; &lt;pauseMs&gt; &lt;haltAfterMs&gt;
 *       Applies &lt;updates&gt; mutations (one task per update), pausing
 *       &lt;pauseMs&gt; between updates, and hard-kills the JVM (Runtime.halt,
 *       the kill -9 analog: no shutdown hooks, no cleanup) after
 *       &lt;haltAfterMs&gt; milliseconds. Prints one "COMMITTED &lt;taskId&gt;"
 *       line per successful update, flushed immediately — so a marker on
 *       disk means update() had returned.
 *   verify  &lt;dir&gt; &lt;harnessId&gt; &lt;markerFile&gt;
 *       Reloads the store after a crash and asserts every COMMITTED marker
 *       survived. Prints "VERIFY OK|MISSING ..." and exits 0/3; exit 2 on
 *       recovery failure (store did not load).
 *   scale   &lt;dir&gt; &lt;harnessId&gt; &lt;tasks&gt; &lt;payloadBytes&gt; &lt;compactionBytes&gt;
 *       Builds &lt;tasks&gt; tasks with a payload of &lt;payloadBytes&gt; bytes,
 *       reports per-update latency percentiles, state/journal sizes, and
 *       load() (replay) timings.
 */
public final class HarnessReliabilityLab {

    private HarnessReliabilityLab() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(64);
        }
        String command = args[0];
        if ("writer".equals(command)) {
            writer(dir(args[1]), args[2], Integer.parseInt(args[3]), Long.parseLong(args[4]), Long.parseLong(args[5]));
        } else if ("verify".equals(command)) {
            verify(dir(args[1]), args[2], Paths.get(args[3]));
        } else if ("scale".equals(command)) {
            scale(dir(args[1]), args[2], Integer.parseInt(args[3]), Integer.parseInt(args[4]), Long.parseLong(args[5]));
        } else {
            usage();
            System.exit(64);
        }
    }

    private static Path dir(String value) {
        return Paths.get(value).toAbsolutePath().normalize();
    }

    private static void usage() {
        System.err.println("usage: HarnessReliabilityLab writer|verify|scale ...");
    }

    private static FileHarnessStore store(Path directory, String harnessId, long compactionBytes) {
        FileHarnessConfig config = FileHarnessConfig.builder()
                .directory(directory)
                .harnessId(harnessId)
                .journalCompactionBytes(compactionBytes)
                .build();
        return new FileHarnessStore(config);
    }

    private static String markerId() {
        String runtime = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        int at = runtime.indexOf('@');
        return (at > 0 ? runtime.substring(0, at) : "jvm") ;
    }

    private static void commitTask(HarnessStore store, String taskId, int payloadBytes) {
        final String payload = payloadBytes <= 0 ? "" : repeat('p', payloadBytes);
        store.update(new HarnessStateMutation() {
            @Override
            public HarnessState apply(HarnessState current) {
                long now = System.currentTimeMillis();
                current.getTasks().put(taskId, TaskRecord.builder()
                        .taskId(taskId)
                        .scopeKey("lab")
                        .title("lab task")
                        .goal("reliability lab")
                        .status(TaskStatus.PLANNED)
                        .createdBy("HarnessReliabilityLab")
                        .createdAtEpochMs(now)
                        .updatedAtEpochMs(now)
                        .version(1L)
                        .metadata(Collections.<String, Object>singletonMap("payload", payload))
                        .build());
                return current;
            }
        });
    }

    private static void writer(Path directory, String harnessId, int updates, long pauseMs, long haltAfterMs) throws Exception {
        final String id = markerId();
        if (haltAfterMs > 0) {
            Thread haltWatchdog = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(haltAfterMs);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    // kill -9 analog: no shutdown hooks, no store cleanup.
                    Runtime.getRuntime().halt(9);
                }
            }, "halt-watchdog");
            haltWatchdog.setDaemon(true);
            haltWatchdog.start();
        }
        HarnessStore store = store(directory, harnessId, 0L);
        for (int i = 0; i < updates; i++) {
            String taskId = "lab-" + id + "-" + i;
            commitTask(store, taskId, 0);
            System.out.println("COMMITTED " + taskId);
            System.out.flush();
            if (pauseMs > 0) {
                Thread.sleep(pauseMs);
            }
        }
        System.out.println("DONE " + id);
        System.out.flush();
    }

    private static void verify(Path directory, String harnessId, Path markerFile) throws Exception {
        TreeSet<String> markers = new TreeSet<String>();
        if (Files.exists(markerFile)) {
            for (String line : Files.readAllLines(markerFile, StandardCharsets.UTF_8)) {
                if (line.startsWith("COMMITTED ")) {
                    markers.add(line.substring("COMMITTED ".length()).trim());
                }
            }
        }
        HarnessState state;
        try {
            state = store(directory, harnessId, 0L).load();
        } catch (RuntimeException error) {
            System.out.println("VERIFY LOAD-FAILED " + error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()).substring(0, Math.min(160, String.valueOf(error.getMessage()).length())));
            System.out.flush();
            System.exit(2);
            return;
        }
        TreeSet<String> missing = new TreeSet<String>(markers);
        missing.removeAll(state.getTasks().keySet());
        if (missing.isEmpty()) {
            System.out.println("VERIFY OK version=" + state.getVersion() + " tasks=" + state.getTasks().size()
                    + " markers=" + markers.size());
            System.out.flush();
        } else {
            System.out.println("VERIFY MISSING version=" + state.getVersion() + " tasks=" + state.getTasks().size()
                    + " markers=" + markers.size() + " missing=" + missing.size() + " " + missing);
            System.out.flush();
            System.exit(3);
        }
    }

    private static void scale(Path directory, String harnessId, int tasks, int payloadBytes, long compactionBytes) {
        HarnessStore store = store(directory, harnessId, compactionBytes);
        long[] samples = new long[tasks];
        long buildStart = System.nanoTime();
        String id = markerId();
        for (int i = 0; i < tasks; i++) {
            final String payload = repeat('p', payloadBytes);
            final String taskId = "scale-" + id + "-" + i;
            long start = System.nanoTime();
            store.update(new HarnessStateMutation() {
                @Override
                public HarnessState apply(HarnessState current) {
                    long now = System.currentTimeMillis();
                    current.getTasks().put(taskId, TaskRecord.builder()
                            .taskId(taskId)
                            .scopeKey("scale")
                            .title("scale task")
                            .status(TaskStatus.PLANNED)
                            .createdAtEpochMs(now)
                            .updatedAtEpochMs(now)
                            .version(1L)
                            .metadata(Collections.<String, Object>singletonMap("payload", payload))
                            .build());
                    return current;
                }
            });
            samples[i] = System.nanoTime() - start;
        }
        long buildNanos = System.nanoTime() - buildStart;
        System.out.println("SCALE tasks=" + tasks + " payloadBytes=" + payloadBytes
                + " compactionBytes=" + compactionBytes
                + " buildMs=" + buildNanos / 1_000_000L
                + " updateP50Ms=" + percentileMs(samples, 0.50)
                + " updateP95Ms=" + percentileMs(samples, 0.95)
                + " updateMaxMs=" + percentileMs(samples, 1.0));
        try {
            Path stateFile = directory.resolve("state.json");
            Path journalFile = directory.resolve("journal.jsonl");
            System.out.println("SIZE state.json=" + Files.size(stateFile)
                    + " journal.jsonl=" + (Files.exists(journalFile) ? Files.size(journalFile) : 0L));
        } catch (Exception ignored) {
            // size reporting is best-effort
        }
        for (int attempt = 1; attempt <= 3; attempt++) {
            long start = System.nanoTime();
            HarnessState state = store.load();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            System.out.println("LOAD attempt=" + attempt + " ms=" + ms + " version=" + state.getVersion()
                    + " tasks=" + state.getTasks().size());
        }
    }

    private static long percentileMs(long[] samples, double quantile) {
        long[] sorted = samples.clone();
        java.util.Arrays.sort(sorted);
        int index = (int) Math.min(sorted.length - 1L, Math.round(quantile * (sorted.length - 1)));
        return sorted[index] / 1_000_000L;
    }

    private static String repeat(char ch, int count) {
        StringBuilder buffer = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            buffer.append(ch);
        }
        return buffer.toString();
    }
}
