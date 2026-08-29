package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.harness.AgentHarness;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.HarnessContract;
import io.github.lnyocly.ai4j.harness.HarnessPersistence;
import io.github.lnyocly.ai4j.harness.HarnessRunBudget;
import io.github.lnyocly.ai4j.harness.HarnessRunListener;
import io.github.lnyocly.ai4j.harness.HarnessRunRequest;
import io.github.lnyocly.ai4j.harness.HarnessRunResult;
import io.github.lnyocly.ai4j.harness.HarnessStore;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

/**
 * Convenient facade for running a {@link CodingAgent} inside a durable
 * Harness. The generic {@link AgentHarness} remains available when an
 * application needs direct access to the adapter protocol or Gateway.
 */
public final class CodingAgentHarness implements AutoCloseable {

    private final AgentHarness harness;

    private CodingAgentHarness(AgentHarness harness) {
        this.harness = harness;
    }

    public static CodingAgentHarness file(Path directory, CodingAgent codingAgent) {
        return builder()
                .codingAgent(codingAgent)
                .persistence(HarnessPersistence.file(directory))
                .build();
    }

    public static CodingAgentHarness file(Path directory,
                                          CodingAgent codingAgent,
                                          HarnessContract contract) {
        return builder()
                .codingAgent(codingAgent)
                .contract(contract)
                .persistence(HarnessPersistence.file(directory))
                .build();
    }

    public static CodingAgentHarness jdbc(DataSource dataSource,
                                          String harnessId,
                                          CodingAgent codingAgent) {
        return builder()
                .codingAgent(codingAgent)
                .persistence(HarnessPersistence.jdbc(dataSource, harnessId))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public CodingAgent getCodingAgent() {
        return ((CodingAgentHarnessExecutionAdapter) harness.getExecutionAdapter()).getCodingAgent();
    }

    public AgentHarness getHarness() {
        return harness;
    }

    public HarnessCommandGateway getGateway() {
        return harness.getGateway();
    }

    public HarnessRunResult run(Object input) {
        return harness.run(input);
    }

    public HarnessRunResult run(String sessionId, Object input) {
        return harness.run(HarnessRunRequest.builder()
                .sessionId(sessionId)
                .input(input)
                .build());
    }

    public HarnessRunResult run(HarnessRunRequest request) {
        return harness.run(request);
    }

    public HarnessRunResult runTask(String taskId, Object input) {
        return harness.runTask(taskId, input);
    }

    public HarnessRunResult resume(String executionId) {
        return harness.resume(executionId);
    }

    public HarnessRunResult resumeTask(String taskId) {
        return harness.resumeTask(taskId);
    }

    public HarnessRunResult deliver(String waitId, Object input) {
        return harness.deliver(waitId, input);
    }

    public List<HarnessRunResult> runReady(HarnessRunBudget budget) {
        return harness.runReady(budget);
    }

    @Override
    public void close() {
        harness.close();
    }

    public static final class Builder {
        private CodingAgent codingAgent;
        private HarnessStore store;
        private HarnessPersistence persistence;
        private HarnessContract contract;
        private HarnessActor actor;
        private String workerId;
        private boolean autoResume = true;
        private HarnessRunListener listener;

        public Builder codingAgent(CodingAgent value) {
            this.codingAgent = value;
            return this;
        }

        public Builder store(HarnessStore value) {
            this.store = value;
            return this;
        }

        public Builder persistence(HarnessPersistence value) {
            this.persistence = value;
            return this;
        }

        public Builder contract(HarnessContract value) {
            this.contract = value;
            return this;
        }

        public Builder actor(HarnessActor value) {
            this.actor = value;
            return this;
        }

        public Builder workerId(String value) {
            this.workerId = value;
            return this;
        }

        public Builder autoResume(boolean value) {
            this.autoResume = value;
            return this;
        }

        public Builder listener(HarnessRunListener value) {
            this.listener = value;
            return this;
        }

        public CodingAgentHarness build() {
            if (codingAgent == null) {
                throw new IllegalStateException("codingAgent is required");
            }
            AgentHarness harness = AgentHarness.builder()
                    .executionAdapter(new CodingAgentHarnessExecutionAdapter(codingAgent))
                    .store(store)
                    .persistence(persistence)
                    .contract(contract)
                    .actor(actor)
                    .workerId(workerId)
                    .autoResume(autoResume)
                    .listener(listener)
                    .build();
            return new CodingAgentHarness(harness);
        }
    }
}
