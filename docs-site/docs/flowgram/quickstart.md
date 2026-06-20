---
sidebar_position: 4
---

# Agentic 工作流平台快速开始

这一页的目标不是把所有概念讲完，而是让你用最短路径确认 4 件事：

- starter 是否装配成功
- `/flowgram/tasks/*` 是否真的暴露出来了
- 一个无 LLM 的最小流程是否能跑通
- 一个带 LLM 的流程是否能走完整个 `validate -> run -> result -> report` 链路

## 1. 先明确这页验证的是什么

快速开始验证的是“后端 runtime 是否可用”，不是“前端画布是否已经接通”。

因此最稳的起步方式是：

1. 先直接跑后端 demo
2. 先用 HTTP 请求验证 task API
3. 确认无 LLM / 有 LLM 两条最小链路
4. 最后再接前端画布

这能把问题分成：

- 装配问题
- schema 问题
- 模型服务问题
- 前后端对接问题

## 2. 最短启动路径：直接运行 demo

如果你只是想先确认 runtime 能跑，最快方式不是自己新建项目，而是直接跑仓库里的 demo。

依赖模块：

- `ai4j-flowgram-demo`
- `ai4j-flowgram-spring-boot-starter`

启动命令：

```powershell
$env:ZHIPU_API_KEY="your-key"
cmd /c "mvn -pl ai4j-flowgram-demo -am -DskipTests package"
java -jar ai4j-flowgram-demo/target/ai4j-flowgram-demo-2.1.0.jar
```

默认服务地址：

- 端口：`18080`
- API 根路径：`/flowgram`

## 3. demo 的默认模型配置要先看清楚

这里最容易踩坑，因为 demo 里同时定义了两个 service，但默认值只指向其中一个。

`ai4j-flowgram-demo/src/main/resources/application.yml` 当前配置的是：

- `minimax-coding`
- `glm-coding`

但默认：

- `ai4j.flowgram.default-service-id = minimax-coding`

这意味着：

- 无 LLM 的流程不依赖任何模型 key
- LLM 流程如果不显式指定 `serviceId`，就会默认去找 `minimax-coding`

所以如果你只配置了 `ZHIPU_API_KEY`，但没有 `MINIMAX_API_KEY`，最稳的做法有两个：

1. 临时把 demo 配置中的 `default-service-id` 改成 `glm-coding`
2. 或者在 LLM 节点里显式传 `serviceId = glm-coding`

只填 `modelName` 不够，因为：

- `modelName` 决定用哪个模型
- `serviceId` / `default-service-id` 决定走哪个注册服务

## 4. 先确认接口是否真的起来了

你不必先写复杂 workflow，先看 `validate` 是否能响应即可。

最小请求：

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

如果 runtime 已经装配成功，你会拿到一个结构化的校验错误，而不是 404。

这一步验证的是：

- `FlowGramTaskController` 已挂载
- `FlowGramRuntimeFacade` 已可用
- `FlowGramProtocolAdapter` 可以接收 schema DTO

## 5. 跑一条最小无 LLM 流程

第一条建议永远是 `Start -> End`，因为它完全绕过模型服务问题，只验证任务链和 schema。

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

### 这一步为什么重要

如果这条链跑通，说明至少这些东西是真的：

- 根图结构合法
- `Start` / `End` 语义正常
- `REF` 引用解析正常
- 异步 task 提交正常
- `result` 收口正常

## 6. 结果要轮询，不要假设同步完成

`run` 返回的是 `taskId`，不是最终输出。

可以用和集成测试一致的思路轮询结果：

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

这是正确的消费方式，因为 Flowgram 的默认模型是异步任务，不是同步 RPC。

## 7. 再跑一条最小 LLM 流程

当无 LLM 流程跑通后，再验证模型节点。

这里建议显式传 `serviceId`，避免被 demo 默认值误导。

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

## 8. LLM 流程里你应该重点检查什么

不要只看“有没有结果”，更应该看这些点：

- `result.status` 是否为 `success`
- `result.result.result` 是否有最终文本
- `report.nodes.llm_0.outputs.metrics` 是否带 token 指标
- `report.trace.summary.metrics` 是否被正确汇总

这能同时验证：

- `RegistryBackedFlowGramModelClientResolver`
- `Ai4jFlowGramLlmNodeRunner`
- trace metrics backfill
- `FlowGramTraceView` 投影

## 9. 如果失败，先按这条顺序排查

### 9.1 `validate` 就失败

优先检查：

- `Start` 是否恰好一个
- 是否至少一个 `End`
- `inputsValues` 引用路径是否正确
- 节点 type 是否被后端识别

### 9.2 无 LLM 流程能跑，LLM 流程失败

优先检查：

- `serviceId` 是否指向已注册服务
- `default-service-id` 是否和你准备的 provider key 对上
- `modelName` 是否是该服务支持的模型
- 对应 provider key 是否真的存在

### 9.3 `run` 成功但一直拿不到结束结果

优先检查：

- 是否在轮询 `result`
- 节点 executor 是否卡住
- 外部服务是否超时

## 10. 从 demo 走向自己的工程

当你已经验证下面这 4 件事，就应该离开 demo，进入自己的工程：

1. `validate` 能返回结构化校验结果
2. `Start -> End` 能稳定跑通
3. `LLM` 节点能稳定走通
4. `report` / `result` / `trace` 的读取方式已经清楚

下一步建议阅读：

1. [Architecture](/docs/flowgram/architecture)
2. [Runtime](/docs/flowgram/runtime)
3. [Flowgram API 与运行时](/docs/flowgram/api-and-runtime)
4. [前端工作流如何在后端执行](/docs/flowgram/workflow-execution-pipeline)
5. [Built-in Nodes](/docs/flowgram/built-in-nodes)
