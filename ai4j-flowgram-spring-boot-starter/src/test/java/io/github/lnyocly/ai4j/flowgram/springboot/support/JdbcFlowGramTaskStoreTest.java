package io.github.lnyocly.ai4j.flowgram.springboot.support;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class JdbcFlowGramTaskStoreTest {

    @Test
    public void shouldPersistAndUpdateTaskState() {
        JdbcFlowGramTaskStore store = new JdbcFlowGramTaskStore(dataSource("store"), "ai4j_flowgram_task_test", true);

        store.save(FlowGramStoredTask.builder()
                .taskId("task-1")
                .creatorId("user-1")
                .tenantId("tenant-1")
                .createdAt(100L)
                .expiresAt(200L)
                .status("pending")
                .terminated(false)
                .resultSnapshot(Collections.<String, Object>singletonMap("step", "start"))
                .build());

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("answer", "ok");
        store.updateState("task-1", "success", true, null, result);

        FlowGramStoredTask task = store.find("task-1");
        assertNotNull(task);
        assertEquals("user-1", task.getCreatorId());
        assertEquals("tenant-1", task.getTenantId());
        assertEquals("success", task.getStatus());
        assertTrue(Boolean.TRUE.equals(task.getTerminated()));
        assertEquals("ok", task.getResultSnapshot().get("answer"));
    }

    private JdbcDataSource dataSource(String suffix) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ai4j_flowgram_" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
