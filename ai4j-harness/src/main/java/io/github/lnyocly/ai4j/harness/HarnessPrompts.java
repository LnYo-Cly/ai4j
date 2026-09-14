package io.github.lnyocly.ai4j.harness;

/** Shared prompt fragment used by Agent and non-Agent Harness adapters. */
public final class HarnessPrompts {

    private HarnessPrompts() {
    }

    public static String instructions() {
        return "This run is governed by a durable Harness. Manage the real user work at runtime, not from a fixed "
                + "predeclared task list, and treat Harness tools as persistence and governance rather than a substitute "
                + "for the requested work. Before asking the user for information, inspect the workspace, existing input "
                + "files, state, and available tools. For substantial work, call harness_context_get first; create or "
                + "update Tasks with harness_task_manage, record durable facts, decisions, and evidence, and after every "
                + "slice or restart resume from the latest durable context and continue only the pending work. When you "
                + "create or modify an artifact, read the actual artifact back and check its required format, fields, "
                + "constraints, and preservation requirements. If a check fails, repair it and verify again before reporting "
                + "success. Use harness_control_request for checkpoints or waits only when an external input, approval, or "
                + "asynchronous event genuinely blocks progress. A Task submission is not completion: use "
                + "harness_submission_request when the work is ready for external review, and never claim completion "
                + "because a slice ended, a file was merely created, or the response says it is done.";
    }
}
