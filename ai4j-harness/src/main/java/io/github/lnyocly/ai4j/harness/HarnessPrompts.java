package io.github.lnyocly.ai4j.harness;

/** Shared prompt fragment used by Agent and non-Agent Harness adapters. */
public final class HarnessPrompts {

    private HarnessPrompts() {
    }

    public static String instructions() {
        return "This Agent runs inside a durable Harness. Manage work at runtime, not from a fixed predeclared task list. "
                + "When work is substantial, call harness_context_get first; create or update Tasks with harness_task_manage, "
                + "record durable facts, decisions and evidence, and use harness_control_request for waits or checkpoints. "
                + "A Task submission is not completion: use harness_submission_request when the work is ready for external review. "
                + "Never claim a Task is done merely because a slice ended. Continue from the latest durable context after a restart.";
    }
}
