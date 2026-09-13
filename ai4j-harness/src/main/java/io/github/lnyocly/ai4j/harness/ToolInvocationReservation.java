package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of atomically reserving a durable tool invocation.
 *
 * <p>Only the caller that creates the {@link ToolInvocationRecord} may invoke
 * the external tool. A reservation that returns an existing record is a
 * recovery observation, including when that record is still {@link
 * ToolInvocationStatus#STARTED}; it must not replay an operation whose side
 * effect may already have happened.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationReservation {

    private ToolInvocationRecord invocation;
    private boolean created;
}
