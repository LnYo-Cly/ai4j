package io.github.lnyocly.ai4j.harness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reserved Function Call names exposed by the Harness runtime. */
public final class HarnessToolNames {

    public static final String CONTEXT_GET = "harness_context_get";
    public static final String TASK_MANAGE = "harness_task_manage";
    public static final String FACT_RECORD = "harness_fact_record";
    public static final String DECISION_PROPOSE = "harness_decision_propose";
    public static final String EVIDENCE_RECORD = "harness_evidence_record";
    public static final String RELATION_MANAGE = "harness_relation_manage";
    public static final String CONTROL_REQUEST = "harness_control_request";
    public static final String SUBMISSION_REQUEST = "harness_submission_request";

    private static final List<String> ORDERED = Collections.unmodifiableList(Arrays.asList(
            CONTEXT_GET,
            TASK_MANAGE,
            FACT_RECORD,
            DECISION_PROPOSE,
            EVIDENCE_RECORD,
            RELATION_MANAGE,
            CONTROL_REQUEST,
            SUBMISSION_REQUEST
    ));

    private HarnessToolNames() {
    }

    public static List<String> all() {
        return Collections.unmodifiableList(new ArrayList<String>(ORDERED));
    }

    public static Set<String> asSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(ORDERED));
    }

    public static boolean isManagementTool(String name) {
        return name != null && asSet().contains(name);
    }
}
