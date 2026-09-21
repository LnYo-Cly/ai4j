---
sidebar_position: 7
title: Prompt 缓存与 KV Cache
description: 讲清 prompt caching / KV cache 的边界：缓存在模型厂商推理侧，SDK 不实现也无法实现 KV cache；ai4j 的职责是透传 cache_control、如实核算 cached_tokens，并通过追加式 memory 与稳定前缀设计最大化命中率。附压缩（compact）对缓存的影响分析与竞品对照。
tags: [concept]
---

# Prompt 缓存与 KV Cache

> **一句话结论**：KV cache 是模型厂商推理侧的能力，不在 SDK 职责范围内；ai4j 做的是三件事——透传缓存标记、如实核算命中量、用前缀稳定性设计为命中创造条件。

## 1. 边界先划清：缓存在哪一层

Transformer 的 attention 用 Q/K/V 三个投影。自回归生成时，每个新 token 都要 attend 之前所有 token 的 K、V——把历史 token 的 K/V 张量缓存起来避免重算，就是 **KV cache**。Prompt caching 把这个思路扩展到**跨请求**：相同 prompt 前缀的 KV 计算结果在服务端复用。

```text
┌─────────────────── provider 推理侧（SDK 够不着）───────────────────┐
│  请求前缀 → 匹配已缓存 KV → 命中部分按低价读、不重算                 │
│  缓存存多久、何时驱逐、最小前缀阈值 —— 全是 provider 策略            │
└──────────────────────────────────────────────────────────────────┘
┌─────────────────── SDK / 调用侧（我们能控制的）─────────────────────┐
│  ① 透传 cache_control 标记（Anthropic 显式断点）                    │
│  ② 捕获 cached_tokens / ephemeral_*_input_tokens（命中量观测）      │
│  ③ 前缀稳定性设计：让请求尽量命中（稳定内容前置、追加式历史）          │
└──────────────────────────────────────────────────────────────────┘
```

因此 KV cache 不属于 SDK 的职责范围——缓存的是模型内部计算状态，发生在服务端。SDK 层的合理职责是透传缓存标记、观测命中量、保持请求前缀稳定。

## 2. 各厂商的缓存形态

不同 provider 的缓存机制差异很大，不能假设一套行为通吃：

| Provider | 机制 | 开关 | 计费特征 |
|----------|------|------|----------|
| OpenAI 系 | 自动前缀缓存 | 无，全自动 | 前缀 ≥ ~1024 tokens 才开始缓存；命中部分按 `cached_tokens` 折价 |
| Anthropic | 显式 `cache_control` 断点 | **必须打标，不打不缓存**；最多 4 个断点 | 写溢价：5m TTL ≈ 1.25× 输入价，1h TTL ≈ 2×；命中读 ≈ 0.1× |
| Moonshot 等 | 自动前缀缓存 | 自动 | `cached_tokens` 位置非标准（在 usage 顶层） |

关键差异：**Anthropic 是 opt-in**——不打 `cache_control` 就没有缓存。所以 SDK 必须支持这个字段透传，否则等于帮用户关掉了缓存。

## 3. ai4j 做的三件事

### 3.1 透传缓存标记

`AnthropicContentBlock` / `AnthropicTool` 实体上有 `cacheControl` 字段，可打在 system 或消息 block 上做缓存断点（见[协议字段对照](/docs/capabilities/models/protocol-fields)）。OpenAI 系无需任何标记。

### 3.2 命中量观测与成本核算

usage 里如实捕获缓存命中量：

- OpenAI 标准位：`usage.prompt_tokens_details.cached_tokens`（`UsageDetails`）
- Responses：`usage.input_tokens_details.cached_tokens`（`ResponseUsageDetails`）
- Anthropic：`ephemeral_5m_input_tokens` / `ephemeral_1h_input_tokens` / `cache_read_input_tokens`
- Moonshot：非标的顶层 `cached_tokens` 也做了归一化

**这一步的意义是成本核算不失真**——命中部分按低价计，不捕获就没法算真实成本，也没法验证前缀稳定性策略是否生效。

### 3.3 前缀稳定性设计

命中率不是 SDK 能承诺的 SLA，但前缀稳不稳定是 SDK 的设计选择。ai4j 的结构恰好是缓存友好的：

| 设计 | 对缓存的作用 |
|------|------------|
| `AgentContext` 快照固定 system prompt + 工具面 | head 逐字节稳定、永远命中（system/tools 是请求顶层字段，不在消息流里，见 §7） |
| append-only memory（item 按时间序追加） | 历史前缀天然稳定，每轮只有尾部新增 |
| 不往前缀塞时间戳/随机 id | 避免人为打断前缀匹配 |
| 工具列表来自固定 registry | 顺序稳定（工具顺序变化同样会改变前缀） |

**Agent 循环的命中形态**：每轮 = `稳定前缀(system+tools+已有history)` + `新增尾部(新tool结果+新user)`。存量部分命中、只有增量计费——这是追加式设计的自然红利。

### 3.4 tools 字段会变吗：MCP 工具与动态可见性

`tools` 是请求级顶层字段、排在 messages 之前，属于前缀的一部分——**工具集或顺序一变，前缀从 tools 位置就断**。ai4j 每轮 `buildPrompt` → `visibleTools(context)` 从 registry 重建该字段，正常情况下逐字节稳定，但有三类动态源：

| 动态源 | 机制 | 对缓存的影响 |
|--------|------|------------|
| MCP `notifications/tools/list_changed` 或断线重连 | `McpClient.availableTools` 缓存失效，下次请求重拉 `tools/list` | 工具集变化 → tools 字段变 → 前缀断 |
| `AgentToolVisibility` 过滤器 | 每轮对注册表做 request 投影 | filter 中途改变可见集 → 前缀断（可作为"一次性前缀变化换每轮省 token"的主动权衡） |
| 运行中注册/注销工具（subagent、skill 等） | registry 内容变化 | 同上 |

关于 **MCP 工具的注入粒度**：`tools/list` 每个 client 连接只调用一次并缓存，不会每轮询问 MCP server；但缓存的列表会**每轮全量序列化进 tools 字段**——注册一个 50 工具的 server，每轮请求就多携带约 5–25K tokens 前缀。命中时按读折价计费，但仍占上下文窗口。ai4j 的 "pass what you use" 是**服务级粒度**：`toolRegistry(functions, mcpServices)` 选定服务即带上其全部工具，目前没有按工具粒度的延迟加载。

### 3.5 竞品的延迟加载路线：defer_loading + tool search

MCP 工具膨胀是真实痛点：5 个 server ≈ 58 个工具 ≈ **55K tokens** 定义在对话开始前就占着上下文（Anthropic 实测过单会话 134K tokens 的工具定义）；工具过多还会拉低选择准确率——50 个工具时约 84–95%，200 个时掉到 41–83%。Anthropic/Claude Code 的解法分两层：

**API 层**（`tool_search_tool`，2025 年末 beta）：

- 客户端每轮仍发送全部工具定义（服务端搜索需要），但给不常用的标 `defer_loading: true`
- deferred 工具的完整 schema **不进入模型可见上下文**——模型起初只看到 tool search tool 和少数非 deferred 工具（官方建议保留 3–5 个高频工具不 defer）
- 模型需要时用自然语言调 `tool_search_tool`（BM25 / regex 两种），服务端返回 ≤5 个 `tool_reference` 块，自动展开为完整定义并以消息形式进入历史，下一轮即可调用
- **缓存友好是设计出来的**：deferred 定义不进模型可见前缀（据公开分析，在 cache key 计算前剥离），工作集增长走消息尾部追加而不改前缀
- 官方数字：约 500 tokens upfront + ~3K discovered ≈ 8.7K tokens，省约 85% token、保留约 95% 上下文；Opus 4.5 的 MCP 评测从 79.5% → 88.1%

**Claude Code 层**（2.1.69 起）：

- 两层结构：prompt 中的 `<available-deferred-tools>` 候选名目录（可见不可调）+ `tools` 数组工作集（可调），`ToolSearch` 在两者间桥接
- 加载的 schema 在**下一轮**请求的 tools 数组生效，不做推理中途变更
- 压缩时用 `preCompactDiscoveredTools` 元数据保住已加载清单，compact 后继续携带
- 自动 fallback：不支持的模型、或不转发 `tool_reference` 的代理部署，退回全量前置加载

**ai4j 对照**：目前是服务级全量注入 + `AgentToolVisibility` 投影过滤。分两层看这个差距：

- **provider 侧**：`defer_loading` 是调用方在每轮请求的 `tools` 里自设的标记（全部定义照常发送，供服务端搜索），但"deferred schema 不进模型可见上下文、`tool_reference` 自动展开"由服务端实现——因此只对 Anthropic 生效。ai4j 原生路径已支持透传：`AnthropicTool.deferLoading`/`type`（可声明 `tool_search_tool_*`）、`AnthropicContentBlock.toolName`（`tool_reference` 历史往返保留）、`AnthropicConfig.betaFeatures`（`anthropic-beta` header，如 `advanced-tool-use-2025-11-20`）。
- **客户端侧**：存在一个不需要 provider 配合的等价思路——meta-tool / 工具搜索模式：tools 数组固定只暴露 `search_tools(query)` + `invoke_tool(name, args)` 两个 meta-tool，真实 schema 随搜索结果返回、调用由 gateway 路由。这样 tools 字段永不变化（前缀稳定）且上下文占用恒定小。`McpGateway` 已有 `getAvailableTools`/`callTool` 路由能力，但 ai4j 目前未提供该模式。
- **适用边界**：全量注入在中等工具规模（30 个以内）是正确且最简单的选择；缺口只在大工具池（多 MCP server、50+ 工具）场景显现。延迟加载本身也有代价——多一轮搜索往返、模型可能绕过搜索直接调未加载工具报错、小工具池下反而更慢。

另外注意 `AgentToolVisibility` 的安全用法：**会话开始时按任务类型过滤一次、会话内不再变更**，既省上下文又不打断前缀；把它当动态开关用则每变一次断一次前缀。

## 4. 命中率由什么决定

前缀稳定只是必要条件，命中还受这些 provider 侧因素约束：

1. **TTL**：Anthropic 5 分钟档，两轮间隔超过 TTL 就得重写（命中会刷新 TTL）；OpenAI 类似，通常 5–10 分钟级
2. **写溢价**：缓存写比未命中读更贵（1.25×~2×）——是"写溢价换读折扣"，前缀要复用多次才回本
3. **最小阈值**：短前缀压根不进入缓存
4. **尽力而为非 SLA**：容量驱逐、负载均衡换副本都可能 miss，provider 不保证命中
5. **断点数上限**：Anthropic ≤ 4 个 `cache_control` 断点

因此在 agent 循环中的典型表现是：**追加式历史让已有前缀部分的命中率接近上限，但每轮新增的 tool 结果照常计费；再叠加 TTL 到期重写和写溢价，实际节省幅度取决于会话密度和复用次数**。

## 5. 压缩（compact）与缓存：物理约束

这是最容易被误读的点：**压缩必然掉缓存，任何实现都一样**。

前缀缓存按**最长匹配前缀**命中——压缩把早期历史改写成 summary，分歧点之后必然全 miss。这不是缺陷，是前缀匹配机制的数学性质：改了内容就不可能命中旧缓存。

差别只在"**掉多少**"和"**多久回暖**"：

```text
压缩后轮次 1：head(system+tools) 照样命中 ✓ | summary+recent 全部重算并写缓存 ✗
压缩后轮次 2+：新前缀稳定 → 重新命中 ✓
```

ai4j 的几个细节决定掉得少、回暖快：

- **head 永远稳定**：system prompt 和工具定义不在 memory 里、不被压缩触碰——最大的静态块照常命中
- **增量式 checkpoint 合并**：`CodingSessionCompactor` 的 `UPDATE_SUMMARIZATION_PROMPT` 要求保留旧 checkpoint 全部信息，summary 演化而非全量重写 → 分歧区域尽量短
- **切点对齐消息边界**：只在完整 message 边界切（优先 user message），不撕裂 tool_use/tool_result 配对
- **压缩失败不中断**：压缩抛异常继续用原 memory 跑——缓存策略不变，只是没省到

:::note 投影层（Tier-1/2）也改前缀
`TypeAwareContextProjector` 把旧工具结果替换成占位符、清空旧 reasoning——**memory 本体不动，但发给模型的内容变了**，所以从被改的第一个 item 起前缀就断了。这是"一次性前缀变化换每轮省 token"的主动权衡：付一次重算，换后续每轮少几千 token。
:::

## 6. 竞品对照

### Claude Code：五级渐进管线 + 服务端协同

Claude Code 的压缩在 `src/services/compact/`（约 4000 行 TypeScript），是**渐进式**的——便宜的先跑，贵的兜底：

| 级 | 机制 | API 成本 | 破坏性 |
|----|------|---------|--------|
| L1 工具结果预算 | >50K 字符结果持久化到磁盘 + 留 2KB 预览（可用 Read 取回） | 0 | 可恢复 |
| L2 History Snip | GC 过期对话脚手架 | 0 | 低 |
| L3 Microcompact | 双路径：基于时间 / `cache_edits` 缓存协同 | 0 API 调用 | 中 |
| L4 Context Collapse | 投影式折叠 ~90% | 0 | 非破坏 |
| L5 Autocompact | fork 子 agent 做全量摘要 | 1 次调用 | 不可逆 |

两个最值得参考的设计：

- **`cache_edits`**：和服务端协同的"外科删除"——从缓存里精准抠掉指定块而不断前缀。这需要 provider 配合，**SDK 层做不到**（ai4j 无对应物，边界要说清）
- **约 50 种 system reminder 在消息尾部注入易变状态**：plan 模式、附件、环境信息等易变内容全部走尾部注入，不进入前缀——这是其维持缓存命中率的关键手段

压缩后处理：system prompt/output style 不动（不在消息历史里）、`CLAUDE.md` 和 auto memory 从磁盘重新注入、插入 compact boundary marker、保留压缩前已加载的 deferred tool schema 清单。

### Codex CLI：本地 + 远端双路径

开源 Rust 实现（`codex-rs/core/src/compact*.rs`）：

- **本地 compact**：专用 compaction task（刻意不走 `run_task`，防摘要过程污染会话状态），`SUMMARY.md` 提示词，`keep_last(n)` 保留尾部；重试复用同一 client session 保住粘性路由
- **Remote compact**：把压缩本身交给**服务端压缩端点**——发历史过去、收回压缩结果。压缩前先本地 `trim_function_call_history`，保留 `GhostSnapshot` 条目让 `/undo` 在压缩后仍可用，追加 `CompactionTrigger` 标记

Remote compaction 的启发：**压缩也可以是一种 provider 能力**——当厂商提供压缩端点时，客户端不用再自己写摘要 prompt。

### LangGraph / 通用框架

`SummarizationMiddleware` / `trim_messages` 一类：计数阈值触发 + 摘要节点写回 state，概念上等价于 ai4j 的 Tier-3。多数框架停在这一层，没有 Claude Code 那样的多级渐进。

### 对照表

| 能力 | Claude Code | Codex CLI | LangGraph | ai4j |
|------|------------|-----------|-----------|------|
| 工具结果就地裁剪 | L1（写磁盘+预览） | trim function_call history | — | Tier-1 投影（不改 memory 本体） |
| 非破坏折叠 | L4 context collapse | — | — | Tier-1/2 投影 |
| LLM 摘要压缩 | L5 autocompact | 本地 compact task | SummarizationMiddleware | `LlmCompactPolicy` / `CodingSessionCompactor` |
| 服务端压缩端点 | — | remote compact | — | — |
| 缓存外科删除 | `cache_edits` | — | — | —（需服务端协同） |
| 工具延迟加载 | `defer_loading` + `tool_search`（§3.5） | — | — | 服务级全量注入；Anthropic 原生路径已透传 `defer_loading` |
| 压缩后可恢复 | `/rewind`、磁盘持久化结果 | `GhostSnapshot` → `/undo` | checkpointer | session snapshot + `CompactResult` |
| 压缩失败兜底 | 多级渐进回退 | 重试 + abort | — | 异常不中断 run + fallback checkpoint |

## 7. 一个具体例子：同一会话的两种压缩路径

先看清楚一次请求的真实结构——**工具定义不在消息流里**。以 Anthropic / Responses 形态为例：

```jsonc
{
  "system": "你是 coding 助手。工作目录 G:\\proj。约束：不改 public API……",  // 顶层字段
  "tools": [{"name": "read_file", "input_schema": {…}},
            {"name": "edit_file", "input_schema": {…}}, /* …共 30 个… */],   // 顶层字段，每轮全量携带
  "messages": [ /* 或 Responses 的 input[] —— 历史在这里 */ ]
}
```

- `system` 和 `tools` 是**请求级顶层字段**：每轮原样重发，压缩永远不动它们——前文说的"静态 head"就是这两块。
- 穿插在 user/assistant 之间的是**工具调用与结果**（`tool_use` / `tool_result` / `function_call_output`），它们才是压缩要处理的对象。

假设一个 coding 会话：200K 窗口、40 轮、总计 ~173K tokens，压缩前的 `messages` 长这样（文本示意）：

```text
user:      "帮我修登录页的校验 bug，输入中文名会报错"
assistant: "我先看下校验逻辑" + tool_use read_file({path:"src/login/validate.ts"})
user:      tool_result "export function validateName(name:string){
            if(!/^[a-zA-Z]+$/.test(name)) …（原文 60K 字符 ≈ 12K tokens）"
assistant: "找到了：正则把中文全挡了" + tool_use edit_file({…})
user:      tool_result "ok, 3 lines changed"
…中间 30+ 轮：又读了 2 个大文件、跑了 5 次测试、改了几处调用点…
user:      "测试还是没过，你再看看"
```

### Claude Code：五级管线逐级兜底

压缩不是一次动作，而是每次请求前的固定流水线——便宜的层先跑，最贵的 LLM 摘要兜底：

| 级 | 这一步具体做什么 | 上下文变化 | 代价 / 可恢复性 |
|----|----------------|-----------|----------------|
| L1 工具结果预算 | 3 个超 50K 字符的 tool_result 全文写磁盘，原位留 2KB 预览 + 路径：`"[Persisted: .claude/tool-results/t1.txt] export function validateName…（截断）"` | 150K → ~114K | 0 成本；模型可用 Read 取回原文件，**可恢复** |
| L2 History Snip | 清掉过期对话脚手架（旧 plan 包装、失效 bookkeeping） | ~114K → ~110K | 0 成本 |
| L3 Microcompact | 旧 tool_use/tool_result 对清成占位符 `"[tool result cleared]"`；若走 cache-edit 路径则从服务端缓存外科删除 | ~110K → ~95K | 0 API 调用 |
| L4 Context Collapse | 投影式折叠约 90%（只改发送内容，不动 transcript） | 发送端 ~95K → ~60K | 0 成本，**非破坏** |
| L5 Autocompact | 仍超阈值 → fork 子 agent 读完整历史产结构化摘要 | ~60K → summary ~4K | **1 次 API 调用，不可逆** |

压缩后下一轮请求的 `messages`：

```text
user(boundary):  "=== compacted (auto) at turn 40, pre-compact ~173K tokens ==="
user(summary):   "本会话已压缩，完整 transcript 在 <transcript 路径>。
                  ## Goal        修复登录页中文名校验 bug
                  ## Progress    已定位 validateName 正则写反并修复；同步改了 3 处调用点
                  ## Pending     test/login.spec.ts 仍有两个用例失败
                  ## Decisions   改用 \u4e00-\u9fa5 范围而非 \w……"     ~4K
+ 重注入: CLAUDE.md "本项目用 pnpm，测试命令 pnpm test" / auto memory / MCP delta / 已加载的 deferred tool schemas
user:            "测试还是没过，你再看看"
```

- **缓存状态**：`system` + `tools` 两个顶层字段逐字节不变、照样命中；summary 起前缀分歧、本轮冷启动并写入缓存，第二轮起重新命中
- **还剩什么**：完整 transcript 仍在磁盘（`/rewind` 可回滚）；`CLAUDE.md`、auto memory 从磁盘重注入
- **丢了什么**：逐条消息历史被 summary 取代；带 `paths:` frontmatter 的规则在再次读到匹配文件前不再注入

### ai4j：投影分层 + Tier-3 结构化摘要

同一会话在 ai4j 里，压缩判定发生在每个 step 开头、模型调用之前：

| 步 | 具体做什么 | 结果 |
|----|-----------|------|
| 0 | `shouldCompact(snapshot)`：items 40 > `maxItems` 30 → 触发 | memory 40 items 待压 |
| 1 | 选切点：从目标位置**回退到最近一条 user 消息**，不切散 tool_use/tool_result 配对 | 前 ~30 items 进摘要，尾部 ~10 items 保留 |
| 2 | 切点前内容 + 上一轮 summary（增量合并）→ 发 LLM 结构化摘要请求 | 1 次模型调用 |
| 3 | 返回 JSON → 渲染成 `## Goal / ## Pending / ## Key Decisions` 段落式 summary | `CompactResult` 回填 decisions / failedCommands / testResults，机械扫描补 readFiles / modifiedFiles |
| 4 | `memory.restore()`：summary + 保留的 recent items 写回 | memory 40 → ~11 items |
| 5 | 发 `MEMORY_COMPRESS` 事件 + `ContextReport`（dropped 30） | 压缩过程可诊断、可审计 |

压缩后下一轮请求（Responses 形态下 summary 作为首条 item 注入；Chat 形态下作为 system 消息注入）：

```text
instructions:  "你是 coding 助手……"                        ← 不变 ✓命中
tools:         [read_file, edit_file, …]                   ← 不变 ✓命中
input[]:
  summary:     "## Goal        修复登录页中文名校验 bug
                ## Pending     test/login.spec.ts 两个用例失败
                ## Key decisions …"                        ~4K，本轮新写缓存
  …保留的最近 ~10 个 item（user/assistant/tool 对原样）…      ← 前缀分歧点之后，本轮重算
  user:        "测试还是没过，你再看看"
```

- **还剩什么**：`CompactResult` 结构化字段随 session snapshot 持久化；`SessionEventLog` 层仍保留完整事实——压缩只改 memory，不改事件日志
- **丢了什么**：被摘要 item 的原文不再进 prompt（event log 可查、可审计）
- **失败路径**：LLM 摘要失败 → `CodingSessionCompactor` 走 prompt-too-long 重试（最多 3 次、每次多丢 25%）→ 仍失败用本地 fallback checkpoint → 自动路径下压缩异常不中断 run，用原 memory 继续

### 从例子看两者差异

| | Claude Code | ai4j |
|---|---|---|
| 压缩策略 | 五级渐进，零成本层先跑、LLM 摘要兜底 | 投影分层（Tier-1/2 不动 memory）+ Tier-3 LLM 摘要 |
| 破坏性 | L1–L4 非破坏/可恢复，仅 L5 不可逆 | Tier-1/2 只改发送内容不改 memory 本体；Tier-3 改 memory 但 event log 留全量事实 |
| 摘要所在位置 | user 消息（`isCompactSummary`）+ boundary marker | summary item / system 消息 |
| 缓存协同 | `cache_edits` 服务端外科删除 | 无对应物（需 provider 协同，SDK 层做不到） |
| 压缩后缓存 | head 命中 + 重注入内容冷启动，第二轮回暖 | 相同：head 命中，第二轮回暖 |

## 8. 实操清单：怎么把命中率拉满

在 ai4j 上构建 agent 时：

1. **稳定内容前置**：system prompt、工具定义、长文档放最前
2. **前缀里不放易变字段**：时间戳、随机 id、动态计数会把缓存打断——要放就放尾部
3. **工具列表顺序保持稳定**：顺序变了前缀就变了
4. **追加式而非重排式历史**：ai4j memory 默认如此，别手动重排
5. **Anthropic 显式打 `cache_control`**：system 块和长前缀边界上打断点
6. **监控 `cached_tokens`**：命中率掉了先查前缀稳定性，不是查 provider
7. **对 compact 的预期**：压缩后首轮 miss 是必要代价；相比缓存命中，避免上下文溢出对任务正确性更关键

## 9. 边界与免责

- ai4j **没有也不会**实现 LLM KV cache——那是推理引擎的职责
- 不承诺具体命中率——由 provider 策略（TTL/驱逐/负载均衡）和调用形态共同决定
- 语义缓存（相同 query 直接返回历史结果）是另一个概念，复用的是**结果**而非**计算**——属于应用层/网关层，ai4j 刻意不内置，可用 interceptor 挂载自行实现

## 继续阅读

- [记忆压缩与上下文投影器](/docs/agent/memory/memory-compact-context)——三层分级压缩机制
- [上下文窗口管理](/docs/agent/memory/context-window-management)——投影预算配置
- [协议字段对照](/docs/capabilities/models/protocol-fields)——`cache_control` 与 usage 字段的实体映射
- [Trace 与可观测](/docs/agent/observability/trace-observability)——成本核算与 token 统计
