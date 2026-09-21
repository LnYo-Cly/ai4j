---
sidebar_position: 7
title: "Prompt Caching and KV Cache"
description: "Clarifies the boundary of prompt caching / KV cache: caching lives on the model provider's inference side, and an SDK cannot and should not implement KV cache. ai4j's job is to pass through cache_control, account for cached_tokens honestly, and maximize hit rate through append-only memory and prefix-stability design. Includes an analysis of how compaction interacts with caching, plus a competitor comparison."
tags: [concept]
---

# Prompt Caching and KV Cache

> **One-line conclusion**: KV cache is a capability of the model provider's inference side and sits outside the SDK's scope. What ai4j does is three things: pass through cache markers, account for hit volume honestly, and create the conditions for hits through prefix-stability design.

## 1. Boundaries first: where the cache lives

Transformer attention uses three projections: Q/K/V. During autoregressive generation, every new token must attend to the K and V of all previous tokens — caching those historical K/V tensors to avoid recomputation is the **KV cache**. Prompt caching extends this idea **across requests**: the KV computation results for identical prompt prefixes are reused on the server.

```text
┌─────────────── provider inference side (SDK cannot reach) ────────────────┐
│  request prefix → match cached KV → hit portion billed at read discount    │
│  TTL, eviction, minimum prefix threshold — all provider policy             │
└───────────────────────────────────────────────────────────────────────────┘
┌─────────────── SDK / caller side (what we control) ───────────────────────┐
│  ① pass through cache_control markers (Anthropic explicit breakpoints)     │
│  ② capture cached_tokens / ephemeral_*_input_tokens (hit observability)    │
│  ③ prefix-stability design: maximize hits (stable head, append-only)       │
└───────────────────────────────────────────────────────────────────────────┘
```

KV cache therefore sits outside the SDK's scope — what is cached is the model's internal computation state on the server. The SDK's proper role is to pass through cache markers, observe hit volume, and keep request prefixes stable.

## 2. Provider cache shapes

Cache mechanisms differ significantly across providers — never assume one behavior fits all:

| Provider | Mechanism | Switch | Billing traits |
|----------|-----------|--------|----------------|
| OpenAI-family | Automatic prefix caching | None, fully automatic | Prefix ≥ ~1024 tokens before caching starts; hits billed at `cached_tokens` discount |
| Anthropic | Explicit `cache_control` breakpoints | **Must be marked — no marker, no caching**; up to 4 breakpoints | Write premium: 5m TTL ≈ 1.25× input price, 1h TTL ≈ 2×; cache reads ≈ 0.1× |
| Moonshot etc. | Automatic prefix caching | Automatic | `cached_tokens` sits at a non-standard location (top level of usage) |

The key difference: **Anthropic is opt-in** — without `cache_control` there is no caching at all. So the SDK must support passing this field through, or it effectively disables caching for the user.

## 3. The three things ai4j does

### 3.1 Pass through cache markers

`AnthropicContentBlock` / `AnthropicTool` entities carry the `cacheControl` field, which can be placed on the system block or message blocks as a caching breakpoint (see [protocol field mapping](/docs/capabilities/models/protocol-fields)). OpenAI-family providers need no marker.

### 3.2 Hit observability and cost accounting

Cache hit volume is captured faithfully in usage:

- OpenAI standard location: `usage.prompt_tokens_details.cached_tokens` (`UsageDetails`)
- Responses: `usage.input_tokens_details.cached_tokens` (`ResponseUsageDetails`)
- Anthropic: `ephemeral_5m_input_tokens` / `ephemeral_1h_input_tokens` / `cache_read_input_tokens`
- Moonshot: the non-standard top-level `cached_tokens` is also normalized

**This step keeps cost accounting honest** — hits are billed at the lower rate, so without capturing them you cannot compute real cost or verify whether your prefix-stability strategy is working.

### 3.3 Prefix-stability design

Hit rate is not an SLA an SDK can promise, but prefix stability is an SDK design choice. ai4j's structure happens to be cache-friendly:

| Design | Effect on caching |
|--------|-------------------|
| `AgentContext` snapshot pins system prompt + tool surface | The head stays byte-stable and always hits (system/tools are top-level request fields, outside the message stream — see §7) |
| append-only memory (items appended chronologically) | History prefix is naturally stable; only the tail grows each turn |
| No timestamps/random ids injected into the prefix | Avoids artificially breaking prefix matching |
| Tool list comes from a fixed registry | Order stays stable (tool order changes also change the prefix) |

**The hit shape of an agent loop**: each turn = `stable prefix (system + tools + existing history)` + `new tail (new tool results + new user)`. The retained portion hits; only the delta is billed — a natural dividend of append-only design.

### 3.4 Does the tools field change: MCP tools and dynamic visibility

`tools` is a request-level top-level field that precedes `messages` and is part of the prefix — **whenever the tool set or its order changes, the prefix breaks at the tools position**. ai4j rebuilds this field every turn via `buildPrompt` → `visibleTools(context)`, which is byte-stable under normal conditions, but three dynamic sources exist:

| Dynamic source | Mechanism | Cache impact |
|----------------|-----------|--------------|
| MCP `notifications/tools/list_changed` or reconnect | `McpClient.availableTools` cache is invalidated; the next request refetches `tools/list` | Tool set changes → tools field changes → prefix breaks |
| `AgentToolVisibility` filter | Per-request projection over the registry | A mid-session change to the visible set breaks the prefix (can be a deliberate trade: "one prefix change for tokens saved every turn") |
| Runtime tool registration/deregistration (subagents, skills, etc.) | Registry content changes | Same as above |

On **MCP tool injection granularity**: `tools/list` is called once per client connection and cached — the MCP server is not queried every turn; but the cached list is **serialized in full into every request's tools field** — registering a 50-tool server adds roughly 5–25K tokens of prefix to every request. That portion hits the cache at read-discount pricing, but still occupies the context window. ai4j's "pass what you use" granularity is **per-service**: `toolRegistry(functions, mcpServices)` exposes all tools of the selected services; there is currently no per-tool lazy loading.

### 3.5 The deferred-loading approach: defer_loading + tool search

MCP tool bloat is a real pain point: 5 servers ≈ 58 tools ≈ **55K tokens** of definitions occupying the context before the conversation even starts (Anthropic has measured 134K tokens of tool definitions in a single session); too many tools also degrade selection accuracy — roughly 84–95% at 50 tools, dropping to 41–83% at 200. Anthropic/Claude Code's solution has two layers:

**API layer** (`tool_search_tool`, beta since late 2025):

- The client still sends all tool definitions every turn (needed server-side for search), but marks infrequently used ones with `defer_loading: true`
- The full schemas of deferred tools **do not enter the model-visible context** — the model initially sees only the tool search tool plus a few non-deferred tools (official guidance: keep 3–5 high-frequency tools non-deferred)
- When needed, the model calls `tool_search_tool` (BM25 / regex variants) with a natural-language query; the server returns ≤5 `tool_reference` blocks, which are auto-expanded into full definitions and enter the message history — callable from the next turn
- **Cache-friendliness is by design**: deferred definitions do not enter the model-visible prefix (per public analysis, they are stripped before the cache key is computed); working-set growth rides on message-tail appends rather than prefix changes
- Official numbers: ~500 tokens upfront + ~3K discovered ≈ 8.7K tokens total, saving ~85% of tokens and preserving ~95% of the context window; Opus 4.5's MCP evaluation went from 79.5% → 88.1%

**Claude Code layer** (since 2.1.69):

- Two-layer structure: an `<available-deferred-tools>` name catalog in the prompt (visible but not callable) + the `tools` array working set (callable), bridged by `ToolSearch`
- Loaded schemas take effect in the **next** request's tools array — no mid-inference availability changes
- On compaction, `preCompactDiscoveredTools` metadata preserves the already-loaded set so post-compact requests keep sending it
- Automatic fallback: unsupported models or proxies that don't forward `tool_reference` blocks revert to upfront loading

**ai4j comparison**: currently service-level full injection + `AgentToolVisibility` projection filtering. The gap splits into two halves:

- **Provider side**: `defer_loading` is a marker the caller sets on each tool in the request (all definitions are still sent for server-side search), but "deferred schemas stay out of the model-visible context" and "`tool_reference` auto-expansion" are implemented server-side — so it only works on Anthropic. The ai4j native path already supports the passthrough: `AnthropicTool.deferLoading`/`type` (to declare `tool_search_tool_*`), `AnthropicContentBlock.toolName` (`tool_reference` preserved across history round-trips), and `AnthropicConfig.betaFeatures` (the `anthropic-beta` header, e.g. `advanced-tool-use-2025-11-20`).
- **Client side**: an equivalent approach exists that needs no provider cooperation — the meta-tool / tool-search pattern: the tools array permanently exposes only `search_tools(query)` + `invoke_tool(name, args)`, real schemas return with search results, and calls are routed by the gateway. The tools field then never changes (stable prefix) while context usage stays constant and small. `McpGateway` already has `getAvailableTools`/`callTool` routing, but ai4j does not currently ship this pattern.
- **Applicability boundary**: full injection is correct and simplest at moderate tool counts (under 30); the gap only shows in large-tool-pool scenarios (multiple MCP servers, 50+ tools). Deferred loading has its own costs — an extra search round trip, models may bypass search and call an unloaded tool (validation error), and it is slower and costlier for small tool pools.

Also note the safe usage of `AgentToolVisibility`: **filter once at session start by task type, then keep it unchanged within the session** — this saves context without breaking the prefix; using it as a dynamic switch breaks the prefix on every change.

## 4. What determines hit rate

Prefix stability is only a necessary condition. Hits are also constrained by provider-side factors:

1. **TTL**: Anthropic's 5-minute tier — if the gap between turns exceeds the TTL, the cache must be rewritten (hits refresh the TTL); OpenAI is similar, typically on the order of 5–10 minutes
2. **Write premium**: cache writes cost more than uncached reads (1.25×–2×) — it is "pay a write premium to earn a read discount"; the prefix must be reused multiple times to break even
3. **Minimum threshold**: short prefixes never enter the cache at all
4. **Best-effort, not SLA**: capacity eviction and load-balancer replica switches can all miss; providers do not guarantee hits
5. **Breakpoint cap**: Anthropic allows ≤ 4 `cache_control` breakpoints

In an agent loop, the typical behavior is therefore: **append-only history pushes the hit rate of the existing prefix portion close to its ceiling, while each turn's new tool results are billed normally; combined with TTL expiry rewrites and write premiums, actual savings depend on session density and reuse count**.

## 5. Compaction and caching: a physical constraint

This is the most commonly misread point: **compaction inevitably drops cache — true for every implementation**.

Prefix caching matches on the **longest matching prefix** — compaction rewrites early history into a summary, and everything after the divergence point necessarily misses. This is not a defect; it is a mathematical property of prefix matching: changed content cannot hit an old cache.

The only differences are "**how much is lost**" and "**how fast it re-warms**":

```text
Turn 1 after compaction: head (system+tools) still hits ✓ | summary+recent recompute and re-cache ✗
Turn 2+ after compaction: new prefix is stable → hits again ✓
```

Several ai4j details keep the loss small and re-warming fast:

- **The head is always stable**: system prompt and tool definitions live outside memory and are never touched by compaction — the largest static block keeps hitting
- **Incremental checkpoint merge**: `CodingSessionCompactor`'s `UPDATE_SUMMARIZATION_PROMPT` requires preserving all existing checkpoint information; the summary evolves rather than being fully rewritten → the diverged region stays as short as possible
- **Boundary-aligned cuts**: cuts happen only at complete message boundaries (preferring user messages), never tearing tool_use/tool_result pairs
- **Compaction failure is non-fatal**: if compaction throws, the run continues with the original memory — cache behavior is unchanged, you just didn't save tokens

:::note Projection tiers (Tier-1/2) also change the prefix
`TypeAwareContextProjector` replaces old tool results with placeholders and clears old reasoning — **the memory store is untouched, but what gets sent to the model changes**, so the prefix breaks at the first modified item. This is a deliberate trade: "pay one prefix change to save tokens every turn" — one recomputation in exchange for thousands fewer tokens on every subsequent turn.
:::

## 6. Competitor comparison

### Claude Code: five-level progressive pipeline + server cooperation

Claude Code's compaction lives in `src/services/compact/` (~4000 lines of TypeScript) and is **progressive** — cheapest first, most expensive last:

| Level | Mechanism | API cost | Destructiveness |
|-------|-----------|----------|-----------------|
| L1 Tool result budget | Results >50K chars persisted to disk + 2KB preview kept (recoverable via Read) | 0 | Recoverable |
| L2 History Snip | GCs stale conversation scaffolding | 0 | Low |
| L3 Microcompact | Dual path: time-based / `cache_edits` cache cooperation | 0 API calls | Medium |
| L4 Context Collapse | Projection-based folding ~90% | 0 | Non-destructive |
| L5 Autocompact | Forks a child agent for a full summary | 1 call | Irreversible |

Two designs most worth referencing:

- **`cache_edits`**: surgical removal coordinated with the server — precisely removes specified blocks from the cache without breaking the prefix. This requires provider cooperation and **cannot be done at the SDK layer** (ai4j has no equivalent; the boundary should be stated honestly)
- **~50 kinds of system reminders injected at the message tail** carrying volatile state: plan mode, attachments, and environment info all go through tail injection rather than entering the prefix — a key mechanism for sustaining cache hit rate

Post-compaction handling: system prompt/output style untouched (not part of message history), `CLAUDE.md` and auto memory re-injected from disk, a compact boundary marker inserted, and the pre-compaction list of loaded deferred tool schemas preserved.

### Codex CLI: local + remote dual path

Open-source Rust implementation (`codex-rs/core/src/compact*.rs`):

- **Local compact**: a dedicated compaction task (deliberately not `run_task`, to keep summarization from polluting session state), a `SUMMARY.md` prompt, `keep_last(n)` for the tail; retries reuse the same client session to preserve sticky routing
- **Remote compact**: hands compaction itself to a **server-side compaction endpoint** — ships the history over, receives the compacted result. Locally runs `trim_function_call_history` first, preserves `GhostSnapshot` items so `/undo` still works after compaction, and appends a `CompactionTrigger` marker

The takeaway from remote compaction: **compaction itself can be a provider capability** — when the vendor offers a compaction endpoint, the client no longer needs to write its own summarization prompt.

### LangGraph and general frameworks

`SummarizationMiddleware` / `trim_messages` and similar: count-threshold trigger + a summarization node writing back into state — conceptually equivalent to ai4j's Tier-3. Most frameworks stop at this level, without Claude Code-style multi-level progression.

### Comparison table

| Capability | Claude Code | Codex CLI | LangGraph | ai4j |
|------------|------------|-----------|-----------|------|
| In-place tool-result trimming | L1 (disk persist + preview) | trim function_call history | — | Tier-1 projection (memory store untouched) |
| Non-destructive folding | L4 context collapse | — | — | Tier-1/2 projection |
| LLM summarization compaction | L5 autocompact | local compact task | SummarizationMiddleware | `LlmCompactPolicy` / `CodingSessionCompactor` |
| Server-side compaction endpoint | — | remote compact | — | — |
| Surgical cache removal | `cache_edits` | — | — | — (requires server cooperation) |
| Deferred tool loading | `defer_loading` + `tool_search` (§3.5) | — | — | service-level full injection; `defer_loading` passthrough on the native Anthropic path |
| Post-compaction recovery | `/rewind`, disk-persisted results | `GhostSnapshot` → `/undo` | checkpointer | session snapshot + `CompactResult` |
| Compaction failure fallback | multi-level progressive fallback | retry + abort | — | non-fatal run + fallback checkpoint |

## 7. A concrete example: two compaction paths for the same session

First, the real shape of a request — **tool definitions do not live in the message stream**. Using the Anthropic / Responses shape:

```jsonc
{
  "system": "You are a coding assistant. Working dir G:\\proj. Constraint: do not change public APIs…",  // top-level field
  "tools": [{"name": "read_file", "input_schema": {…}},
            {"name": "edit_file", "input_schema": {…}}, /* …30 in total… */],   // top-level field, resent in full every turn
  "messages": [ /* or input[] for Responses — this is where history lives */ ]
}
```

- `system` and `tools` are **request-level top-level fields**: resent verbatim every turn, and compaction never touches them — they are the "static head" mentioned earlier.
- What interleaves between user/assistant messages are **tool calls and tool results** (`tool_use` / `tool_result` / `function_call_output`) — those are what compaction operates on.

Assume a coding session: a 200K window, 40 turns, ~173K tokens total. Before compaction, `messages` looks like this (text abbreviated):

```text
user:      "Fix the login-page validation bug — Chinese names throw an error"
assistant: "Let me look at the validation logic" + tool_use read_file({path:"src/login/validate.ts"})
user:      tool_result "export function validateName(name:string){
            if(!/^[a-zA-Z]+$/.test(name)) …(60K chars ≈ 12K tokens)"
assistant: "Found it: the regex blocks all Chinese characters" + tool_use edit_file({…})
user:      tool_result "ok, 3 lines changed"
…30+ more turns: 2 more large file reads, 5 test runs, several call-site edits…
user:      "Tests still failing — take another look"
```

### Claude Code: a five-level pipeline with progressive fallback

Compaction is not a single action but a fixed pipeline run before every request — the cheap levels run first, the expensive LLM summary is the last resort:

| Level | What this step concretely does | Context change | Cost / recoverability |
|-------|-------------------------------|----------------|-----------------------|
| L1 Tool result budget | 3 tool_results over 50K chars each → full content persisted to disk, a 2KB preview + path left in place: `"[Persisted: .claude/tool-results/t1.txt] export function validateName…(truncated)"` | 150K → ~114K | 0 cost; the model can Read the original back — **recoverable** |
| L2 History Snip | GCs stale conversation scaffolding (old plan wrappers, dead bookkeeping) | ~114K → ~110K | 0 cost |
| L3 Microcompact | Old tool_use/tool_result pairs cleared to `"[tool result cleared]"` placeholders; on the cache-edit path they are surgically removed from the server-side cache | ~110K → ~95K | 0 API calls |
| L4 Context Collapse | Projection-based folding of ~90% (changes only what is sent; the transcript is untouched) | sent side ~95K → ~60K | 0 cost, **non-destructive** |
| L5 Autocompact | Still over threshold → forks a child agent to read the full history and produce a structured summary | ~60K → summary ~4K | **1 API call, irreversible** |

The `messages` of the next request after compaction:

```text
user(boundary):  "=== compacted (auto) at turn 40, pre-compact ~173K tokens ==="
user(summary):   "This session was compacted; full transcript at <transcript path>.
                  ## Goal        Fix the login-page Chinese-name validation bug
                  ## Progress    Located the inverted validateName regex and fixed it; updated 3 call sites
                  ## Pending     test/login.spec.ts still has 2 failing cases
                  ## Decisions   Use \u4e00-\u9fa5 range instead of \w…"     ~4K
+ re-injected: CLAUDE.md "This project uses pnpm; test command: pnpm test" / auto memory / MCP deltas / already-loaded deferred tool schemas
user:            "Tests still failing — take another look"
```

- **Cache state**: the `system` + `tools` top-level fields are byte-identical and still hit; the prefix diverges at the summary — recomputed and re-cached this turn, hitting again from turn 2
- **What remains**: the full transcript stays on disk (`/rewind` can roll back); `CLAUDE.md` and auto memory are re-injected from disk
- **What is lost**: per-message history is replaced by the summary; rules with `paths:` frontmatter are not injected again until a matching file is read

### ai4j: projection tiers + Tier-3 structured summarization

For the same session on ai4j, the compaction check runs at the top of each step, before the model call:

| Step | What concretely happens | Result |
|------|------------------------|--------|
| 0 | `shouldCompact(snapshot)`: items 40 > `maxItems` 30 → trigger | memory holds 40 items pending compaction |
| 1 | Choose the cut point: **walk back to the nearest user message**, never tearing tool_use/tool_result pairs | ~30 items before the cut go to summarization; ~10 tail items are kept |
| 2 | Pre-cut content + previous summary (incremental merge) → send the LLM structured summarization request | 1 model call |
| 3 | JSON returned → rendered into a sectioned summary (`## Goal / ## Pending / ## Key Decisions`) | `CompactResult` backfills decisions / failedCommands / testResults; a mechanical scan adds readFiles / modifiedFiles |
| 4 | `memory.restore()`: summary + kept recent items written back | memory 40 → ~11 items |
| 5 | `MEMORY_COMPRESS` event + `ContextReport` emitted (dropped 30) | compaction stays diagnosable and auditable |

The next request after compaction (in the Responses shape the summary is injected as the first item; in the Chat shape as a system message):

```text
instructions:  "You are a coding assistant…"               ← unchanged ✓hit
tools:         [read_file, edit_file, …]                   ← unchanged ✓hit
input[]:
  summary:     "## Goal        Fix the login-page Chinese-name validation bug
                ## Pending     test/login.spec.ts has 2 failing cases
                ## Key decisions …"                        ~4K, cached this turn
  …the ~10 kept recent items (user/assistant/tool pairs verbatim)…   ← below the divergence point, recomputed this turn
  user:        "Tests still failing — take another look"
```

- **What remains**: `CompactResult` structured fields persist with the session snapshot; the `SessionEventLog` layer retains the complete facts — compaction rewrites memory, not the event log
- **What is lost**: the original items absorbed into the summary no longer enter the prompt (still queryable in the event log)
- **Failure path**: LLM summarization fails → `CodingSessionCompactor` retries prompt-too-long (up to 3 times, dropping 25% more each round) → still failing uses a local fallback checkpoint → on the automatic path, a compaction exception does not interrupt the run, which continues with the original memory

### Differences visible from the example

| | Claude Code | ai4j |
|---|---|---|
| Compaction strategy | Five-level progression; zero-cost levels first, LLM summary as fallback | Projection tiers (Tier-1/2 never touch memory) + Tier-3 LLM summary |
| Destructiveness | L1–L4 non-destructive/recoverable; only L5 is irreversible | Tier-1/2 change only what is sent, not the memory store; Tier-3 rewrites memory but the event log keeps the full facts |
| Where the summary lives | user message (`isCompactSummary`) + boundary marker | summary item / system message |
| Cache cooperation | `cache_edits` server-side surgical removal | No equivalent (requires provider cooperation, not possible at the SDK layer) |
| Post-compaction cache | Head hits + re-injected content starts cold, re-warms on turn 2 | Same: head hits, re-warms on turn 2 |

## 8. Practical checklist: how to maximize hit rate

When building agents on ai4j:

1. **Put stable content first**: system prompt, tool definitions, long documents go at the front
2. **No volatile fields in the prefix**: timestamps, random ids, and dynamic counters break the cache — put them in the tail if needed
3. **Keep tool list order stable**: reordering changes the prefix
4. **Append-only rather than reordered history**: ai4j memory does this by default; don't reorder manually
5. **Mark `cache_control` explicitly on Anthropic**: place breakpoints on the system block and long-prefix boundaries
6. **Monitor `cached_tokens`**: when hit rate drops, check prefix stability first — not the provider
7. **Set expectations for compact**: a first-turn miss after compaction is a necessary cost; compared with cache hits, avoiding context overflow matters more for task correctness

## 9. Boundaries and disclaimers

- ai4j **does not and will not** implement LLM KV cache — that is the inference engine's job
- No specific hit rate is promised — it is determined jointly by provider policy (TTL / eviction / load balancing) and call shape
- Semantic caching (returning historical results for identical/similar queries) is a different concept — it reuses **results**, not **computation**. It belongs at the application/gateway layer; ai4j deliberately does not build it in — implement it via interceptor hooks if needed

## Further reading

- [Memory Compact Context Projector](/docs/agent/memory/memory-compact-context) — the three-tier compaction mechanism
- [Context Window Management](/docs/agent/memory/context-window-management) — projection budget configuration
- [Protocol Field Mapping](/docs/capabilities/models/protocol-fields) — entity mapping for `cache_control` and usage fields
- [Trace and Observability](/docs/agent/observability/trace-observability) — cost accounting and token statistics
