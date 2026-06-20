# 外部资料摄取标准

## 职责

本标准定义 Agent 如何接收、过滤、整理外部项目或微服务团队提供的大量文档。目标是把外部资料变成可执行上下文，而不是把资料堆进 `context/{architecture,development,integrations}`。

## 核心模型

```text
外部原始资料 -> source pack 索引 -> digest 摘要 -> context/{architecture,development,integrations} 执行投影
```

`context/architecture`、`context/development`、`context/integrations` 只保存已经提炼并可用于执行的事实。外部团队丢来的原文、长文档、截图、聊天记录、导出包和历史资料先进入 `coding-agent-harness/context/development/external-source-packs/`，不能直接污染执行文档。

## 何时询问用户

在 Diagnose / Decide 阶段，只要发现以下任一信号，Agent 必须询问用户是否有外部资料：

- 当前仓库属于多仓系统、微服务系统、前后端分仓或平台子系统。
- 代码中出现外部服务、SDK、API gateway、message queue、webhook、contract、schema 或 mock。
- 用户提到“其他仓库”“外部服务”“上下游”“接口文档”“业务知识”“系统整体设计”。
- Agent 无法只靠当前仓库判断服务职责、接口契约或联调方式。

推荐问题：

1. 这个项目是否依赖外部服务或其他仓库？
2. 你是否有外部团队提供的架构文档、接口文档、流程图、会议纪要、代码路径、链接或导出包？
3. 这些资料能否复制进本仓？如果不能，是否只能保存本地路径或 URL？
4. 哪些资料是可信来源，哪些只是历史参考？

## 存储规则

| 场景 | 存储方式 |
| --- | --- |
| 只有 1-4 个稳定外部文档 | 不必建独立 source pack；在对应 `context/{architecture,development,integrations}` 的 `Source Evidence` 中链接即可 |
| 外部资料超过 5 份、跨多个主题、或会持续增长 | 创建 `coding-agent-harness/context/development/external-source-packs/<source-key>/` |
| 资料含敏感信息、密钥、客户数据或不能进仓 | 不复制原文；在 source pack README 记录外部路径、owner、访问条件和摘要 |
| 资料是可公开或可入仓的原始文档 | 可放 `raw/`，但仍要经过 digest 后才能投影到执行文档 |

建议结构：

```text
coding-agent-harness/context/development/external-source-packs/<source-key>/
├── README.md              # 资料索引和投影状态
├── digests/               # 每份或每组资料的摘要
├── raw/                   # 可入仓原文；禁止放密钥、隐私或客户数据
└── raw-index.md           # 原文不能入仓时，用路径/URL/owner 索引替代 raw/
```

不要为每个微服务复制一套完整 `context/{architecture,development,integrations}` 目录。source pack 是资料入口；稳定执行入口仍然是：

- `coding-agent-harness/context/architecture/service-catalog.md`
- `coding-agent-harness/context/architecture/services/<service-key>.md`
- `coding-agent-harness/context/development/external-context/<service-key>.md`
- `coding-agent-harness/context/integrations/<contract>.md`

## 摄取流程

1. **Inventory**：列出所有外部资料，记录来源、owner、时间、可信度、是否可入仓。
2. **Classify**：按 architecture、development、integration、security、operations、product、unknown 分类。
3. **Sanitize**：检查密钥、token、客户数据、隐私、内部账号、不可公开链接；不能入仓的只存引用。
4. **Digest**：用 digest 模板提炼事实、疑问、不安全假设和证据。
5. **Project**：把稳定事实投影到 `context/{architecture,development,integrations}`，并在 source pack README 标记 projected。
6. **Verify**：能用代码、接口测试、owner 确认或运行证据验证的，更新 `Last Verified` 和 `Confidence`。
7. **Residual**：不能确认的内容留在 source pack，不进入执行文档，或进入 `Do Not Assume`。

## 投影规则

| 资料内容 | 投影位置 |
| --- | --- |
| 服务职责、上下游、owner、数据归属、系统拓扑 | `coding-agent-harness/context/architecture/service-catalog.md` 或 `services/<service-key>.md` |
| 本仓开发时如何 mock、stub、启动、联调、排查 | `coding-agent-harness/context/development/external-context/<service-key>.md` |
| endpoint、payload、auth、error、event、webhook、SDK、contract test | `coding-agent-harness/context/integrations/<contract>.md` |
| 仍未确认、来源冲突、过期或只作为背景参考 | 留在 source pack README / digest，不进入执行文档 |

## 禁止事项

- 不要把几十份外部文档直接复制到 `context/architecture`、`context/development` 或 `context/integrations` 根目录。
- 不要把外部资料摘要当成已验证事实；必须记录 `Source Evidence`、`Last Verified` 和 `Confidence`。
- 不要在执行文档里保留大段原文、聊天流水或历史会议记录。
- 不要把密钥、真实 token、客户数据、个人隐私或不可公开资料提交进仓。
- 不要为了“完整”而给每个微服务复制一套目录树；只给资料量大的外部来源建 source pack。
