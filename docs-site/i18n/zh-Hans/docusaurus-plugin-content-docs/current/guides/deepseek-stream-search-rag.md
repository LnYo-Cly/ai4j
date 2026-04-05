---
sidebar_position: 1
---

# DeepSeek：流式 + 联网搜索 + RAG + 多轮会话

历史主题来源：一个完整 DeepSeek 应用的工程化落地。

## 1. 目标能力

- 流式输出（低等待感）
- 联网增强（解决时效信息）
- RAG 检索（解决私域知识）
- 多轮会话（上下文连续）

## 2. 推荐架构

```text
前端聊天页
  |- /chat/stream   # 流式
  |- /chat/search   # 联网增强
  `- /chat/rag      # 知识库检索

Spring Boot + AI4J
  |- Prompt 组装
  |- Tool/MCP 调用
  `- Agent/Workflow

模型 + 搜索 + 向量库
```

## 3. 关键工程拆分

### 流式层

- SSE 推送增量
- 前端拼接消息
- 支持中断与重连

### 搜索层

- 多源检索聚合
- 结果清洗与摘要
- 注入 prompt 上下文

### RAG 层

- 文档解析、分块、向量化、存储
- 召回 + 重排 + 证据拼接

### 会话层

- 滑动窗口记忆
- 压缩旧轮对话
- 关键事实持久化

## 4. 控制器骨架

```java
@RestController
@RequestMapping("/chat")
public class ChatController {
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String question) { return null; }

    @GetMapping("/search")
    public String search(@RequestParam String question) { return null; }

    @GetMapping("/rag")
    public String rag(@RequestParam String question) { return null; }
}
```

## 5. 上线顺序（很关键）

1. 先把流式链路稳定（可用性优先）
2. 加联网增强（时效性优先）
3. 加 RAG（准确性优先）
4. 加 Trace 和评估指标（可运营优先）

## 6. 评估指标建议

- 流式首包时间（TTFT）
- 回答完成耗时
- 检索命中率
- 引用覆盖率
- 人工纠错率

## 7. 常见失败点

- 联网文本过长导致 prompt 污染
- 检索片段质量差导致“看似有证据但不可用”
- 多轮上下文无限增长导致成本上升

建议每一步都加上“长度上限 + 质量阈值 + fallback”。
