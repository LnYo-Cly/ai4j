package io.github.lnyocly.ai4j.agent.trace;

import com.alibaba.fastjson2.JSON;

public class ConsoleTraceExporter implements TraceExporter {

    @Override
    public void export(TraceSpan span) {
        if (span == null) {
            return;
        }
        System.out.println("TRACE " + JSON.toJSONString(span));
    }
}
