package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds the Harness management tools to an existing Agent tool registry.
 * Business tools remain owned by the application and are returned unchanged.
 */
public final class HarnessToolRegistry implements AgentToolRegistry {

    private final AgentToolRegistry businessRegistry;
    private final List<Object> managementTools;

    public HarnessToolRegistry(AgentToolRegistry businessRegistry) {
        this.businessRegistry = businessRegistry;
        this.managementTools = createManagementTools();
        assertNoReservedCollision(businessRegistry);
    }

    public AgentToolRegistry getBusinessRegistry() {
        return businessRegistry;
    }

    @Override
    public List<Object> getTools() {
        List<Object> tools = new ArrayList<Object>();
        tools.addAll(managementTools);
        if (businessRegistry != null && businessRegistry.getTools() != null) {
            tools.addAll(businessRegistry.getTools());
        }
        return tools;
    }

    private void assertNoReservedCollision(AgentToolRegistry registry) {
        if (registry == null || registry.getTools() == null) {
            return;
        }
        for (Object item : registry.getTools()) {
            if (!(item instanceof Tool)) {
                continue;
            }
            Tool.Function function = ((Tool) item).getFunction();
            if (function != null && HarnessToolNames.isManagementTool(function.getName())) {
                throw new IllegalArgumentException("Business tool uses reserved Harness name: "
                        + function.getName());
            }
        }
    }

    private List<Object> createManagementTools() {
        List<Object> tools = new ArrayList<Object>();
        tools.add(tool(HarnessToolNames.CONTEXT_GET,
                "Read the durable Harness context for this execution: current task, runnable tasks, waits, facts, decisions and evidence.",
                properties(
                        property("taskId", "string", "Optional task to focus on.")
                ),
                Collections.<String>emptyList()));
        tools.add(tool(HarnessToolNames.TASK_MANAGE,
                "Create, split, update, transition, list, or connect durable Tasks. Tasks are created at runtime; do not assume all work was declared in advance.",
                properties(
                        property("operation", "string", "create, split, update, transition, add_dependency, get, list, or runnable."),
                        property("taskId", "string", "Task id; defaults to the current task where applicable."),
                        property("title", "string", "Task title for create."),
                        property("goal", "string", "Task goal."),
                        property("plan", "string", "Current plan or intended next steps."),
                        property("parentTaskId", "string", "Parent task id for a new task or split."),
                        property("dependencyTaskId", "string", "Task that must finish before taskId can run."),
                        property("idempotencyKey", "string", "Stable retry key for create operations."),
                        property("status", "string", "PLANNED, ACTIVE, WAITING, BLOCKED, or CANCELLED."),
                        property("reason", "string", "Reason for a transition or block."),
                        property("children", "array", "Child task objects used by split."),
                        property("metadata", "object", "Application-neutral task metadata."),
                        property("tags", "array", "Task tags.")
                ),
                Collections.singletonList("operation")));
        tools.add(tool(HarnessToolNames.FACT_RECORD,
                "Record or invalidate a durable fact with its source and evidence references.",
                properties(
                        property("operation", "string", "record or invalidate."),
                        property("factId", "string", "Fact id."),
                        property("taskId", "string", "Optional related task id."),
                        property("statement", "string", "Fact statement for record."),
                        property("source", "string", "Source of the fact."),
                        property("confidence", "string", "Confidence description."),
                        property("evidenceIds", "array", "Evidence ids supporting the fact."),
                        property("reason", "string", "Reason for invalidation."),
                        property("metadata", "object", "Fact metadata.")
                ),
                Collections.singletonList("operation")));
        tools.add(tool(HarnessToolNames.DECISION_PROPOSE,
                "Propose a durable decision, or resolve one only when acting as a permitted non-Agent arbiter.",
                properties(
                        property("operation", "string", "propose or resolve."),
                        property("decisionId", "string", "Decision id for resolve."),
                        property("taskId", "string", "Optional related task id."),
                        property("question", "string", "Question being decided."),
                        property("chosenOption", "string", "Chosen option for a proposal."),
                        property("rationale", "string", "Reasoning for the proposal or resolution."),
                        property("status", "string", "ACCEPTED or REJECTED for resolve."),
                        property("factIds", "array", "Facts used by the decision."),
                        property("evidenceIds", "array", "Evidence used by the decision.")
                ),
                Collections.singletonList("operation")));
        tools.add(tool(HarnessToolNames.EVIDENCE_RECORD,
                "Record durable evidence produced by a tool, model step, test, or external system.",
                properties(
                        property("evidenceId", "string", "Evidence id."),
                        property("taskId", "string", "Optional related task id."),
                        property("executionId", "string", "Optional related execution id."),
                        property("kind", "string", "Evidence kind, such as test, file, api, or observation."),
                        property("location", "string", "Location or command that produced the evidence."),
                        property("summary", "string", "Human-readable evidence summary."),
                        property("contentRef", "string", "Reference to durable content.")
                ),
                Collections.emptyList()));
        tools.add(tool(HarnessToolNames.RELATION_MANAGE,
                "Create or inspect durable relations between Harness entities. Task dependencies and parent relations are checked for cycles.",
                properties(
                        property("operation", "string", "create, add, get, or list."),
                        property("relationId", "string", "Relation id for get."),
                        property("type", "string", "PARENT_OF, DEPENDS_ON, SUPPORTS, DERIVED_FROM, or EVIDENCE_FOR."),
                        property("fromKind", "string", "Source entity kind."),
                        property("fromId", "string", "Source entity id."),
                        property("toKind", "string", "Target entity kind."),
                        property("toId", "string", "Target entity id."),
                        property("metadata", "object", "Application-neutral relation metadata.")
                ),
                Collections.singletonList("operation")));
        tools.add(tool(HarnessToolNames.CONTROL_REQUEST,
                "Request a durable checkpoint, external/user input, asynchronous operation wait, or approval. The host delivers waits; an Agent cannot self-approve.",
                properties(
                        property("operation", "string", "checkpoint, wait, or approval."),
                        property("type", "string", "Wait type: TIME, EXTERNAL_EVENT, ASYNC_OPERATION, APPROVAL, USER_INPUT, or RETRY."),
                        property("waitId", "string", "Optional stable wait id."),
                        property("operationId", "string", "External asynchronous operation id."),
                        property("externalKey", "string", "External event or approval key."),
                        property("toolName", "string", "Tool requiring approval."),
                        property("callId", "string", "Tool call requiring approval."),
                        property("arguments", "string", "Arguments requiring approval."),
                        property("summary", "string", "Checkpoint or wait summary."),
                        property("state", "object", "Checkpoint state."),
                        property("payload", "object", "Wait payload.")
                ),
                Collections.singletonList("operation")));
        tools.add(tool(HarnessToolNames.SUBMISSION_REQUEST,
                "Submit a Task for external review. Submission is not completion; an Agent cannot approve its own submission or mark a Task done.",
                properties(
                        property("taskId", "string", "Task to submit; defaults to the current task."),
                        property("executionId", "string", "Execution that produced the submission."),
                        property("completionClaim", "string", "What the Agent claims is complete."),
                        property("verificationNotes", "string", "Verification performed."),
                        property("deliverables", "array", "Deliverable references."),
                        property("evidenceIds", "array", "Evidence supporting the submission."),
                        property("knownGaps", "array", "Known gaps."),
                        property("residualRisks", "array", "Residual risks.")
                ),
                Collections.emptyList()));
        return Collections.unmodifiableList(toObjectList(tools));
    }

    private Tool tool(String name,
                      String description,
                      Map<String, Tool.Function.Property> properties,
                      List<String> required) {
        Tool.Function.Parameter parameter = new Tool.Function.Parameter(
                "object", properties, required);
        return new Tool("function", new Tool.Function(name, description, parameter));
    }

    private Map<String, Tool.Function.Property> properties(ToolProperty... values) {
        Map<String, Tool.Function.Property> result = new LinkedHashMap<String, Tool.Function.Property>();
        if (values != null) {
            for (ToolProperty value : values) {
                if (value != null) {
                    result.put(value.name, new Tool.Function.Property(value.type, value.description, null, null));
                }
            }
        }
        return result;
    }

    private ToolProperty property(String name, String type, String description) {
        return new ToolProperty(name, type, description);
    }

    private List<Object> toObjectList(List<Object> source) {
        return new ArrayList<Object>(source);
    }

    private static final class ToolProperty {
        private final String name;
        private final String type;
        private final String description;

        private ToolProperty(String name, String type, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
        }
    }
}
