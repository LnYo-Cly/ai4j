package io.github.lnyocly.ai4j.agent.trace;

import com.alibaba.fastjson2.JSON;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class JsonlTraceExporter implements TraceExporter, AutoCloseable {

    private final Writer writer;
    private final boolean ownsWriter;

    public JsonlTraceExporter(String path) {
        this(path == null ? null : new File(path));
    }

    public JsonlTraceExporter(File file) {
        this(createWriter(file), true);
    }

    public JsonlTraceExporter(Writer writer) {
        this(writer, false);
    }

    private JsonlTraceExporter(Writer writer, boolean ownsWriter) {
        if (writer == null) {
            throw new IllegalArgumentException("writer is required");
        }
        this.writer = writer;
        this.ownsWriter = ownsWriter;
    }

    @Override
    public synchronized void export(TraceSpan span) {
        if (span == null) {
            return;
        }
        try {
            writer.write(JSON.toJSONString(span));
            writer.write(System.lineSeparator());
            writer.flush();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export trace span as JSONL", ex);
        }
    }

    @Override
    public synchronized void close() throws Exception {
        if (ownsWriter) {
            writer.close();
        } else {
            writer.flush();
        }
    }

    private static Writer createWriter(File file) {
        if (file == null) {
            throw new IllegalArgumentException("file is required");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IllegalStateException("Failed to create trace export directory: " + parent.getAbsolutePath());
        }
        try {
            return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to open trace export file: " + file.getAbsolutePath(), ex);
        }
    }
}
