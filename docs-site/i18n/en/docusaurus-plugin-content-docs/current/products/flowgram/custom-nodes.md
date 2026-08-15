---
title: "Custom Nodes"
description: "Custom nodes are how a capability is formally wired into the Flowgram front-end and back-end execution contract: the back-end FlowGramNodeExecutor extension point, the getType protocol name, input/output contract stability, and the three things that must land together on both sides."
tags: [how-to]
---

# Custom Nodes

The core of a `Custom Nodes` is not "add another node card" — it is formally wiring a capability into the front-end and back-end execution contract of Flowgram.

If you only build a front-end node without a back-end executor, it is just a canvas element; if you only have a back-end executor but no stable schema on the front end, it is also hard for it to become a genuinely usable platform node.

## 1. What the real back-end extension point is

There is only one core extension point on the back end:

```java
public interface FlowGramNodeExecutor {
    String getType();
    FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception;
}
```

This means the formal definition of a custom node on the back end is:

- A stable `type`
- A piece of execution logic based on `FlowGramNodeExecutionContext`
- An `outputs` map as the result

## 2. How the runtime recognizes your node

`FlowGramRuntimeService` only natively understands:

- `START`
- `END`
- `LLM`
- `CONDITION`
- `LOOP`

Any type beyond these must be recognized by the runtime through a registered executor.

### 2.1 The type is checked at validation time

If a node type is neither a runtime built-in type nor registered in `customExecutors`, the `validate` stage will immediately report:

- `Unsupported FlowGram node type ...`

This means a custom node is not "deal with it when execution gets there" — it is part of the schema contract.

### 2.2 How to register under Spring Boot

The most common approach is:

- Implement a `FlowGramNodeExecutor`
- Register it as a Spring Bean
- Let the executor registrar in `FlowGramAutoConfiguration` inject it into the runtime

If you do not go through the starter, you can also register programmatically via `FlowGramRuntimeService.registerNodeExecutor(...)`.

## 3. What is actually inside `FlowGramNodeExecutionContext`

Many docs only say "you get the context" but never say what is in the context. Here we get specific.

The current context fields include:

- `taskId`
- `node`
- `inputs`
- `taskInputs`
- `nodeOutputs`
- `locals`

These objects each answer a different question:

- Which task is currently running
- What the schema of the current node is
- What the already-parsed input of the current node is
- What the root task input is
- What previous nodes produced
- What the current local variables are

So a well-designed custom node usually does not need to rebuild its own global state management.

## 4. The output contract must also be designed deliberately

The executor returns:

```java
FlowGramNodeExecutionResult.builder()
    .outputs(...)
    .build();
```

That is, what a custom node truly exposes to downstream is a `Map<String, Object>`.

This directly affects how downstream references your result, so the output structure must be stable.

### Not recommended

- Returning a string today and a map tomorrow
- Letting the same field be an array sometimes and an object other times
- Mixing debug information with formal business output in one field

### More recommended

- Return formal results under fixed keys
- Put the raw response in a separate `raw*` field
- Put statistics in separate `metrics` / `meta` fields

## 5. A custom node is not just back-end code

For a node to be truly usable, you must complete at least 3 things in parallel.

### 5.1 Front-end node definition

You need to define on the canvas side:

- The node type
- The form schema
- The default data
- How inputs and outputs are rendered

### 5.2 Front-end and back-end protocol alignment

You must ensure that the types and fields the front end sends to the back end match the fields the executor reads.

In particular, check:

- Whether `backend-workflow.ts` needs a type mapping
- Whether this node will be mistakenly filtered out as a UI-only node
- Whether the front-end field names match the field names the back end reads

### 5.3 Back-end executor

Only then comes the actual execution logic:

- Parse inputs
- Call an internal service or an external capability
- Return stable outputs

Missing any one of these, the node is only "half-wired".

## 6. A minimal custom node implementation example

The following example only demonstrates the contract; it does not represent a final business design.

```java
public class EchoNodeExecutor implements FlowGramNodeExecutor {

    @Override
    public String getType() {
        return "ECHO";
    }

    @Override
    public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) {
        Map<String, Object> inputs = context == null || context.getInputs() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(context.getInputs());

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("message", inputs.get("message"));
        outputs.put("taskId", context == null ? null : context.getTaskId());
        return FlowGramNodeExecutionResult.builder()
                .outputs(outputs)
                .build();
    }
}
```

This example illustrates 3 things:

- `type` is the stable protocol name
- The node reads already-parsed inputs and does not necessarily have to handle the raw schema itself
- What downstream gets is a stable outputs map

## 7. If you want the same reference capability as built-in nodes

Multiple executors in the starter use `FlowGramNodeValueResolver`. It supports:

- `REF`
- `CONSTANT`
- `TEMPLATE`
- `EXPRESSION`

If your custom node also needs to:

- Read upstream node results
- Interpolate variables inside templates
- Interpret lightweight expressions

You should consider reusing the same resolution logic instead of treating the input as plain static JSON.

Otherwise a very typical problem appears:

- Built-in nodes can reference `${nodeA.result}`
- Your custom node can only get a literal string

## 8. The principles to hold to most when designing custom nodes

### 8.1 `type` must be stable

Do not treat the display name as the protocol name. Once `type` is depended on by the front-end schema, the back-end executor, and historical workflow graphs at the same time, it has already become a compatibility boundary.

### 8.2 One node does one kind of thing

Do not cram "pull data + apply rules + call a model + send a notification" all into one node. That makes the node both hard to debug and hard to reuse.

### 8.3 Inputs and outputs matter more than implementation details

The long-term maintenance cost of a node comes more from contract drift than from those dozens of lines inside `execute(...)`.

### 8.4 Errors must be clear

When a good node fails, it should let the front end and the platform quickly know:

- What input is missing
- Which external service was called
- Why it failed
- Whether it is worth retrying

### 8.5 Stay deterministic where possible

If a node carries a platform business capability, it should behave more like a stable function than a random actor.

## 9. When not to write a custom node

In some cases writing a node only complicates the system.

Cases where writing a custom node is not recommended first:

- It is actually just simple field assembly — `VARIABLE` is enough
- It is actually just calling an existing HTTP service — `HTTP` is enough
- It is actually just a short script transformation — `CODE` is enough
- It is actually just a single-step model processing — `LLM` is enough

A custom node is worth introducing only when existing nodes cannot express your boundary.

## 10. Common mistakes

### 10.1 Changing only the front end, not the back end

The result is usually that the canvas can drag it out, but `validate` fails immediately.

### 10.2 Changing only the back end, not the front-end schema

The result is usually that the executor exists, but nobody can reliably send the right fields into it.

### 10.3 Using a node to replace a workflow

If a node starts maintaining a complex state machine itself, it usually means that logic should have stayed in the workflow graph rather than being stuffed into a single node.

### 10.4 Unstable output structure

This is the most common and most hidden problem. Once downstream depends on your field paths, the output contract should not change arbitrarily.

## 11. One final judgment criterion

Whether a custom node is well designed is not about how fast its code was written, but whether it satisfies these 4 points:

- Front-end and back-end type alignment
- Stable input and output contract
- Clear execution logic boundary
- Errors and results observable enough

Only when these 4 points are met is it a platform node; otherwise it is just a temporarily stitched-together piece of execution code.
