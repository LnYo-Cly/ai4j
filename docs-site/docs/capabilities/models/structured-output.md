---
title: 结构化输出（Structured Outputs）
description: StructuredOutputs 门面一行完成 POJO→JSON Schema→response_format→chat→反序列化，跨 provider 拿到类型化结果；带围栏剥离、refusal/截断识别和原始文本回溯。
tags: [how-to]
---

# 结构化输出

`StructuredOutputs` 把"让模型返回 JSON 再手动解析"收成一个调用：从 POJO 类生成 JSON Schema（OpenAI Structured Outputs 兼容，`strict: true`），填进 `response_format`，调任意 `IChatService`，剥离 Markdown 围栏后反序列化为目标类型。

```java
public class CityWeather {
    private String city;
    private Integer temp;
    // getters/setters
}

CityWeather weather = StructuredOutputs.chat(chatService,
        ChatCompletion.builder()
                .model("gpt-4o")
                .message(ChatMessage.withUser("上海天气？"))
                .build(),
        CityWeather.class);
```

便捷重载（模型 + 单条用户消息）：

```java
CityWeather w = StructuredOutputs.chat(chatService, "gpt-4o", "上海天气？", CityWeather.class);
```

## 行为细节

- **response_format 只在未设置时填充**——请求里自带的 `response_format` 优先，可以手工调 schema。
- **围栏剥离**：模型把 JSON 包进 ```` ```json ```` 代码块时自动剥离再解析。
- **refusal**：`message.refusal` 非空时抛 `StructuredOutputException`（带 refusal 原文）。
- **截断**：`finish_reason=length` 时抛异常提示放大 max tokens，而不是返回半个 JSON。
- **解析失败**：抛 `StructuredOutputException`，`getRawContent()` 拿回原始文本便于记录/重试。
- **usage 回溯**：`chatDetailed(...)` 返回 `StructuredResult<T>`（value + rawText + usage）。

## Schema 生成能力

`JsonSchemaGenerator` 支持 String/数值/布尔/枚举（→string+enum 值）/嵌套对象递归/`List<T>`/`Map`/数组；所有字段默认 `required`，`additionalProperties: false`。需要自定义名称：

```java
StructuredOutputs.chat(chatService, request, CityWeather.class, "city_weather");
```

> 源码：`ai4j/src/main/java/io/github/lnyocly/ai4j/structured/`；schema 生成器：`io.github.lnyocly.ai4j.convert.JsonSchemaGenerator`。
