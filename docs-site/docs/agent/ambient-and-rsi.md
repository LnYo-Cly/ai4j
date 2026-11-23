---
title: Ambient 常驻、心跳与门控自演化 RSI
description: 说明 ai4j-ambient 模块的身份前缀缓存、记忆溯源门控、做梦整合、两段式 AgenticHeartbeat、多端 ChannelGateway 与 L1-L3 门控自演化 RSI。
tags: [concept, agent, ambient, rsi]
---

# Ambient 常驻、心跳与门控自演化 RSI

在工程实践里，很多 Agent 不再是“一问一答后立即销毁”，而是长期运行在多渠道（IM、Webhook、IDE、CLI）与定时心跳上的**常驻智能体（Ambient Agent）**。`ai4j-ambient` 模块围绕 `ai4j`、`ai4j-extension-api`、`ai4j-agent`、`ai4j-harness` 与 `ai4j-testing`，补全了长期身份记忆、低成本睡眠哨兵、多端交互路由与受控自演化（Recursive Self-Improvement, RSI）四大能力。

---

## 1. 长期身份前缀与记忆治理（`ambient.memory`）

### 1.1 `IdentityCorePromptBuilder`：前缀字节稳定的身份加载

常驻 Agent 通常维护 `SOUL.md`、`USER.md` 与 `BOUNDARIES.md` 三个核心档案。每次把时间、未读消息或动态任务拼在最前面，都会直接击穿大模型的 **Prompt Caching**：

- `IdentityCorePromptBuilder` 按固定顺序装入 `SOUL.md`、`USER.md` 与 `BOUNDARIES.md`，并计算静态前缀的 SHA-256 `prefixDigest`；
- 通过 `toCacheableSections()` 与 `withDynamicSection()` / `withDynamicTail()` 将动态上下文严格追加在静态前缀之后，确保跨轮次、跨唤醒时的前缀逐字节完全一致。

> 更多关于提示词前缀缓存机制的说明见 [Prompt Caching 与上下文缓存](/docs/agent/memory/prompt-caching)。

### 1.2 `MemoryProvenanceGate`：不可信外部输入的隔离检疫

常驻 Agent 会读取网页、邮件、群聊与第三方工具返回，绝不能让外部 Prompt Injection 直接改写自我定位或长期规则：

- 依据 `TrustLevel` 划分三档可信度：`OWNER_DIRECT` > `VERIFIED_TOOL` > `EXTERNAL_UNTRUSTED`；
- 任何来自 `EXTERNAL_UNTRUSTED` 的记忆写入提案都会被自动检疫隔离（Quarantine），**直接拦截对 `IDENTITY_CORE` 与 `HARNESS_RULE` 的自动修改**；
- 被隔离的候选项仅在 Owner 调用 `promoteQuarantined(...)` 后才可晋升入长期记忆。

### 1.3 `DreamingConsolidator`：离线“做梦”记忆整合

随着会话增多，流水账（Episodes）会持续膨胀并产生事实冲突。`DreamingConsolidator` 负责周期性后台整理：

1. 修剪高噪声、低信息熵的临时情景片段；
2. 提炼结构化 `FactCandidate` 事实候选；
3. 检测语义冲突：当新事实推翻旧事实时，自动将 `ai4j-harness` 中的过时 Fact 置为失效，并写入 `RelationType.DERIVED_FROM` 血缘追溯关系，保持账本简洁且有据可查。

---

## 2. 两段式心跳与低成本唤醒（`ambient.heartbeat`）

让高价推理大模型每分钟空转轮询既浪费 Token 又容易打扰人类。`AgenticHeartbeat` 采用“系统一哨兵 + 主 Agent 唤醒”两段式架构：

```text
定时/事件触发
   │
   ▼
Tier 1: SystemOneSentinel（ISystemOneService Choice/Noul + 零成本空转过滤）
   ├─ 无值得唤醒的变化 -> SILENT_NOOP（零主模型消耗）
   ├─ 处于 QuietHoursPolicy 静默窗口 -> QUEUED_DIGEST / DEFERRED_QUIET_HOURS
   └─ 需要介入或裁决 -> Tier 2: MainAgentWakeupHandler (fromAgent / fromHarness)
                           ├─ 自主执行完成 -> MAIN_AGENT_EXECUTED
                           └─ 需人拍板     -> ESCALATED_IMMEDIATE
```

`AgenticHeartbeat` 每次心跳返回明确 `OutcomeType`（`SILENT_NOOP`、`QUEUED_DIGEST`、`MAIN_AGENT_EXECUTED`、`ESCALATED_IMMEDIATE`、`DEFERRED_QUIET_HOURS`），并可联动 `ambient.heartbeat.SupervisoryAgenda` 汇总待办与静默窗口摘要。

> 关于 Tier 1 使用的轻量快判器，可参阅 [SystemOne 轻量分级决策](/docs/capabilities/system-one)。

---

## 3. 多端通道网关与插件集成（`ambient.channel`）

- **`ChannelGateway`**：统一的多端收发件箱路由器，支持将 Harness 的升级决策以结构化交互卡片 `EscalationOptionCard` 推送到外部通道；当外部回调经 `EscalationCallback` 返回时，严格校验响应方必须为人类 Owner，拒绝 Agent Actor 伪造审批。
- **`AmbientExtension`**：将 Ambient 运行时接入 [Cordis 微内核](/docs/extending/plugins/cordis-microkernel)：
  - 暴露服务键：`ChannelGateway`、`SupervisoryAgenda`、`MemoryProvenanceGate` 与 `HarnessCommandGateway`；
  - 注册工具：`ambient_channel_send` 与 `ambient_escalate`；
  - 注册命令与 Skill：`ambient-status` 命令与 `ambient-runtime` Skill。

---

## 4. L1-L3 门控自演化 RSI（`ambient.evolve`）

AI4J 对 Agent 自演化（RSI）采取“**提议自由、合入严格门控、随时可原子回滚**”的工程原则：

### 4.1 对比轨迹挖掘与三层产物

`TrajectoryContrastiveMiner` 从成功与失败轨迹的对比中挖掘改进线索，并配合 `MetaRsiEvaluator` 评估演化收益；改进提案分为三级（L1–L3）：

| 级别 | 产物类 | 说明 |
| --- | --- | --- |
| **L1** | `HarnessRuleProposal` | 生成 Harness 治理准则提案，命中场景后自动注入行为约束 |
| **L2** | `SkillPatchCandidate` | 对既有 Skill Markdown 指令或参数模板生成增量补丁 |
| **L3** | `SkillCompiler` | 编译生成或升级可复用 Skill 产物，并通过 `verifyInIsolatedSandbox` 基于父 `ExtensionRegistry.fork()` 在隔离沙箱中试跑验证，不污染宿主运行态 |

### 4.2 `PreCommitGoldenGate` 五重发布门控与一键回滚

任何 L1–L3 自演化改动都必须依次通过 `PreCommitGoldenGate` 的 **5 道门禁**，最终产出 `GateOutcome` 才允许正式合入主配置：

1. **隔离沙箱验证**：在 `ExtensionRegistry.fork()` 子上下文中完成加载与静态/运行检查；
2. **黄金样本回放测试**：使用 `ai4j-testing` 的 `ReplayModelClient` 在固定 Golden Cases 上离线重放，确保既往核心场景零回归；
3. **成本与步数预算护栏**：校验步数（Steps）与 Token 消耗相比基线不得出现显著退化；
4. **独立 Witness 验收**：由非提案作者的独立 `Witness`（非 Agent 角色）完成验收；
5. **人类钉扎同意**：由 Owner 基于改动摘要（Digest）显式签发同意，摘要不匹配直接拒绝。

若上线后发现退化，直接调用 `PreCommitGoldenGate.rollback(...)`，系统会**同步撤销对应的 L1 Harness 规则并把 Skill 恢复到先前的 `v1` 基线版本**。

## 下一步建议

- 查看门控所依赖的 Harness 账本、Consent 摘要钉扎与 Witness 复跑原语：阅读 [Harness 2.0 治理与多节点 Fleet](/docs/agent/harness-governance-and-fleet)
- 查看隔离试跑所使用的微内核分叉机制：阅读 [Cordis 微内核与模块化生态插件](/docs/extending/plugins/cordis-microkernel)
- 了解离线确定性回放工具 `ReplayModelClient`：阅读 [Agent 测试与回放](/docs/agent/testing)
