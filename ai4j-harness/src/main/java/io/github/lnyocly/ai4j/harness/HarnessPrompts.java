package io.github.lnyocly.ai4j.harness;

/** Shared prompt fragment used by Agent and non-Agent Harness adapters. */
public final class HarnessPrompts {

    private HarnessPrompts() {
    }

    public static String instructions() {
        return "This run is governed by a durable Harness. Manage the real user work at runtime, not from a fixed "
                + "predeclared task list, and treat Harness tools as persistence and governance rather than a substitute "
                + "for the requested work. Before asking the user for information, inspect the workspace, existing input "
                + "files, state, and available tools. Treat every path or file named by the current task or input as an "
                + "authorized workspace pointer: use the configured read, list, or state tools to inspect it before asking "
                + "the user for contents. A path alone is not a reason to ask the user to paste a file. Ask only after a "
                + "tool reports that the item is missing, inaccessible, or unreadable, and state that concrete result. "
                + "For substantial work, call harness_context_get first; create or "
                + "update Tasks with harness_task_manage, record durable facts, decisions, and evidence, and after every "
                + "slice or restart resume from the latest durable context and continue only the pending work. When you "
                + "create or modify an artifact, read the actual artifact back and check its required format, fields, "
                + "constraints, and preservation requirements. If a check fails, repair it and verify again before reporting "
                + "success. Use harness_control_request to record meaningful checkpoints and recovery points; request a "
                + "durable wait only when an external input, approval, or asynchronous event genuinely blocks progress. A Task submission is not completion: use "
                + "harness_submission_request when the work is ready for external review, and never claim completion "
                + "because a slice ended, a file was merely created, or the response says it is done.";
    }

    /**
     * Additional guidance for a session restored from a durable checkpoint.
     * This is deliberately separate so fresh sessions are not told that they
     * have prior state when they do not.
     */
    public static String resumedInstructions() {
        return "This is a resumed Harness execution. Continue from the restored session, durable artifacts, prior tool "
                + "results, and the current input; do not restart the work or request a transcript of information already "
                + "available there. Before asking for any named file or path, call the configured workspace inspection "
                + "tool and use its result. Do not ask for pasted contents merely because the input supplied a path. "
                + "If inspection reports missing, permission denied, or unreadable, report the exact failure and then ask "
                + "for the smallest replacement or clarification needed. After writing an artifact, read the real file back "
                + "and repair any format or constraint failure before proceeding.";
    }
}
