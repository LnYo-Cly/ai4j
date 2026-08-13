---
sidebar_position: 4
title: "Agentic Workflow Platform Quickstart"
description: "Shortest path to confirm starter wiring, task API exposure, and two minimal flows (without and with an LLM) running the full validate -> run -> result -> report chain, plus pitfalls in the demo's default model configuration and a troubleshooting order."
tags: [how-to]
---

# Agentic Workflow Platform Quickstart

The goal of this page is not to exhaust every concept, but to get you, by the shortest path, to confirm four things:

- whether the starter is wired successfully
- whether `/flowgram/tasks/*` is actually exposed
- whether a minimal no-LLM flow can run end to end
- whether a flow with an LLM can complete the full `validate -> run -> result -> report` chain

## 1. First, be clear about what this page verifies

The quickstart verifies that "the backend runtime is usable", not that "the frontend canvas is already wired in".

So the most reliable way to start is:

1. Run the backend demo directly first
2. Use HTTP requests to verify the task API first
3. Confirm the two minimal chains: no-LLM and with-LLM
4. Only then wire up the frontend canvas

This separates problems into:

- wiring problems
- schema problems
- model service problems
- frontend/backend integration problems

## 2. Shortest startup path: run the demo directly

If all you want is to confirm the runtime can run, the fastest way is not to scaffold a new project yourself but to run the demo that lives in the repo.

Dependent modules:

- `ai4j-flowgram-demo`
- `ai4j-flowgram-spring-boot-starter`

Startup command:

```powershell
$env:ZHIPU_API_KEY="your-key"
cmd /c "mvn -pl ai4j-flowgram-demo -am -DskipTests package"
java -jar ai4j-flowgram-demo/target/ai4j-flowgram-demo-2.1.0.jar
```

Default service address:

- Port: `18080`
- API root path: `/flowgram`

## 3. Read the demo's default model configuration carefully first

:::warning demo default model service is minimax-coding
This is the easiest place to stumble, because the demo defines two services at once, but the default value points at only one of them.

`ai4j-flowgram-demo/src/main/resources/application.yml` currently configures:

- `minimax-coding`
- `glm-coding`

But the default is:

- `ai4j.flowgram.default-service-id = minimax-coding`

This means:

- a no-LLM flow does not depend on any model key
- an LLM flow, if it does not explicitly specify a `serviceId`, will default to `minimax-coding`

So if you have only configured `ZHIPU_API_KEY` but not `MINIMAX_API_KEY`, the two safest options are:

1. Temporarily change `default-service-id` in the demo configuration to `glm-coding`
2. Or explicitly pass `serviceId = glm-coding` inside the LLM node
:::

Filling in only `modelName` is not enough, because:

- `modelName` decides which model to use
- `serviceId` / `default-service-id` decides which registered service to route through

## 4. First confirm the endpoint is actually up

You do not need to write a complex workflow first; just check whether `validate` can respond.

Minimal request:

```powershell
$body = @{
  schema = @{
    nodes = @()
    edges = @()
  }
  inputs = @{}
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/validate" -ContentType "application/json" -Body $body
```

If the runtime has been wired successfully, you will get back a structured validation error, not a 404.

What this step verifies:

- `FlowGramTaskController` is mounted
- `FlowGramRuntimeFacade` is available
- `FlowGramProtocolAdapter` can accept the schema DTO

## 5. Run a minimal no-LLM flow

The first one to try is always `Start -> End`, because it bypasses model service issues entirely and only exercises the task chain and schema.

```powershell
$body = @{
  schema = @{
    nodes = @(
      @{
        id = "start_0"
        type = "Start"
        name = "start_0"
        data = @{
          outputs = @{
            type = "object"
            required = @("message")
            properties = @{
              message = @{ type = "string" }
            }
          }
        }
      },
      @{
        id = "end_0"
        type = "End"
        name = "end_0"
        data = @{
          inputs = @{
            type = "object"
            required = @("result")
            properties = @{
              result = @{ type = "string" }
            }
          }
          inputsValues = @{
            result = @{
              type = "ref"
              content = @("start_0", "message")
            }
          }
        }
      }
    )
    edges = @(
      @{
        sourceNodeID = "start_0"
        targetNodeID = "end_0"
      }
    )
  }
  inputs = @{
    message = "hello-flowgram"
  }
} | ConvertTo-Json -Depth 10

$validate = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/validate" -ContentType "application/json" -Body $body
$run = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/run" -ContentType "application/json" -Body $body
```

### Why this step matters

If this chain runs, it proves at least the following are real:

- the root graph structure is valid
- `Start` / `End` semantics are normal
- `REF` reference resolution is normal
- asynchronous task submission is normal
- `result` collection is normal

## 6. Poll for the result; do not assume synchronous completion

`run` returns a `taskId`, not the final output.

You can poll the result using the same approach as the integration tests:

```powershell
function Wait-FlowgramResult {
  param(
    [string]$TaskId,
    [int]$TimeoutMs = 5000
  )

  $deadline = (Get-Date).AddMilliseconds($TimeoutMs)
  while ((Get-Date) -lt $deadline) {
    $result = Invoke-RestMethod -Method Get -Uri ("http://127.0.0.1:18080/flowgram/tasks/" + $TaskId + "/result")
    if ($result.terminated) {
      return $result
    }
    Start-Sleep -Milliseconds 100
  }

  throw "Timed out waiting for Flowgram result: $TaskId"
}

$result = Wait-FlowgramResult -TaskId $run.taskId
$report = Invoke-RestMethod -Method Get -Uri ("http://127.0.0.1:18080/flowgram/tasks/" + $run.taskId + "/report")
```

This is the correct way to consume it, because Flowgram's default model is an asynchronous task, not a synchronous RPC.

## 7. Now run a minimal LLM flow

Once the no-LLM flow runs, validate the model node.

Here it is recommended to pass `serviceId` explicitly, to avoid being misled by the demo default.

```powershell
$body = @{
  schema = @{
    nodes = @(
      @{
        id = "start_0"
        type = "Start"
        name = "start_0"
        data = @{
          outputs = @{
            type = "object"
            required = @("message")
            properties = @{
              message = @{ type = "string" }
            }
          }
        }
      },
      @{
        id = "llm_0"
        type = "LLM"
        name = "llm_0"
        data = @{
          inputs = @{
            type = "object"
            required = @("serviceId", "modelName", "prompt")
            properties = @{
              serviceId = @{ type = "string" }
              modelName = @{ type = "string" }
              prompt = @{ type = "string" }
            }
          }
          outputs = @{
            type = "object"
            required = @("result")
            properties = @{
              result = @{ type = "string" }
            }
          }
          inputsValues = @{
            serviceId = @{
              type = "constant"
              content = "glm-coding"
            }
            modelName = @{
              type = "constant"
              content = "glm-4.7"
            }
            prompt = @{
              type = "ref"
              content = @("start_0", "message")
            }
          }
        }
      },
      @{
        id = "end_0"
        type = "End"
        name = "end_0"
        data = @{
          inputs = @{
            type = "object"
            required = @("result")
            properties = @{
              result = @{ type = "string" }
            }
          }
          inputsValues = @{
            result = @{
              type = "ref"
              content = @("llm_0", "result")
            }
          }
        }
      }
    )
    edges = @(
      @{ sourceNodeID = "start_0"; targetNodeID = "llm_0" }
      @{ sourceNodeID = "llm_0"; targetNodeID = "end_0" }
    )
  }
  inputs = @{
    message = "Please answer with exactly three words: FlowGram spring boot."
  }
} | ConvertTo-Json -Depth 12

$validate = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/validate" -ContentType "application/json" -Body $body
$run = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/run" -ContentType "application/json" -Body $body
$result = Wait-FlowgramResult -TaskId $run.taskId
$report = Invoke-RestMethod -Method Get -Uri ("http://127.0.0.1:18080/flowgram/tasks/" + $run.taskId + "/report")
```

## 8. What you should check closely in the LLM flow

Do not only look at "whether there is a result"; look at these points:

- whether `result.status` is `success`
- whether `result.result.result` has the final text
- whether `report.nodes.llm_0.outputs.metrics` carries token metrics
- whether `report.trace.summary.metrics` is aggregated correctly

This simultaneously verifies:

- `RegistryBackedFlowGramModelClientResolver`
- `Ai4jFlowGramLlmNodeRunner`
- trace metrics backfill
- `FlowGramTraceView` projection

## 9. If it fails, troubleshoot in this order

### 9.1 `validate` fails outright

Check first:

- whether there is exactly one `Start`
- whether there is at least one `End`
- whether the `inputsValues` reference paths are correct
- whether the node type is recognized by the backend

### 9.2 The no-LLM flow runs, but the LLM flow fails

Check first:

- whether `serviceId` points to a registered service
- whether `default-service-id` matches the provider key you prepared
- whether `modelName` is a model the service supports
- whether the corresponding provider key actually exists

### 9.3 `run` succeeds but you never get a terminated result

Check first:

- whether you are polling `result`
- whether the node executor is stuck
- whether an external service is timing out

## 10. From the demo to your own project

Once you have verified the following four things, you should leave the demo and move into your own project:

1. `validate` can return a structured validation result
2. `Start -> End` runs reliably
3. the `LLM` node runs reliably
4. the way to read `report` / `result` / `trace` is already clear

Recommended further reading:

1. [Architecture](/docs/flowgram/architecture)
2. [Runtime](/docs/flowgram/runtime)
3. [Flowgram API and runtime](/docs/flowgram/api-and-runtime)
4. [How a frontend workflow executes on the backend](/docs/flowgram/workflow-execution-pipeline)
5. [Built-in Nodes](/docs/flowgram/built-in-nodes)
