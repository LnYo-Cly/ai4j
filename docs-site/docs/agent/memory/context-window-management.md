---
title: 上下文窗口管理
description: 讲清 ai4j 的上下文窗口管理机制：ContextBudget 设预算（maxItems/maxApproxChars/pinnedPrefixItems），DefaultContextProjector 按"保留头部 + 尾部"策略投影，ContextReport 报告丢弃诊断。这是 Compaction 之前的第一道闸，负责"选哪些进窗口"，而 Compaction 负责"怎么压缩"。
tags: [concept]
---

# 上下文窗口管理
> **上下文窗口管理 = 决定哪些历史条目进入模型的上下文窗口。** 它是 Compaction 之前的第一道闸：Projection 负责"选哪些进"，Compaction 负责"怎么压缩还太大的"。

## 为什么需要管

每个模型都有一个有限的上下文窗口（如 8K / 32K / 200K tokens）。但一个长跑 session 的完整历史（所有 user/assistant/tool-call/tool-output 消息）往往远超窗口。如果全塞进去，要么超限报错、要么被 provider 截断（丢掉最近的关键信息）。

ai4j 的做法是**两道闸门**：

```
完整 Memory（全部历史）
    ↓
① ContextProjector（投影：按预算选子集）     ← 这页讲的
    ↓
② Compaction（压缩：如果还太大，摘要/裁剪）   ← 下一道闸
    ↓
发送给模型的上下文
```

:::note Projection vs Compaction 的区别
- **Projection（投影）**：从完整历史里**选一个子集**，不改变条目内容。丢弃的是"不太重要的中间条目"。
- **Compaction（压缩）**：把条目**变换**成更短的形式（摘要、microcompact），改变内容。
- 先投影再压缩：先用预算裁掉明显多余的，如果还太大再压缩。两者可以只用一个，也可以叠加。
:::

## ContextBudget：设预算

`ContextBudget` 是一个简单的值对象，定义"上下文窗口最多保留多少"：

| 字段 | 类型 | 含义 |
|---|---|---|
| `maxItems` | Integer | 最多保留多少条消息（null = 不限） |
| `maxApproxChars` | Integer | 最多保留多少近似字符（null = 不限） |
| `pinnedPrefixItems` | Integer | 头部固定保留多少条不被裁（默认 0） |

```java
// 最多保留最近 20 条消息
ContextBudget budget = ContextBudget.maxItems(20);

// 最多保留约 10000 字符
ContextBudget budget = ContextBudget.maxApproxChars(10000);

// 最多 50 条，且前 3 条（system + 前 2 条 user/assistant）永远保留
ContextBudget budget = ContextBudget.builder()
        .maxItems(50)
        .pinnedPrefixItems(3)
        .build();
```

`pinnedPrefixItems` 的作用：session 开头的 system prompt 和最初几轮对话通常最重要（定义了任务和角色），不应该被裁掉。设 `pinnedPrefixItems(3)` 保证头 3 条始终保留，裁剪只作用于中间。

## DefaultContextProjector：投影策略

`ContextProjector` 是接口，只有一个方法：

```java
public interface ContextProjector {
    ContextProjection project(List<Object> items, ContextBudget budget);
}
```

`DefaultContextProjector` 是默认实现，采用**保留头部 + 尾部**（pin head + keep tail）策略：

1. **先按 maxItems 裁**：如果条目数超限，保留 `pinnedPrefixItems` 条头部 + 最近 `maxItems - pinnedPrefixItems` 条尾部，**丢弃中间**。
2. **再按 maxApproxChars 裁**：如果字符数还超限，对剩余条目同样保留头部 + 尾部、丢弃中间，直到字符数达标。

:::tip 为什么保留头部 + 尾部
- **头部**（pinned prefix）：system prompt + 早期指令——定义任务和角色，丢了 agent 就"忘了自己在做什么"。
- **尾部**（recent）：最近几轮对话——包含当前任务进展和最新工具结果，丢了 agent 就"忘了刚做了什么"。
- **中间**（middle drop）：远期中间步骤——通常是已完成的中间结果，丢弃影响最小。
:::

## ContextReport：丢弃诊断

每次投影返回一个 `ContextReport`，告诉你**裁了多少、为什么裁**：

| 字段 | 含义 |
|---|---|
| `sourceItemCount` | 原始条目数 |
| `projectedItemCount` | 投影后保留的条目数 |
| `droppedItemCount` | 丢弃的条目数 |
| `sourceApproxChars` | 原始近似字符数 |
| `projectedApproxChars` | 投影后近似字符数 |
| `itemLimitApplied` | 是否触发了条目数限制 |
| `characterLimitApplied` | 是否触发了字符数限制 |
| `notes` | 人读说明（如 `"maxItems applied: 20"`） |

```java
ContextProjection projection = projector.project(items, budget);
ContextReport report = projection.getReport();

if (report.getDroppedItemCount() > 0) {
    System.out.println("投影丢弃了 " + report.getDroppedItemCount() + " 条"
            + "，原始 " + report.getSourceItemCount() + " → 保留 " + report.getProjectedItemCount());
    // 输出示例：投影丢弃了 35 条，原始 55 → 保留 20
}
```

## 在 AgentBuilder 里配置

```java
Agent agent = Agent.builder()
        .modelClient(modelClient)
        .contextBudget(ContextBudget.builder()
                .maxItems(30)
                .maxApproxChars(20000)
                .pinnedPrefixItems(2)
                .build())
        .build();
```

不设 `contextBudget` 时，默认不投影（所有历史直接进窗口）——长 session 下需要手动配置，或依赖 Compaction 兜底。

## RAG 侧的 Token 预算

RAG 注入的检索结果也占上下文窗口。`TokenAwareRagContextAssembler` 在 RAG 侧做类似的预算管理：根据剩余 token 空间，决定注入多少检索片段。它与 ContextProjector **正交**——一个管历史消息，一个管 RAG 结果——但目标相同：不超出窗口。

→ 详见 [Search and RAG 总览](/docs/capabilities/rag/overview)

## 继续阅读

- [Memory 与 Compact Context](/docs/agent/memory/memory-compact-context)——Compaction（投影之后的下一道闸）
- [Memory 总览](/docs/capabilities/chat-memory/overview)——ChatMemory 是 Projection 的输入
- [Compact & Checkpoint](/docs/products/coding-agent/compact-and-checkpoint)——Coding Agent 层的压缩管线
- [Agent 概念地图](/docs/agent/agent-concepts)——Memory → Context Window → Compaction → Checkpoint 完整链路
