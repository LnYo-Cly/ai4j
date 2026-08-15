---
sidebar_position: 13
title: "Flowgram Custom Node Extension"
description: "Covers only the backend executor half: the FlowGramNodeExecutor interface, runtime recognition and registration timing, getType protocol-name stability, and the input parsing and context fields the runtime has already completed before execute."
tags: [how-to]
---

# Flowgram Custom Node Extension

This page covers only the backend executor half.

If you already know how to register a node on the frontend, the questions you actually need to answer are:

- How does the runtime recognize your node
- What context does the executor actually receive
- Have the inputs already been parsed
- How do outputs, exceptions, and status flow into the report / result

## 1. There Is Only One Official Backend Extension Point

The core interface is:

```java
public interface FlowGramNodeExecutor {

    String getType();

    FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception;
}
```

This means the official definition of a backend custom node is very clear:

- `type`
- `execute(...)`
- `outputs`

There is no extra "mystery registration mechanism".

## 2. How the Runtime Recognizes Your Node

`FlowGramRuntimeService` natively understands only:

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

Any other type must be registered via `customExecutors` before it is recognized.

### 2.1 When Registration Happens

The most common path is:

- You declare `FlowGramNodeExecutor` as a Spring Bean
- The registrar in `FlowGramAutoConfiguration` registers it with the runtime

If you are not on Spring, you can also do it manually:

- `runtimeService.registerNodeExecutor(...)`

### 2.2 Unsupported Types Are Caught at Validation

If a type is not registered, the `validate` stage may directly report:

- `Unsupported FlowGram node type ...`

This is good, because it surfaces the error before submission, rather than blowing up only when execution reaches the node.

## 3. `getType()` Is More Than Returning a String

Many people underestimate `getType()`.

In practice it is the protocol name that:

- Frontend mapping
- The workflow schema
- Backend executor dispatch
- Historical workflow compatibility

all depend on together.

### Recommended

- Use a stable, uppercase protocol name in the backend `getType()`, e.g. `TRANSFORM`
- Keep the frontend display name and the protocol name separate

### Not Recommended

- Using business copy that changes often as the `type`
- `Transform` today, `TextTransform` tomorrow

## 4. What the Runtime Has Already Done Before Calling the Executor

This point is the most critical, and the easiest to get wrong in documentation.

Before actually invoking the executor, `executeCustomNode(...)` has already done this work:

1. Parsed inputs based on the node `inputsValues`
2. Supported `REF` / `CONSTANT` / `TEMPLATE` / `EXPRESSION`
3. Applied input schema defaults
4. Recorded node inputs
5. Built the `FlowGramNodeExecutionContext`

Therefore:

- `context.inputs` is usually already the fully parsed, execution-ready input
- It is not the raw frontend form JSON

This vastly simplifies implementing a custom executor.

## 5. What Is Actually Inside `FlowGramNodeExecutionContext`

The current context object includes:

- `taskId`
- `node`
- `inputs`
- `taskInputs`
- `nodeOutputs`
- `locals`

### `inputs`

The already-parsed inputs for the current node.

### `taskInputs`

The original root inputs of the whole task.

### `nodeOutputs`

Output snapshots from previously completed nodes.

### `locals`

The current local context, which matters especially in scenarios like loops.

This means an executor generally does not need to maintain global state itself; what it should do is consume the current context and produce stable outputs.

## 6. What a Minimal Backend Node Looks Like

A minimal `TRANSFORM` node can be written like this:

```java
@Bean
public FlowGramNodeExecutor transformNodeExecutor() {
    return new FlowGramNodeExecutor() {
        @Override
        public String getType() {
            return "TRANSFORM";
        }

        @Override
        public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) {
            Map<String, Object> inputs = context == null || context.getInputs() == null
                    ? new LinkedHashMap<String, Object>()
                    : context.getInputs();

            String text = String.valueOf(inputs.get("text"));
            String mode = String.valueOf(inputs.get("mode"));
            String result = "upper".equalsIgnoreCase(mode)
                    ? text.toUpperCase(java.util.Locale.ROOT)
                    : text.toLowerCase(java.util.Locale.ROOT);

            Map<String, Object> outputs = new LinkedHashMap<String, Object>();
            outputs.put("result", result);
            return FlowGramNodeExecutionResult.builder()
                    .outputs(outputs)
                    .build();
        }
    };
}
```

The point of this code is not the "uppercase" behavior itself, but that it reflects the runtime contract:

- Read the already-parsed inputs
- Return a stable outputs map
- Do not care about controller / facade details

## 7. Output Affects Not Only Downstream, but Also the Report / Result

The `outputs` returned by the executor are automatically recorded by the runtime.

This flows into:

- The node-level report
- The workflow outputs aggregation
- The final result

So output design must be stable.

### Recommended

- Put business results under a fixed key, e.g. `result`
- Put the raw external response under `rawResponse`
- Put metrics under `metrics`

### Not Recommended

- Letting the same field change type between runs
- Stuffing logs, business output, and debug info into a single string

## 8. How Exceptions Are Consumed by the System

An exception thrown by the executor is not just logged; it directly affects node and task status.

The current semantics are roughly:

- The node status is marked `failed`
- The node error is recorded
- The workflow status may enter `failed`
- A failure event appears in the corresponding trace

So the exception message of a custom node should be clear enough to at least answer:

- Which input was missing
- Which external dependency was called
- Whether it was a logic error or a timeout error

## 9. What If You Need More Complex Value Parsing

For most ordinary custom nodes, `context.inputs` is enough.

But if your node also needs to parse more complex config objects internally — for example nested templates or custom structs — be clear about one thing:

- The runtime only parses the node's standard inputs into `context.inputs` for you

If your own config structure still embeds template or reference logic, you have to handle it explicitly yourself, or reuse an approach like `FlowGramNodeValueResolver`.

## 10. Design Principles of a Good Executor

### 10.1 Single Responsibility

A node should ideally do one kind of thing; do not do all of these in a single executor:

- Pull data
- Call a model
- Apply rule processing
- Send notifications

### 10.2 Stable Inputs and Outputs

Long-term maintenance cost comes mainly from contract drift, not from the line count of `execute(...)`.

### 10.3 Deterministic Where Possible

A platform node should be more like a stable function, not an unpredictable mini-workflow.

### 10.4 Explicit Failure Semantics

The caller should be able to tell from the error:

- A missing value
- An invalid parameter
- A remote service failure
- A timeout / exhausted retries

## 11. When You Should Write a Custom Executor

A better fit:

- The logic is a stable rule
- You need to integrate with internal enterprise systems
- You need strongly constrained inputs and outputs
- This capability will be reused across multiple workflows

Not necessarily a good fit:

- Just a one-off experiment
- Just simple string concatenation
- Something that actually fits better in `HTTP`, `CODE`, `TOOL`, or `LLM`

## 12. The Boundary Between This Page and Other Pages

- [Custom Nodes](/docs/products/flowgram/custom-nodes)
  covers the overall frontend-backend contract
- [Frontend Custom Node Development](/docs/products/flowgram/frontend-custom-node-development)
  covers the frontend registry, form, and type map
- This page
  covers how the runtime invokes the backend executor you wrote

If you remember only one sentence:

The essence of a backend custom node is encapsulating a stable capability into an executor that the runtime can schedule, the report can observe, and the result can reuse.
