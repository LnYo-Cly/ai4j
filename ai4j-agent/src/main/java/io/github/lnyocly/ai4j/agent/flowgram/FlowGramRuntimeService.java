package io.github.lnyocly.ai4j.agent.flowgram;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramEdgeSchema;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskCancelOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskReportOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskResultOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskRunInput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskRunOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskValidateOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramWorkflowSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FlowGramRuntimeService implements AutoCloseable {

    private static final String NODE_TYPE_START = "START";
    private static final String NODE_TYPE_END = "END";
    private static final String NODE_TYPE_LLM = "LLM";
    private static final String NODE_TYPE_CONDITION = "CONDITION";
    private static final String NODE_TYPE_LOOP = "LOOP";

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_CANCELED = "canceled";

    private final FlowGramLlmNodeRunner llmNodeRunner;
    private final ExecutorService executorService;
    private final boolean ownsExecutor;
    private final List<FlowGramRuntimeListener> listeners = new CopyOnWriteArrayList<FlowGramRuntimeListener>();
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<String, TaskRecord>();
    private final ConcurrentMap<String, FlowGramNodeExecutor> customExecutors =
            new ConcurrentHashMap<String, FlowGramNodeExecutor>();

    public FlowGramRuntimeService(FlowGramLlmNodeRunner llmNodeRunner) {
        this(llmNodeRunner, createExecutor(), true);
    }

    public FlowGramRuntimeService(FlowGramLlmNodeRunner llmNodeRunner, ExecutorService executorService) {
        this(llmNodeRunner, executorService, false);
    }

    private FlowGramRuntimeService(FlowGramLlmNodeRunner llmNodeRunner,
                                   ExecutorService executorService,
                                   boolean ownsExecutor) {
        this.llmNodeRunner = llmNodeRunner;
        this.executorService = executorService == null ? createExecutor() : executorService;
        this.ownsExecutor = executorService == null || ownsExecutor;
    }

    public FlowGramRuntimeService registerNodeExecutor(FlowGramNodeExecutor executor) {
        if (executor == null || isBlank(executor.getType())) {
            return this;
        }
        customExecutors.put(normalizeType(executor.getType()), executor);
        return this;
    }

    public FlowGramRuntimeService registerListener(FlowGramRuntimeListener listener) {
        if (listener == null) {
            return this;
        }
        listeners.add(listener);
        return this;
    }

    public FlowGramTaskRunOutput runTask(FlowGramTaskRunInput input) {
        ParsedTask parsed = parseAndValidate(input);
        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, parsed.schema, parsed.nodeIndex, safeMap(input == null ? null : input.getInputs()));
        tasks.put(taskId, record);
        record.future = executorService.submit(new Runnable() {
            @Override
            public void run() {
                executeTask(record, parsed.startNode);
            }
        });
        return FlowGramTaskRunOutput.builder().taskID(taskId).build();
    }

    public FlowGramTaskValidateOutput validateTask(FlowGramTaskRunInput input) {
        List<String> errors = validateInternal(input);
        return FlowGramTaskValidateOutput.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .build();
    }

    public FlowGramTaskReportOutput getTaskReport(String taskId) {
        TaskRecord record = tasks.get(taskId);
        return record == null ? null : record.toReport();
    }

    public FlowGramTaskResultOutput getTaskResult(String taskId) {
        TaskRecord record = tasks.get(taskId);
        return record == null ? null : record.toResult();
    }

    public FlowGramTaskCancelOutput cancelTask(String taskId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null) {
            return FlowGramTaskCancelOutput.builder().success(false).build();
        }
        record.cancelRequested.set(true);
        Future<?> future = record.future;
        if (future != null) {
            future.cancel(true);
        }
        return FlowGramTaskCancelOutput.builder().success(true).build();
    }

    @Override
    public void close() {
        if (ownsExecutor) {
            executorService.shutdownNow();
        }
    }

    private void executeTask(TaskRecord record, FlowGramNodeSchema startNode) {
        record.updateWorkflow(STATUS_PROCESSING, false, null);
        publishTaskEvent(record, FlowGramRuntimeEvent.Type.TASK_STARTED, STATUS_PROCESSING, null);
        try {
            GraphSegment graph = GraphSegment.root(record.schema);
            executeFromNode(record, graph, startNode.getId(), Collections.<String, Object>emptyMap(), new LinkedHashSet<String>());
            if (record.cancelRequested.get()) {
                record.updateWorkflow(STATUS_CANCELED, true, null);
                publishTaskEvent(record, FlowGramRuntimeEvent.Type.TASK_CANCELED, STATUS_CANCELED, null);
                return;
            }
            if (record.result == null) {
                throw new IllegalStateException("FlowGram workflow finished without reaching an End node");
            }
            record.updateWorkflow(STATUS_SUCCESS, true, null);
            publishTaskEvent(record, FlowGramRuntimeEvent.Type.TASK_FINISHED, STATUS_SUCCESS, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            record.updateWorkflow(STATUS_CANCELED, true, null);
            publishTaskEvent(record, FlowGramRuntimeEvent.Type.TASK_CANCELED, STATUS_CANCELED, null);
        } catch (Exception ex) {
            String error = safeMessage(ex);
            record.updateWorkflow(STATUS_FAILED, true, error);
            publishTaskEvent(record, FlowGramRuntimeEvent.Type.TASK_FAILED, STATUS_FAILED, error);
        }
    }

    private Map<String, Object> executeFromNode(TaskRecord record,
                                                GraphSegment graph,
                                                String nodeId,
                                                Map<String, Object> locals,
                                                Set<String> activePath) throws Exception {
        checkCanceled(record);
        if (isBlank(nodeId)) {
            return Collections.emptyMap();
        }
        if (!activePath.add(nodeId)) {
            throw new IllegalStateException("Cycle detected in FlowGram graph at node " + nodeId);
        }
        try {
            FlowGramNodeSchema node = graph.getNode(nodeId);
            if (node == null) {
                throw new IllegalArgumentException("Node not found in graph: " + nodeId);
            }
            Map<String, Object> outputs = executeNode(record, graph, node, locals);
            List<FlowGramEdgeSchema> nextEdges = selectNextEdges(record, graph, node, outputs);
            Map<String, Object> last = outputs;
            for (FlowGramEdgeSchema edge : nextEdges) {
                last = executeFromNode(record, graph, edge.getTargetNodeID(), locals, new LinkedHashSet<String>(activePath));
            }
            return last;
        } finally {
            activePath.remove(nodeId);
        }
    }

    private Map<String, Object> executeNode(TaskRecord record,
                                            GraphSegment graph,
                                            FlowGramNodeSchema node,
                                            Map<String, Object> locals) throws Exception {
        checkCanceled(record);
        String nodeId = node.getId();
        record.recordNodeOutputs(nodeId, Collections.<String, Object>emptyMap());
        record.updateNode(nodeId, STATUS_PROCESSING, null, false);
        publishNodeEvent(record, nodeId, FlowGramRuntimeEvent.Type.NODE_STARTED, STATUS_PROCESSING, null);
        try {
            Map<String, Object> outputs;
            String type = normalizeType(node.getType());
            if (NODE_TYPE_START.equals(type)) {
                outputs = executeStartNode(record, node);
            } else if (NODE_TYPE_END.equals(type)) {
                outputs = executeEndNode(record, node, locals, graph.isTerminalResultGraph());
            } else if (NODE_TYPE_LLM.equals(type)) {
                outputs = executeLlmNode(record, node, locals);
            } else if (NODE_TYPE_CONDITION.equals(type)) {
                outputs = executeConditionNode(record, node, locals);
            } else if (NODE_TYPE_LOOP.equals(type)) {
                outputs = executeLoopNode(record, node, locals);
            } else {
                outputs = executeCustomNode(record, node, locals);
            }
            Map<String, Object> safeOutputs = safeMap(outputs);
            record.recordNodeOutputs(nodeId, safeOutputs);
            record.updateNode(nodeId, STATUS_SUCCESS, null, true);
            publishNodeEvent(record, nodeId, FlowGramRuntimeEvent.Type.NODE_FINISHED, STATUS_SUCCESS, null);
            return safeOutputs;
        } catch (InterruptedException ex) {
            record.recordNodeOutputs(nodeId, Collections.<String, Object>emptyMap());
            record.updateNode(nodeId, STATUS_CANCELED, null, true);
            publishNodeEvent(record, nodeId, FlowGramRuntimeEvent.Type.NODE_CANCELED, STATUS_CANCELED, null);
            throw ex;
        } catch (Exception ex) {
            if (record.cancelRequested.get()) {
                record.recordNodeOutputs(nodeId, Collections.<String, Object>emptyMap());
                record.updateNode(nodeId, STATUS_CANCELED, null, true);
                publishNodeEvent(record, nodeId, FlowGramRuntimeEvent.Type.NODE_CANCELED, STATUS_CANCELED, null);
                throw new InterruptedException("FlowGram task canceled");
            }
            record.recordNodeOutputs(nodeId, Collections.<String, Object>emptyMap());
            String error = safeMessage(ex);
            record.updateNode(nodeId, STATUS_FAILED, error, true);
            publishNodeEvent(record, nodeId, FlowGramRuntimeEvent.Type.NODE_FAILED, STATUS_FAILED, error);
            throw ex;
        }
    }

    private void publishTaskEvent(TaskRecord record,
                                  FlowGramRuntimeEvent.Type type,
                                  String status,
                                  String error) {
        if (record == null || type == null) {
            return;
        }
        publishEvent(FlowGramRuntimeEvent.builder()
                .type(type)
                .timestamp(System.currentTimeMillis())
                .taskId(record.taskId)
                .status(status)
                .error(error)
                .build());
    }

    private void publishNodeEvent(TaskRecord record,
                                  String nodeId,
                                  FlowGramRuntimeEvent.Type type,
                                  String status,
                                  String error) {
        if (record == null || type == null || isBlank(nodeId)) {
            return;
        }
        publishEvent(FlowGramRuntimeEvent.builder()
                .type(type)
                .timestamp(System.currentTimeMillis())
                .taskId(record.taskId)
                .nodeId(nodeId)
                .status(status)
                .error(error)
                .build());
    }

    private void publishEvent(FlowGramRuntimeEvent event) {
        if (event == null || listeners.isEmpty()) {
            return;
        }
        for (FlowGramRuntimeListener listener : listeners) {
            if (listener == null) {
                continue;
            }
            try {
                listener.onEvent(event);
            } catch (RuntimeException ignored) {
                // Listener failures must not break workflow execution.
            }
        }
    }

    private Map<String, Object> executeStartNode(TaskRecord record, FlowGramNodeSchema node) {
        record.recordNodeInputs(node.getId(), record.taskInputs);
        Map<String, Object> outputSchema = schemaMap(node, "outputs");
        Map<String, Object> outputs = applyObjectDefaults(outputSchema, record.taskInputs);
        validateObjectSchema("Start node " + safeNodeId(node) + " inputs", outputSchema, outputs);
        return outputs;
    }

    private Map<String, Object> executeEndNode(TaskRecord record,
                                               FlowGramNodeSchema node,
                                               Map<String, Object> locals,
                                               boolean terminalResultGraph) {
        Map<String, Object> inputs = resolveInputs(record, node, locals);
        record.recordNodeInputs(node.getId(), inputs);
        Map<String, Object> inputSchema = schemaMap(node, "inputs");
        validateObjectSchema("End node " + safeNodeId(node) + " inputs", inputSchema, inputs);
        Map<String, Object> result = safeMap(inputs);
        if (terminalResultGraph) {
            record.result = result;
        }
        return result;
    }

    private Map<String, Object> executeLlmNode(TaskRecord record,
                                               FlowGramNodeSchema node,
                                               Map<String, Object> locals) throws Exception {
        if (llmNodeRunner == null) {
            throw new IllegalStateException("FlowGram LLM node runner is not configured");
        }
        Map<String, Object> inputs = resolveInputs(record, node, locals);
        record.recordNodeInputs(node.getId(), inputs);
        Map<String, Object> inputSchema = schemaMap(node, "inputs");
        validateObjectSchema("LLM node " + safeNodeId(node) + " inputs", inputSchema, inputs);
        Map<String, Object> outputs = llmNodeRunner.run(node, inputs);
        Map<String, Object> outputSchema = schemaMap(node, "outputs");
        validateObjectSchema("LLM node " + safeNodeId(node) + " outputs", outputSchema, outputs);
        return outputs;
    }

    private Map<String, Object> executeConditionNode(TaskRecord record,
                                                     FlowGramNodeSchema node,
                                                     Map<String, Object> locals) {
        Map<String, Object> inputs = resolveInputs(record, node, locals);
        record.recordNodeInputs(node.getId(), inputs);
        Map<String, Object> inputSchema = schemaMap(node, "inputs");
        validateObjectSchema("Condition node " + safeNodeId(node) + " inputs", inputSchema, inputs);
        String matchedBranch = resolveConditionBranch(record, node, locals, inputs);
        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("branchKey", matchedBranch);
        outputs.putAll(inputs);
        return outputs;
    }

    private Map<String, Object> executeLoopNode(TaskRecord record,
                                                FlowGramNodeSchema node,
                                                Map<String, Object> locals) throws Exception {
        Map<String, Object> inputs = resolveInputs(record, node, locals);
        record.recordNodeInputs(node.getId(), inputs);
        Map<String, Object> inputSchema = schemaMap(node, "inputs");
        validateObjectSchema("Loop node " + safeNodeId(node) + " inputs", inputSchema, inputs);

        Object loopFor = inputs.get("loopFor");
        if (!(loopFor instanceof List)) {
            throw new IllegalArgumentException("Loop node " + safeNodeId(node) + " requires loopFor to be an array");
        }
        List<?> items = (List<?>) loopFor;
        Map<String, List<Object>> aggregates = new LinkedHashMap<String, List<Object>>();
        Map<String, Object> loopOutputDefs = mapValue(firstNonNull(dataValue(node, "loopOutputs"), dataValue(node, "outputsValues")));
        GraphSegment loopGraph = GraphSegment.loop(node);

        for (int i = 0; i < items.size(); i++) {
            checkCanceled(record);
            Map<String, Object> iterationLocals = new LinkedHashMap<String, Object>(safeMap(locals));
            Map<String, Object> loopLocals = new LinkedHashMap<String, Object>();
            loopLocals.put("item", copyValue(items.get(i)));
            loopLocals.put("index", i);
            iterationLocals.put(node.getId() + "_locals", loopLocals);

            if (!loopGraph.isEmpty()) {
                List<String> entryNodeIds = loopGraph.entryNodeIds();
                for (String entryNodeId : entryNodeIds) {
                    executeFromNode(record, loopGraph, entryNodeId, iterationLocals, new LinkedHashSet<String>());
                }
            }

            if (loopOutputDefs != null) {
                for (Map.Entry<String, Object> entry : loopOutputDefs.entrySet()) {
                    List<Object> values = aggregates.get(entry.getKey());
                    if (values == null) {
                        values = new ArrayList<Object>();
                        aggregates.put(entry.getKey(), values);
                    }
                    values.add(copyValue(evaluateValue(entry.getValue(), record, iterationLocals)));
                }
            }
        }

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, List<Object>> entry : aggregates.entrySet()) {
            outputs.put(entry.getKey(), entry.getValue());
        }
        Map<String, Object> outputSchema = schemaMap(node, "outputs");
        validateObjectSchema("Loop node " + safeNodeId(node) + " outputs", outputSchema, outputs);
        return outputs;
    }


    private Map<String, Object> executeCustomNode(TaskRecord record,
                                                  FlowGramNodeSchema node,
                                                  Map<String, Object> locals) throws Exception {
        FlowGramNodeExecutor executor = customExecutors.get(normalizeType(node.getType()));
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported FlowGram node type: " + node.getType());
        }
        Map<String, Object> inputs = resolveInputs(record, node, locals);
        record.recordNodeInputs(node.getId(), inputs);
        FlowGramNodeExecutionResult result = executor.execute(FlowGramNodeExecutionContext.builder()
                .taskId(record.taskId)
                .node(node)
                .inputs(inputs)
                .taskInputs(copyMap(record.taskInputs))
                .nodeOutputs(copyMap(record.nodeOutputs))
                .locals(copyMap(locals))
                .build());
        return result == null ? Collections.<String, Object>emptyMap() : safeMap(result.getOutputs());
    }

    private List<FlowGramEdgeSchema> selectNextEdges(TaskRecord record,
                                                     GraphSegment graph,
                                                     FlowGramNodeSchema node,
                                                     Map<String, Object> outputs) {
        if (NODE_TYPE_END.equals(normalizeType(node.getType()))) {
            return Collections.emptyList();
        }
        List<FlowGramEdgeSchema> outgoing = graph.outgoing(node.getId());
        if (outgoing.isEmpty()) {
            return Collections.emptyList();
        }
        if (!NODE_TYPE_CONDITION.equals(normalizeType(node.getType()))) {
            return outgoing;
        }
        String branchKey = valueAsString(outputs.get("branchKey"));
        if (isBlank(branchKey)) {
            return Collections.emptyList();
        }
        List<FlowGramEdgeSchema> matched = new ArrayList<FlowGramEdgeSchema>();
        for (FlowGramEdgeSchema edge : outgoing) {
            String edgeKey = firstNonBlank(edge.getSourcePortID(), edge.getSourcePort());
            if (branchKey.equals(edgeKey)) {
                matched.add(edge);
            }
        }
        return matched;
    }

    private String resolveConditionBranch(TaskRecord record,
                                          FlowGramNodeSchema node,
                                          Map<String, Object> locals,
                                          Map<String, Object> inputs) {
        Object conditionsObject = dataValue(node, "conditions");
        if (conditionsObject instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> rules = (List<Object>) conditionsObject;
            for (Object ruleObject : rules) {
                Map<String, Object> rule = mapValue(ruleObject);
                if (rule == null || !conditionMatches(rule, record, locals, inputs)) {
                    continue;
                }
                String branchKey = firstNonBlank(
                        valueAsString(rule.get("branchKey")),
                        valueAsString(rule.get("key")),
                        valueAsString(rule.get("sourcePortID")),
                        valueAsString(rule.get("sourcePort"))
                );
                if (!isBlank(branchKey)) {
                    return branchKey;
                }
            }
        }

        Object explicit = firstNonNull(inputs.get("branchKey"), inputs.get("sourcePort"), inputs.get("sourcePortID"));
        if (explicit != null) {
            return String.valueOf(explicit);
        }
        Object passed = firstNonNull(inputs.get("passed"), inputs.get("matched"), inputs.get("condition"));
        if (passed instanceof Boolean) {
            return Boolean.TRUE.equals(passed) ? "true" : "false";
        }
        throw new IllegalArgumentException("Condition node " + safeNodeId(node) + " did not resolve any branch");
    }

    private boolean conditionMatches(Map<String, Object> rule,
                                     TaskRecord record,
                                     Map<String, Object> locals,
                                     Map<String, Object> inputs) {
        String operator = normalizeOperator(valueAsString(rule.get("operator")));
        if ("DEFAULT".equals(operator)) {
            return true;
        }

        Object left = firstNonNull(
                evaluateConditionOperand(rule.get("left"), record, locals, inputs),
                evaluateConditionOperand(rule.get("leftValue"), record, locals, inputs),
                resolveInputValue(inputs, valueAsString(firstNonNull(rule.get("leftKey"), rule.get("inputKey"))))
        );
        Object right = firstNonNull(
                evaluateConditionOperand(rule.get("right"), record, locals, inputs),
                evaluateConditionOperand(rule.get("rightValue"), record, locals, inputs),
                rule.get("value")
        );
        return compareCondition(left, operator, right);
    }

    private Object evaluateConditionOperand(Object operand,
                                            TaskRecord record,
                                            Map<String, Object> locals,
                                            Map<String, Object> inputs) {
        if (operand == null) {
            return null;
        }
        if (operand instanceof Map) {
            return evaluateValue(operand, record, locals);
        }
        String key = valueAsString(operand);
        if (!isBlank(key) && inputs.containsKey(key)) {
            return inputs.get(key);
        }
        return operand;
    }

    private boolean compareCondition(Object left, String operator, Object right) {
        if (operator == null) {
            operator = "EQ";
        }
        if ("TRUTHY".equals(operator)) {
            return truthy(left);
        }
        if ("FALSY".equals(operator)) {
            return !truthy(left);
        }
        if ("EQ".equals(operator) || "==".equals(operator)) {
            return valuesEqual(left, right);
        }
        if ("NE".equals(operator) || "!=".equals(operator)) {
            return !valuesEqual(left, right);
        }

        Double leftNumber = valueAsDouble(left);
        Double rightNumber = valueAsDouble(right);
        if (leftNumber != null && rightNumber != null) {
            if ("GT".equals(operator) || ">".equals(operator)) {
                return leftNumber > rightNumber;
            }
            if ("GTE".equals(operator) || ">=".equals(operator)) {
                return leftNumber >= rightNumber;
            }
            if ("LT".equals(operator) || "<".equals(operator)) {
                return leftNumber < rightNumber;
            }
            if ("LTE".equals(operator) || "<=".equals(operator)) {
                return leftNumber <= rightNumber;
            }
        }

        String leftText = valueAsString(left);
        String rightText = valueAsString(right);
        if ("CONTAINS".equals(operator)) {
            return leftText != null && rightText != null && leftText.contains(rightText);
        }
        if ("STARTS_WITH".equals(operator)) {
            return leftText != null && rightText != null && leftText.startsWith(rightText);
        }
        if ("ENDS_WITH".equals(operator)) {
            return leftText != null && rightText != null && leftText.endsWith(rightText);
        }
        return valuesEqual(left, right);
    }

    private Map<String, Object> resolveInputs(TaskRecord record,
                                              FlowGramNodeSchema node,
                                              Map<String, Object> locals) {
        Map<String, Object> rawInputs = mapValue(dataValue(node, "inputsValues"));
        Map<String, Object> resolved = new LinkedHashMap<String, Object>();
        if (rawInputs != null) {
            for (Map.Entry<String, Object> entry : rawInputs.entrySet()) {
                resolved.put(entry.getKey(), copyValue(evaluateValue(entry.getValue(), record, locals)));
            }
        }
        return applyObjectDefaults(schemaMap(node, "inputs"), resolved);
    }

    private Object evaluateValue(Object value, TaskRecord record, Map<String, Object> locals) {
        if (!(value instanceof Map)) {
            return copyValue(value);
        }
        Map<String, Object> valueMap = mapValue(value);
        if (valueMap == null) {
            return copyValue(value);
        }
        String type = normalizeType(valueAsString(valueMap.get("type")));
        if ("REF".equals(type)) {
            return resolveReference(valueMap.get("content"), record, locals);
        }
        if ("CONSTANT".equals(type)) {
            return copyValue(valueMap.get("content"));
        }
        if ("TEMPLATE".equals(type)) {
            return renderTemplate(valueAsString(valueMap.get("content")), record, locals);
        }
        if ("EXPRESSION".equals(type)) {
            return evaluateExpression(valueAsString(valueMap.get("content")), record, locals);
        }
        return copyValue(valueMap.get("content"));
    }

    private Object resolveReference(Object content, TaskRecord record, Map<String, Object> locals) {
        List<Object> path = objectList(content);
        if (path.isEmpty()) {
            return null;
        }
        Object current = resolveRootReference(path.get(0), record, locals);
        for (int i = 1; i < path.size(); i++) {
            current = descend(current, path.get(i));
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object resolveRootReference(Object segment, TaskRecord record, Map<String, Object> locals) {
        String key = valueAsString(segment);
        if (isBlank(key)) {
            return null;
        }
        if (locals != null && locals.containsKey(key)) {
            return locals.get(key);
        }
        if ("inputs".equals(key) || "taskInputs".equals(key) || "$inputs".equals(key)) {
            return record.taskInputs;
        }
        return record.nodeOutputs.get(key);
    }

    private Object evaluateExpression(String expression, TaskRecord record, Map<String, Object> locals) {
        if (isBlank(expression)) {
            return expression;
        }
        String trimmed = expression.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return resolvePathExpression(trimmed.substring(2, trimmed.length() - 1), record, locals);
        }
        if (trimmed.startsWith("{{") && trimmed.endsWith("}}")) {
            return resolvePathExpression(trimmed.substring(2, trimmed.length() - 2), record, locals);
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        Double number = valueAsDouble(trimmed);
        if (number != null) {
            return trimmed.contains(".") ? number : Integer.valueOf(number.intValue());
        }
        return renderTemplate(trimmed, record, locals);
    }

    private String renderTemplate(String template, TaskRecord record, Map<String, Object> locals) {
        if (template == null) {
            return null;
        }
        String rendered = template;
        rendered = replaceTemplatePattern(rendered, "${", "}", record, locals);
        rendered = replaceTemplatePattern(rendered, "{{", "}}", record, locals);
        return rendered;
    }

    private String replaceTemplatePattern(String template,
                                          String prefix,
                                          String suffix,
                                          TaskRecord record,
                                          Map<String, Object> locals) {
        String rendered = template;
        int start = rendered.indexOf(prefix);
        while (start >= 0) {
            int end = rendered.indexOf(suffix, start + prefix.length());
            if (end < 0) {
                break;
            }
            String expr = rendered.substring(start + prefix.length(), end).trim();
            Object value = resolvePathExpression(expr, record, locals);
            rendered = rendered.substring(0, start)
                    + (value == null ? "" : String.valueOf(value))
                    + rendered.substring(end + suffix.length());
            start = rendered.indexOf(prefix, start);
        }
        return rendered;
    }

    private Object resolvePathExpression(String expression, TaskRecord record, Map<String, Object> locals) {
        if (isBlank(expression)) {
            return null;
        }
        List<Object> path = new ArrayList<Object>();
        for (String segment : expression.split("\\.")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty()) {
                path.add(trimmed);
            }
        }
        return resolveReference(path, record, locals);
    }

    private Object descend(Object current, Object segment) {
        if (current == null || segment == null) {
            return null;
        }
        if (current instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) current;
            return map.get(String.valueOf(segment));
        }
        if (current instanceof List) {
            Integer index = valueAsInteger(segment);
            if (index == null) {
                return null;
            }
            List<?> list = (List<?>) current;
            return index >= 0 && index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    private ParsedTask parseAndValidate(FlowGramTaskRunInput input) {
        List<String> errors = validateInternal(input);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }
        FlowGramWorkflowSchema schema = parseSchema(input);
        Map<String, FlowGramNodeSchema> index = new LinkedHashMap<String, FlowGramNodeSchema>();
        collectNodes(schema.getNodes(), index, new ArrayList<String>());
        return new ParsedTask(schema, index, findSingleStart(index.values()));
    }

    private List<String> validateInternal(FlowGramTaskRunInput input) {
        List<String> errors = new ArrayList<String>();
        FlowGramWorkflowSchema schema = parseSchema(input, errors);
        if (schema == null) {
            return errors;
        }

        Map<String, FlowGramNodeSchema> nodeIndex = new LinkedHashMap<String, FlowGramNodeSchema>();
        collectNodes(schema.getNodes(), nodeIndex, errors);
        validateGraph("workflow", schema.getNodes(), schema.getEdges(), true, errors);
        validateNodeDefinitions(schema.getNodes(), nodeIndex, errors);

        FlowGramNodeSchema startNode = findSingleStart(nodeIndex.values());
        if (startNode != null) {
            Map<String, Object> startSchema = schemaMap(startNode, "outputs");
            Map<String, Object> inputs = applyObjectDefaults(startSchema, safeMap(input == null ? null : input.getInputs()));
            collectObjectSchemaErrors("workflow inputs", startSchema, inputs, errors);
        }

        return errors;
    }

    private FlowGramWorkflowSchema parseSchema(FlowGramTaskRunInput input) {
        List<String> errors = new ArrayList<String>();
        FlowGramWorkflowSchema schema = parseSchema(input, errors);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }
        return schema;
    }

    private FlowGramWorkflowSchema parseSchema(FlowGramTaskRunInput input, List<String> errors) {
        if (input == null || isBlank(input.getSchema())) {
            errors.add("FlowGram schema is required");
            return null;
        }
        try {
            FlowGramWorkflowSchema schema = JSON.parseObject(input.getSchema(), FlowGramWorkflowSchema.class);
            if (schema == null || schema.getNodes() == null || schema.getNodes().isEmpty()) {
                errors.add("FlowGram schema must contain at least one node");
                return null;
            }
            return schema;
        } catch (Exception ex) {
            errors.add("Failed to parse FlowGram schema: " + safeMessage(ex));
            return null;
        }
    }

    private void validateGraph(String graphName,
                               List<FlowGramNodeSchema> nodes,
                               List<FlowGramEdgeSchema> edges,
                               boolean rootGraph,
                               List<String> errors) {
        List<FlowGramNodeSchema> safeNodes = nodes == null ? Collections.<FlowGramNodeSchema>emptyList() : nodes;
        Map<String, FlowGramNodeSchema> localIndex = new LinkedHashMap<String, FlowGramNodeSchema>();
        int startCount = 0;
        int endCount = 0;
        for (FlowGramNodeSchema node : safeNodes) {
            if (node == null) {
                continue;
            }
            localIndex.put(node.getId(), node);
            String type = normalizeType(node.getType());
            if (NODE_TYPE_START.equals(type)) {
                startCount++;
            }
            if (NODE_TYPE_END.equals(type)) {
                endCount++;
            }
            if (!isSupportedType(type)) {
                errors.add("Unsupported FlowGram node type '" + node.getType() + "' at node " + safeNodeId(node));
            }
            if (NODE_TYPE_LOOP.equals(type)) {
                validateGraph("loop:" + safeNodeId(node), node.getBlocks(), node.getEdges(), false, errors);
            }
        }

        if (rootGraph) {
            if (startCount != 1) {
                errors.add("FlowGram workflow must contain exactly one Start node");
            }
            if (endCount < 1) {
                errors.add("FlowGram workflow must contain at least one End node");
            }
        }

        if (edges != null) {
            for (FlowGramEdgeSchema edge : edges) {
                if (edge == null) {
                    continue;
                }
                if (!localIndex.containsKey(edge.getSourceNodeID())) {
                    errors.add("Edge source node not found in " + graphName + ": " + edge.getSourceNodeID());
                }
                if (!localIndex.containsKey(edge.getTargetNodeID())) {
                    errors.add("Edge target node not found in " + graphName + ": " + edge.getTargetNodeID());
                }
            }
        }
    }

    private void validateNodeDefinitions(List<FlowGramNodeSchema> nodes,
                                         Map<String, FlowGramNodeSchema> nodeIndex,
                                         List<String> errors) {
        if (nodes == null) {
            return;
        }
        for (FlowGramNodeSchema node : nodes) {
            if (node == null) {
                continue;
            }
            validateRequiredInputBindings(node, errors);
            validateOutputRefs(node, nodeIndex, errors);
            validateNodeDefinitions(node.getBlocks(), nodeIndex, errors);
        }
    }

    private void validateRequiredInputBindings(FlowGramNodeSchema node, List<String> errors) {
        Map<String, Object> inputSchema = schemaMap(node, "inputs");
        List<String> required = stringList(inputSchema == null ? null : inputSchema.get("required"));
        if (required.isEmpty()) {
            return;
        }
        Map<String, Object> inputsValues = mapValue(dataValue(node, "inputsValues"));
        for (String key : required) {
            if (inputsValues == null || !inputsValues.containsKey(key)) {
                Object defaultValue = propertyDefault(inputSchema, key);
                if (defaultValue == null) {
                    errors.add("Node " + safeNodeId(node) + " is missing required input binding: " + key);
                }
            }
        }
    }

    private void validateOutputRefs(FlowGramNodeSchema node,
                                    Map<String, FlowGramNodeSchema> nodeIndex,
                                    List<String> errors) {
        Map<String, Object> loopOutputs = mapValue(firstNonNull(dataValue(node, "loopOutputs"), dataValue(node, "outputsValues")));
        if (loopOutputs == null) {
            return;
        }
        for (Object value : loopOutputs.values()) {
            validateRefValue(node, value, nodeIndex, errors);
        }
    }

    private void validateRefValue(FlowGramNodeSchema node,
                                  Object rawValue,
                                  Map<String, FlowGramNodeSchema> nodeIndex,
                                  List<String> errors) {
        Map<String, Object> value = mapValue(rawValue);
        if (value == null || !"REF".equals(normalizeType(valueAsString(value.get("type"))))) {
            return;
        }
        List<Object> path = objectList(value.get("content"));
        if (path.isEmpty()) {
            return;
        }
        String root = valueAsString(path.get(0));
        if (isBlank(root) || root.endsWith("_locals") || "inputs".equals(root) || "taskInputs".equals(root) || "$inputs".equals(root)) {
            return;
        }
        if (!nodeIndex.containsKey(root)) {
            errors.add("Node " + safeNodeId(node) + " references unknown node output: " + root);
        }
    }

    private void collectNodes(List<FlowGramNodeSchema> nodes,
                              Map<String, FlowGramNodeSchema> nodeIndex,
                              List<String> errors) {
        if (nodes == null) {
            return;
        }
        for (FlowGramNodeSchema node : nodes) {
            if (node == null) {
                continue;
            }
            if (isBlank(node.getId())) {
                errors.add("FlowGram node id is required");
                continue;
            }
            if (nodeIndex.containsKey(node.getId())) {
                errors.add("Duplicate FlowGram node id: " + node.getId());
                continue;
            }
            nodeIndex.put(node.getId(), node);
            collectNodes(node.getBlocks(), nodeIndex, errors);
        }
    }

    private FlowGramNodeSchema findSingleStart(Iterable<FlowGramNodeSchema> nodes) {
        if (nodes == null) {
            return null;
        }
        FlowGramNodeSchema start = null;
        for (FlowGramNodeSchema node : nodes) {
            if (!NODE_TYPE_START.equals(normalizeType(node.getType()))) {
                continue;
            }
            if (start != null) {
                return null;
            }
            start = node;
        }
        return start;
    }

    private boolean isSupportedType(String type) {
        if (NODE_TYPE_START.equals(type)
                || NODE_TYPE_END.equals(type)
                || NODE_TYPE_LLM.equals(type)
                || NODE_TYPE_CONDITION.equals(type)
                || NODE_TYPE_LOOP.equals(type)) {
            return true;
        }
        return type != null && customExecutors.containsKey(type);
    }

    private void checkCanceled(TaskRecord record) throws InterruptedException {
        if (record.cancelRequested.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("FlowGram task canceled");
        }
    }

    private static ExecutorService createExecutor() {
        AtomicInteger sequence = new AtomicInteger(1);
        return Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "ai4j-flowgram-" + sequence.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private Map<String, Object> applyObjectDefaults(Map<String, Object> schema, Map<String, Object> values) {
        Map<String, Object> resolved = safeMap(values);
        if (schema == null) {
            return resolved;
        }
        Map<String, Object> properties = mapValue(schema.get("properties"));
        if (properties == null) {
            return resolved;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> propertySchema = mapValue(entry.getValue());
            if (propertySchema == null) {
                continue;
            }
            if (!resolved.containsKey(entry.getKey()) && propertySchema.containsKey("default")) {
                resolved.put(entry.getKey(), copyValue(propertySchema.get("default")));
            }
        }
        return resolved;
    }

    private void validateObjectSchema(String label, Map<String, Object> schema, Map<String, Object> value) {
        List<String> errors = new ArrayList<String>();
        collectObjectSchemaErrors(label, schema, value, errors);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }
    }

    private void collectObjectSchemaErrors(String label,
                                           Map<String, Object> schema,
                                           Map<String, Object> value,
                                           List<String> errors) {
        if (schema == null) {
            return;
        }
        if (!"object".equalsIgnoreCase(valueAsString(schema.get("type")))) {
            return;
        }
        Map<String, Object> safeValue = value == null ? Collections.<String, Object>emptyMap() : value;
        for (String required : stringList(schema.get("required"))) {
            if (!safeValue.containsKey(required) || safeValue.get(required) == null) {
                errors.add(label + " is missing required field '" + required + "'");
            }
        }
        Map<String, Object> properties = mapValue(schema.get("properties"));
        if (properties == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!safeValue.containsKey(entry.getKey())) {
                continue;
            }
            collectPropertyErrors(label + "." + entry.getKey(), mapValue(entry.getValue()), safeValue.get(entry.getKey()), errors);
        }
    }

    private void collectPropertyErrors(String label,
                                       Map<String, Object> schema,
                                       Object value,
                                       List<String> errors) {
        if (schema == null || value == null) {
            return;
        }
        String type = valueAsString(schema.get("type"));
        if (!isBlank(type) && !matchesType(type, value)) {
            errors.add(label + " expected " + type + " but got " + actualType(value));
            return;
        }
        Object enumValue = schema.get("enum");
        if (enumValue instanceof List && !((List<?>) enumValue).contains(value)) {
            errors.add(label + " is not in enum " + enumValue);
        }
        if ("object".equalsIgnoreCase(type) && value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>) value;
            collectObjectSchemaErrors(label, schema, child, errors);
            return;
        }
        if ("array".equalsIgnoreCase(type) && value instanceof List) {
            Map<String, Object> itemSchema = mapValue(schema.get("items"));
            if (itemSchema == null) {
                return;
            }
            int index = 0;
            for (Object item : (List<?>) value) {
                collectPropertyErrors(label + "[" + index + "]", itemSchema, item, errors);
                index++;
            }
        }
    }

    private boolean matchesType(String type, Object value) {
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if ("string".equals(normalized)) {
            return value instanceof String;
        }
        if ("number".equals(normalized)) {
            return value instanceof Number;
        }
        if ("integer".equals(normalized)) {
            return value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long;
        }
        if ("boolean".equals(normalized)) {
            return value instanceof Boolean;
        }
        if ("object".equals(normalized)) {
            return value instanceof Map;
        }
        if ("array".equals(normalized)) {
            return value instanceof List;
        }
        return true;
    }

    private Object propertyDefault(Map<String, Object> objectSchema, String key) {
        Map<String, Object> properties = mapValue(objectSchema == null ? null : objectSchema.get("properties"));
        Map<String, Object> propertySchema = mapValue(properties == null ? null : properties.get(key));
        return propertySchema == null ? null : propertySchema.get("default");
    }

    private Map<String, Object> schemaMap(FlowGramNodeSchema node, String key) {
        return mapValue(dataValue(node, key));
    }

    private Object dataValue(FlowGramNodeSchema node, String key) {
        return node == null || node.getData() == null ? null : node.getData().get(key);
    }

    private static Map<String, Object> safeMap(Map<String, Object> value) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (value == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private static Map<String, Object> copyMap(Map<String, ?> value) {
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        if (value == null) {
            return copy;
        }
        for (Map.Entry<String, ?> entry : value.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            Map<?, ?> source = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (List<Object>) value) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        Map<?, ?> source = (Map<?, ?>) value;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> objectList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        return new ArrayList<Object>((List<Object>) value);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (Object item : (List<Object>) value) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private static String normalizeType(String value) {
        return isBlank(value) ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeOperator(String value) {
        if (isBlank(value)) {
            return null;
        }
        String trimmed = value.trim();
        if ("==".equals(trimmed) || "!=".equals(trimmed) || ">".equals(trimmed) || ">=".equals(trimmed)
                || "<".equals(trimmed) || "<=".equals(trimmed)) {
            return trimmed;
        }
        return trimmed.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static String safeNodeId(FlowGramNodeSchema node) {
        return node == null || isBlank(node.getId()) ? "(unknown)" : node.getId();
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || isBlank(throwable.getMessage())) {
            return throwable == null ? null : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean valuesEqual(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0D;
        }
        if (value instanceof String) {
            return !((String) value).trim().isEmpty();
        }
        if (value instanceof List) {
            return !((List<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return !((Map<?, ?>) value).isEmpty();
        }
        return true;
    }

    private static String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double valueAsDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer valueAsInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String actualType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof List) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return "integer";
        }
        if (value instanceof Number) {
            return "number";
        }
        return value.getClass().getSimpleName();
    }

    private static Object resolveInputValue(Map<String, Object> inputs, String key) {
        return inputs == null || isBlank(key) ? null : inputs.get(key);
    }

    private static final class ParsedTask {
        private final FlowGramWorkflowSchema schema;
        private final Map<String, FlowGramNodeSchema> nodeIndex;
        private final FlowGramNodeSchema startNode;

        private ParsedTask(FlowGramWorkflowSchema schema,
                           Map<String, FlowGramNodeSchema> nodeIndex,
                           FlowGramNodeSchema startNode) {
            this.schema = schema;
            this.nodeIndex = nodeIndex;
            this.startNode = startNode;
        }
    }

    private static final class GraphSegment {
        private final Map<String, FlowGramNodeSchema> nodes;
        private final Map<String, List<FlowGramEdgeSchema>> outgoing;
        private final boolean terminalResultGraph;

        private GraphSegment(Map<String, FlowGramNodeSchema> nodes,
                             Map<String, List<FlowGramEdgeSchema>> outgoing,
                             boolean terminalResultGraph) {
            this.nodes = nodes;
            this.outgoing = outgoing;
            this.terminalResultGraph = terminalResultGraph;
        }

        private static GraphSegment root(FlowGramWorkflowSchema schema) {
            return new GraphSegment(indexNodes(schema == null ? null : schema.getNodes()), indexOutgoing(schema == null ? null : schema.getEdges()), true);
        }

        private static GraphSegment loop(FlowGramNodeSchema node) {
            return new GraphSegment(indexNodes(node == null ? null : node.getBlocks()), indexOutgoing(node == null ? null : node.getEdges()), false);
        }

        private FlowGramNodeSchema getNode(String nodeId) {
            return nodes.get(nodeId);
        }

        private List<FlowGramEdgeSchema> outgoing(String nodeId) {
            List<FlowGramEdgeSchema> edges = outgoing.get(nodeId);
            return edges == null ? Collections.<FlowGramEdgeSchema>emptyList() : edges;
        }

        private List<String> entryNodeIds() {
            if (nodes.isEmpty()) {
                return Collections.emptyList();
            }
            Set<String> incoming = new LinkedHashSet<String>();
            for (List<FlowGramEdgeSchema> edges : outgoing.values()) {
                if (edges == null) {
                    continue;
                }
                for (FlowGramEdgeSchema edge : edges) {
                    if (edge != null && !isBlank(edge.getTargetNodeID())) {
                        incoming.add(edge.getTargetNodeID());
                    }
                }
            }
            List<String> roots = new ArrayList<String>();
            for (String nodeId : nodes.keySet()) {
                if (!incoming.contains(nodeId)) {
                    roots.add(nodeId);
                }
            }
            return roots.isEmpty() ? new ArrayList<String>(nodes.keySet()) : roots;
        }

        private boolean isEmpty() {
            return nodes.isEmpty();
        }

        private boolean isTerminalResultGraph() {
            return terminalResultGraph;
        }

        private static Map<String, FlowGramNodeSchema> indexNodes(List<FlowGramNodeSchema> nodes) {
            Map<String, FlowGramNodeSchema> index = new LinkedHashMap<String, FlowGramNodeSchema>();
            if (nodes == null) {
                return index;
            }
            for (FlowGramNodeSchema node : nodes) {
                if (node == null || isBlank(node.getId())) {
                    continue;
                }
                index.put(node.getId(), node);
            }
            return index;
        }

        private static Map<String, List<FlowGramEdgeSchema>> indexOutgoing(List<FlowGramEdgeSchema> edges) {
            Map<String, List<FlowGramEdgeSchema>> index = new LinkedHashMap<String, List<FlowGramEdgeSchema>>();
            if (edges == null) {
                return index;
            }
            for (FlowGramEdgeSchema edge : edges) {
                if (edge == null || isBlank(edge.getSourceNodeID()) || isBlank(edge.getTargetNodeID())) {
                    continue;
                }
                List<FlowGramEdgeSchema> outgoing = index.get(edge.getSourceNodeID());
                if (outgoing == null) {
                    outgoing = new ArrayList<FlowGramEdgeSchema>();
                    index.put(edge.getSourceNodeID(), outgoing);
                }
                outgoing.add(edge);
            }
            return index;
        }
    }

    private static final class TaskRecord {
        private final String taskId;
        private final FlowGramWorkflowSchema schema;
        private final Map<String, FlowGramNodeSchema> nodeIndex;
        private final Map<String, Object> taskInputs;
        private final Map<String, Map<String, Object>> nodeInputs =
                new ConcurrentHashMap<String, Map<String, Object>>();
        private final Map<String, Map<String, Object>> nodeOutputs =
                new ConcurrentHashMap<String, Map<String, Object>>();
        private final ConcurrentMap<String, FlowGramTaskReportOutput.NodeStatus> nodeStatuses =
                new ConcurrentHashMap<String, FlowGramTaskReportOutput.NodeStatus>();
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

        private volatile String workflowStatus = STATUS_PENDING;
        private volatile boolean terminated = false;
        private volatile String workflowError;
        private volatile Long workflowStartTime;
        private volatile Long workflowEndTime;
        private volatile Future<?> future;
        private volatile Map<String, Object> result;

        private TaskRecord(String taskId,
                           FlowGramWorkflowSchema schema,
                           Map<String, FlowGramNodeSchema> nodeIndex,
                           Map<String, Object> taskInputs) {
            this.taskId = taskId;
            this.schema = schema;
            this.nodeIndex = nodeIndex == null ? Collections.<String, FlowGramNodeSchema>emptyMap() : nodeIndex;
            this.taskInputs = taskInputs == null ? Collections.<String, Object>emptyMap() : taskInputs;
            for (String nodeId : this.nodeIndex.keySet()) {
                nodeStatuses.put(nodeId, FlowGramTaskReportOutput.NodeStatus.builder()
                        .status(STATUS_PENDING)
                        .build());
            }
        }

        private void updateNode(String nodeId, String status, String error, boolean finished) {
            FlowGramTaskReportOutput.NodeStatus current = nodeStatuses.get(nodeId);
            long now = System.currentTimeMillis();
            FlowGramTaskReportOutput.NodeStatus.NodeStatusBuilder builder = current == null
                    ? FlowGramTaskReportOutput.NodeStatus.builder()
                    : current.toBuilder();
            builder.status(status);
            builder.terminated(finished || STATUS_SUCCESS.equals(status)
                    || STATUS_FAILED.equals(status) || STATUS_CANCELED.equals(status));
            if (STATUS_PROCESSING.equals(status) && (current == null || current.getStartTime() == null)) {
                builder.startTime(now);
            }
            if (finished) {
                if (current == null || current.getStartTime() == null) {
                    builder.startTime(now);
                }
                builder.endTime(now);
            }
            builder.error(error);
            nodeStatuses.put(nodeId, builder.build());
        }

        private void recordNodeInputs(String nodeId, Map<String, Object> inputs) {
            nodeInputs.put(nodeId, copyMap(inputs));
        }

        private void recordNodeOutputs(String nodeId, Map<String, Object> outputs) {
            nodeOutputs.put(nodeId, copyMap(outputs));
        }

        private void updateWorkflow(String status, boolean terminated, String error) {
            long now = System.currentTimeMillis();
            this.workflowStatus = status;
            this.terminated = terminated;
            this.workflowError = error;
            if (STATUS_PROCESSING.equals(status) && workflowStartTime == null) {
                workflowStartTime = now;
            }
            if (terminated) {
                if (workflowStartTime == null) {
                    workflowStartTime = now;
                }
                workflowEndTime = now;
            }
        }

        private FlowGramTaskReportOutput toReport() {
            Map<String, FlowGramTaskReportOutput.NodeStatus> snapshot =
                    new LinkedHashMap<String, FlowGramTaskReportOutput.NodeStatus>();
            for (Map.Entry<String, FlowGramTaskReportOutput.NodeStatus> entry : nodeStatuses.entrySet()) {
                FlowGramTaskReportOutput.NodeStatus value = entry.getValue();
                snapshot.put(entry.getKey(), value == null ? null : value.toBuilder()
                        .inputs(copyMap(nodeInputs.get(entry.getKey())))
                        .outputs(copyMap(nodeOutputs.get(entry.getKey())))
                        .build());
            }
            return FlowGramTaskReportOutput.builder()
                    .inputs(copyMap(taskInputs))
                    .outputs(copyMap(result))
                    .workflow(FlowGramTaskReportOutput.WorkflowStatus.builder()
                            .status(workflowStatus)
                            .terminated(terminated)
                            .startTime(workflowStartTime)
                            .endTime(workflowEndTime)
                            .error(workflowError)
                            .build())
                    .nodes(snapshot)
                    .build();
        }

        private FlowGramTaskResultOutput toResult() {
            return FlowGramTaskResultOutput.builder()
                    .status(workflowStatus)
                    .terminated(terminated)
                    .error(workflowError)
                    .result(result == null ? null : copyMap(result))
                    .build();
        }
    }
}

