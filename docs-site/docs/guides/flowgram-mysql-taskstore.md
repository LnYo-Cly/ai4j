---
sidebar_position: 3
---

# Flowgram + MySQL：让工作流任务可落库、可恢复、可查询

如果你要把 AI4J 的 Flowgram runtime 真正当成一个后端工作流平台使用，`TaskStore` 不能只放内存。

这页给出一条最直接的生产化路径：

- 前端画布或调用方提交 schema
- `FlowGramTaskController` 接收任务
- `JdbcFlowGramTaskStore` 把任务状态写入 MySQL
- `report / result` 从数据库里恢复任务状态

## 1. 这页解决什么问题

默认内存版 `TaskStore` 适合 demo，但不适合：

- 服务重启后继续查任务
- 多实例部署
- 按任务 ID 查询历史结果
- 平台化接入任务审计

切到 JDBC 后，你会得到：

- 任务状态跨进程保留
- 最终结果和错误信息落库
- 更容易接平台侧权限、归属和报表

## 2. 依赖

```xml
<dependencies>
  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-flowgram-spring-boot-starter</artifactId>
    <version>2.0.0</version>
  </dependency>

  <dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.0.0</version>
  </dependency>

  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

## 3. `application.yml`

下面是一份最小可运行配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ai4j?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456

ai:
  platforms:
    - id: glm-coding
      platform: openai
      api-key: ${MODEL_API_KEY}
      api-host: https://open.bigmodel.cn/api/paas/v4/

ai4j:
  flowgram:
    enabled: true
    default-service-id: glm-coding
    api:
      base-path: /flowgram
    task-store:
      type: jdbc
      table-name: ai4j_flowgram_task
      initialize-schema: true
```

这几个配置最关键：

- `ai.platforms[].id`
- `ai4j.flowgram.default-service-id`
- `ai4j.flowgram.task-store.type=jdbc`

关系是：

- `ai.platforms[].id` 定义了可用模型服务注册名
- Flowgram 的 `LLM` 节点默认会走 `default-service-id`
- `task-store.type=jdbc` 则会自动启用 `JdbcFlowGramTaskStore`

## 4. 自动装配后会发生什么

在上面的配置下，starter 会自动完成：

- 注入 `FlowGramTaskController`
- 注入 `FlowGramRuntimeFacade`
- 注入 `JdbcFlowGramTaskStore`
- 启动时创建表 `ai4j_flowgram_task`

也就是说，你不需要自己手写 `TaskStore` Bean。

## 5. 一条最小工作流请求

你可以先不接前端画布，直接用接口验证后端 runtime。

```bash
curl -X POST "http://127.0.0.1:8080/flowgram/tasks/run" ^
  -H "Content-Type: application/json" ^
  -d "{
    \"schema\": {
      \"nodes\": [
        {
          \"id\": \"start_0\",
          \"type\": \"Start\",
          \"name\": \"start_0\",
          \"data\": {
            \"outputs\": {
              \"type\": \"object\",
              \"required\": [\"message\"],
              \"properties\": {
                \"message\": {\"type\": \"string\"}
              }
            }
          }
        },
        {
          \"id\": \"llm_0\",
          \"type\": \"LLM\",
          \"name\": \"llm_0\",
          \"data\": {
            \"inputs\": {
              \"type\": \"object\",
              \"required\": [\"modelName\", \"prompt\"],
              \"properties\": {
                \"modelName\": {\"type\": \"string\"},
                \"prompt\": {\"type\": \"string\"}
              }
            },
            \"outputs\": {
              \"type\": \"object\",
              \"required\": [\"result\"],
              \"properties\": {
                \"result\": {\"type\": \"string\"}
              }
            },
            \"inputsValues\": {
              \"modelName\": {
                \"type\": \"constant\",
                \"content\": \"glm-4.7\"
              },
              \"prompt\": {
                \"type\": \"ref\",
                \"content\": [\"start_0\", \"message\"]
              }
            }
          }
        },
        {
          \"id\": \"end_0\",
          \"type\": \"End\",
          \"name\": \"end_0\",
          \"data\": {
            \"inputs\": {
              \"type\": \"object\",
              \"required\": [\"result\"],
              \"properties\": {
                \"result\": {\"type\": \"string\"}
              }
            },
            \"inputsValues\": {
              \"result\": {
                \"type\": \"ref\",
                \"content\": [\"llm_0\", \"result\"]
              }
            }
          }
        }
      ],
      \"edges\": [
        {\"sourceNodeID\": \"start_0\", \"targetNodeID\": \"llm_0\"},
        {\"sourceNodeID\": \"llm_0\", \"targetNodeID\": \"end_0\"}
      ]
    },
    \"inputs\": {
      \"message\": \"请用一句话介绍 AI4J Flowgram。\" 
    }
  }"
```

返回值只会先给你：

```json
{
  "taskId": "..."
}
```

这说明 Flowgram 是任务式运行，不是同步一次性把最终结果直接塞回来。

## 6. 查询结果与报告

### 查最终结果

```bash
curl "http://127.0.0.1:8080/flowgram/tasks/{taskId}/result"
```

返回结构：

```json
{
  "taskId": "...",
  "status": "success",
  "terminated": false,
  "error": null,
  "result": {
    "result": "..."
  }
}
```

### 查完整执行报告

```bash
curl "http://127.0.0.1:8080/flowgram/tasks/{taskId}/report"
```

报告里能拿到：

- workflow 状态
- 每个节点的输入输出
- 起止时间
- 节点级错误信息

这对平台后端做“运行面板”和“调试面板”很重要。

## 7. 数据库里会存什么

`JdbcFlowGramTaskStore` 当前会把这些信息写进 `ai4j_flowgram_task`：

- `task_id`
- `creator_id`
- `tenant_id`
- `created_at`
- `expires_at`
- `status`
- `terminated`
- `error`
- `result_snapshot`

也就是说，Flowgram JDBC 版当前更像：

- 任务状态存储
- 结果快照存储

而不是完整的事件溯源系统。

## 8. 前端画布如何接它

如果你已经有 Flowgram 前端画布，后端仍然是这套接口：

- `POST /flowgram/tasks/run`
- `POST /flowgram/tasks/validate`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

前端通常的主链路是：

1. 本地表单校验
2. 调 `validate`
3. 调 `run`
4. 轮询 `report`
5. 拉取 `result`

完整联调说明继续看：

- [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)

## 9. 平台化接入时建议补的三层

只把 `TaskStore` 切成 JDBC 还不够，真实平台建议继续替换：

- `FlowGramCallerResolver`
- `FlowGramAccessChecker`
- `FlowGramTaskOwnershipStrategy`

原因很简单：

- 你需要知道谁创建了任务
- 你需要限制谁能看某个任务
- 你需要按租户或项目维度做任务隔离

## 10. 上线建议

- demo 阶段可以只开 `task-store.type=jdbc`
- 真正平台化时，补 caller、ownership、auth 三层
- `result_snapshot` 适合查最终结果，不适合替代完整审计日志
- 如果你要做海量任务平台，再考虑任务归档、过期清理和冷热分层

## 11. 继续阅读

1. [Flowgram API 与运行时](/docs/flowgram/api-and-runtime)
2. [前端画布与后端 Runtime 对接](/docs/flowgram/frontend-backend-integration)
3. [前端工作流如何在后端执行](/docs/flowgram/workflow-execution-pipeline)
4. [自定义节点扩展](/docs/flowgram/custom-node-extension)
