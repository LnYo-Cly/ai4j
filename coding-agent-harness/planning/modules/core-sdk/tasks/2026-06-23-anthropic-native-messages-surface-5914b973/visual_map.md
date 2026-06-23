# Visual Map

Visual Map Contract: v1.0

## 架构图

```mermaid
graph TD
  subgraph 统一层
    IChat[IChatService OpenAI格式]
  end
  subgraph 原生层(本任务)
    IMsg[IMessagesService Anthropic原生]
    AMsg[AnthropicMessagesService 实现]
    Handler[AnthropicStreamHandler 原生事件]
    AErr[AnthropicApiException 类型化]
  end
  Adapter[AnthropicChatService 统一适配器] -->|委托+翻译| AMsg
  AMsg -.implements.-> IMsg
  AMsg --> Handler
  AMsg --> AErr
  IMsg -->|api.anthropic.com / open.bigmodel.cn/api/anthropic| Endpoint[/v1/messages]
```

## 状态表

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务包就位 | task_plan 等 | coordinator | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | IMessagesService + AnthropicMessagesService + StreamHandler + 异常 | diff | coordinator | missing | 抽取回归 | coordinator |
| EXEC-02 | execution | EXEC-01 | done | 100 | thinking 映射 + AnthropicChatService 委托 + getMessagesService | 单测 | coordinator | missing | thinking 失真 | coordinator |
| GATE-01 | gate | EXEC-02 | done | 100 | 回归 + live 烟测 + harness check | progress | coordinator | missing | n/a | coordinator |
