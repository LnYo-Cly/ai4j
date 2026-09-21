---
title: Structured Outputs
description: The StructuredOutputs facade turns POJO→JSON Schema→response_format→chat→deserialization into one call — typed results across providers, with fence stripping, refusal/truncation detection, and raw-text recovery.
tags: [how-to]
---

# Structured Outputs

`StructuredOutputs` collapses "ask the model for JSON then parse it by hand" into one call: it generates a JSON Schema from a POJO class (OpenAI Structured Outputs compatible, `strict: true`), attaches it as `response_format`, calls any `IChatService`, strips Markdown fences, and deserializes into the target type.

```java
public class CityWeather {
    private String city;
    private Integer temp;
    // getters/setters
}

CityWeather weather = StructuredOutputs.chat(chatService,
        ChatCompletion.builder()
                .model("gpt-4o")
                .message(ChatMessage.withUser("Weather in Shanghai?"))
                .build(),
        CityWeather.class);
```

Convenience overload (model + single user prompt):

```java
CityWeather w = StructuredOutputs.chat(chatService, "gpt-4o", "Weather in Shanghai?", CityWeather.class);
```

## Behavior details

- **`response_format` is only filled when absent** — a caller-provided format wins, so the schema can still be hand-tuned.
- **Fence stripping**: JSON wrapped in ```` ```json ```` blocks is unwrapped before parsing.
- **Refusal**: a non-empty `message.refusal` throws `StructuredOutputException` carrying the refusal text.
- **Truncation**: `finish_reason=length` throws an exception advising a larger token budget instead of returning half a JSON document.
- **Parse failure**: throws `StructuredOutputException`; `getRawContent()` returns the raw model text for logging or retry.
- **Usage recovery**: `chatDetailed(...)` returns `StructuredResult<T>` (value + rawText + usage).

## Schema generation coverage

`JsonSchemaGenerator` handles String/numeric/Boolean/enum (→string+enum values)/nested objects recursively/`List<T>`/`Map`/arrays; all fields are `required` and `additionalProperties: false` by default. Custom schema name:

```java
StructuredOutputs.chat(chatService, request, CityWeather.class, "city_weather");
```

> Sources: `ai4j/src/main/java/io/github/lnyocly/ai4j/structured/`; schema generator: `io.github.lnyocly.ai4j.convert.JsonSchemaGenerator`.
