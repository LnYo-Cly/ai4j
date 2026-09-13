package io.github.lnyocly.ai4j.harness;

/**
 * Adapter contract for runtimes whose resumable state is not an
 * {@code AgentSessionSnapshot}.
 *
 * <p>An adapter owns opening/restoring its runtime, invoking one bounded
 * slice, and exporting its state. The surrounding Harness still owns the
 * durable Execution, lease, wait, checkpoint, dependency, review and gate
 * lifecycle. Implementations should return expected runtime failures as a
 * {@link HarnessAdapterExecution}; unexpected infrastructure failures may be
 * thrown and will be classified by the outer Harness.</p>
 */
public interface HarnessExecutionAdapter {

    /** Stable type used to partition adapter state in the Harness ledger. */
    String getAdapterType();

    HarnessExecutionAdapterSession open(HarnessExecutionContext context,
                                        HarnessRunBudget budget,
                                        HarnessAdapterState previousState) throws Exception;

    /**
     * Applies a host-delivered value to the adapter-owned pending state. A
     * false replacement tells the Harness to pass the value as the next input
     * when the wait represents a user or external event rather than a tool
     * result.
     */
    HarnessAdapterDelivery applyDelivery(HarnessAdapterState state,
                                         WaitRecord wait,
                                         Object input);

    default void close() {
    }
}
