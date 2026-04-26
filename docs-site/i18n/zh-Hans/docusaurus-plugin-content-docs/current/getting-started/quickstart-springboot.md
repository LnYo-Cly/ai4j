---
sidebar_position: 3
---

# Spring Boot 快速接入模式

这页关注“怎么把 SDK 接进现有业务系统”，而不是单点 demo。

## 1. 推荐分层结构

```text
src/main/java
  |- controller      # 只做协议转换与鉴权入口
  |- service         # 业务编排、异常语义
  |- ai
  |  |- prompts      # system/instruction 模板
  |  |- tools        # Function/MCP 工具调用封装
  |  `- workflow     # Agent/StateGraph 编排
  `- config          # AI4J 与 HTTP 客户端配置
```

## 2. SSE Controller 模板

```java
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestParam String q) {
    SseEmitter emitter = new SseEmitter(300000L);
    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);

    ChatCompletion req = ChatCompletion.builder()
            .model("gpt-4o-mini")
            .message(ChatMessage.withUser(q))
            .build();

    chatService.chatCompletionStream(req, new SseListener() {
        @Override
        protected void send() {
            try {
                emitter.send(getCurrStr());
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }
    });
    return emitter;
}
```

## 3. 生产化建议

### 3.1 API 层

- 统一鉴权（用户、租户、调用来源）
- 入参校验与长度限制
- 限流与熔断

### 3.2 Service 层

- 将 prompt 组装与业务逻辑解耦
- 为模型错误建立统一错误码映射
- 对长耗时请求增加超时与降级模型

### 3.3 观测层

- 每次请求生成 `requestId` 并贯穿日志
- 记录模型耗时、token、tool 次数
- 保留失败样本（脱敏后）用于回归测试

## 4. 什么时候升级到 Agent

当出现以下任一情况，建议使用 Agent：

- 单轮 prompt 无法稳定完成任务
- 需要“思考 -> 调工具 -> 再思考”的多步流程
- 需要子代理分工或状态图编排

升级路径：

1. 先把现有调用封装为 `AgentNode`
2. 用 `SequentialWorkflow` 替代手写 if/else 链
3. 再迁移到 `StateGraphWorkflow` 支持路由与循环

## 5. 最低测试覆盖建议

- 控制器：协议与状态码测试
- 服务层：prompt 构建与结果解析测试
- Agent/Workflow：至少 1 个真实场景集成测试

可参考：`ai4j/src/test/java/io/github/lnyocly/agent/WeatherAgentWorkflowTest.java`。
