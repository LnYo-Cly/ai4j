# Function Calling

`Function Calling` 是 AI4J 里最直接的本地工具接入方式。

## 1. 它解决什么

- 把本地 Java 能力暴露给模型
- 用统一 schema 描述函数输入
- 在 `Chat` 或上层 runtime 中完成调用链

## 2. 典型入口

```java
ChatCompletion request = ChatCompletion.builder()
        .model("gpt-4o-mini")
        .message(ChatMessage.withUser("查询北京天气"))
        .functions("queryWeather")
        .build();
```

## 3. 和上层的关系

- 基座层：负责函数声明、暴露与调用桥
- Agent 层：负责更复杂的 registry / executor / governance
- Coding Agent：再叠加 workspace 和审批语义
