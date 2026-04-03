package io.github.lnyocly.ai4j.flowgram.springboot;

import io.github.lnyocly.ai4j.AiConfigAutoConfiguration;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramLlmNodeRunner;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramTaskStore;
import io.github.lnyocly.ai4j.flowgram.springboot.support.JdbcFlowGramTaskStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = FlowGramJdbcTaskStoreAutoConfigurationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@TestPropertySource(properties = {
        "org.springframework.boot.logging.LoggingSystem=none",
        "ai4j.flowgram.enabled=true",
        "ai4j.flowgram.task-store.type=jdbc",
        "ai4j.flowgram.task-store.table-name=ai4j_flowgram_task_autotest"
})
public class FlowGramJdbcTaskStoreAutoConfigurationTest {

    @Autowired
    private FlowGramTaskStore taskStore;

    @Test
    public void shouldAutoConfigureJdbcTaskStoreWhenDataSourceIsPresent() {
        assertTrue(taskStore instanceof JdbcFlowGramTaskStore);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = AiConfigAutoConfiguration.class)
    public static class TestApplication {

        @Bean
        public DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:ai4j_flowgram_autoconfig;MODE=MYSQL;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        public FlowGramLlmNodeRunner flowGramLlmNodeRunner() {
            return new FlowGramLlmNodeRunner() {
                @Override
                public Map<String, Object> run(io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema node,
                                               Map<String, Object> inputs) {
                    throw new UnsupportedOperationException("not used in auto configuration test");
                }
            };
        }
    }
}
