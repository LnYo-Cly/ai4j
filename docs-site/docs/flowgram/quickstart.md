---
sidebar_position: 2
---

# Agentic 工作流平台快速开始

本页目标：让你在最短路径下把基于 Flowgram 的 AI4J runtime demo 跑起来，并成功调用一次 `/flowgram/tasks/run`。

---

## 1. 引用 starter

如果你要把这套 Agentic 工作流平台能力接进自己的 Spring Boot 项目，先引入：

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-flowgram-spring-boot-starter</artifactId>
  <version>${project.version}</version>
</dependency>
```

当前 demo 工程就是这样接入的。

---

## 2. 直接运行 demo

最短路径是直接跑仓库里的 demo：

```powershell
$env:ZHIPU_API_KEY="your-key"
mvn -pl ai4j-flowgram-demo -am -DskipTests package
java -jar ai4j-flowgram-demo/target/ai4j-flowgram-demo-2.1.0.jar
```

默认端口和路径：

- 端口：`18080`
- API 根路径：`/flowgram`

---

## 3. demo 默认配置

demo 中默认配置了一个名为 `glm-coding` 的模型服务，并把它作为 Flowgram runtime 的默认 LLM 服务：

```yaml
ai:
  platforms:
    - id: glm-coding
      platform: zhipu
      api-key: ${ZHIPU_API_KEY:}
      api-host: https://open.bigmodel.cn/api/paas/
      chat-completion-url: v4/chat/completions

ai4j:
  flowgram:
    default-service-id: glm-coding
    api:
      base-path: /flowgram
```

如果你换模型，只需要先让 `AiServiceRegistry` 里存在一个能解析到的 service id。

---

## 4. 先跑一个最小无 LLM 流程

这个流程只做一件事：把输入从 `Start` 传到 `End`。

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

Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:18080/flowgram/tasks/run" -ContentType "application/json" -Body $body
```

---

## 5. 再跑一个带 LLM 的流程

demo 自带一个 `LLM` 节点例子。关键点是：

- 节点类型写成 `LLM`
- 给节点传入 `modelName` 和 `prompt`
- 结果通过 `End` 节点回收

完整示例可直接参考：

- [ai4j-flowgram-demo/README.md](https://github.com/LnYo-Cly/ai4j/blob/main/ai4j-flowgram-demo/README.md)

---

## 6. 查看结果与报告

`run` 成功后会先返回 `taskId`，后续常用两个接口：

- `GET /flowgram/tasks/{taskId}/result`
- `GET /flowgram/tasks/{taskId}/report`

建议理解为：

- `result`：看最终状态和最终输出
- `report`：看整条流程的节点输入输出和运行细节

---

## 7. 什么时候该切到自己的工程

如果下面三个条件满足，就可以不再停留在 demo：

- 你已经能稳定跑通 `run -> result`
- 你已经知道自己的默认模型 service id
- 你已经明确要用哪些节点类型

下一步建议看：

1. [Architecture](/docs/flowgram/architecture)
2. [Runtime](/docs/flowgram/runtime)
3. [Built-in Nodes](/docs/flowgram/built-in-nodes)
4. [Custom Nodes](/docs/flowgram/custom-nodes)

