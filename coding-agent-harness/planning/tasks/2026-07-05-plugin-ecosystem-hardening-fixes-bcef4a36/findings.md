# plugin ecosystem hardening fixes - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 插件资源读取不应隐式兜底宿主 classloader

- 背景：Skill/Prompt 资源由插件 manifest 声明，validator 和 runtime 消费侧如果从线程上下文或 API classloader 兜底，可能掩盖插件包漏资源问题。
- 发现：`ExtensionResourceResolver.readText/exists` 原来保留 fallback 行为；validator、CLI resource、coding materialization 需要 strict 语义。
- 影响：新增 `readTextStrict` / `existsStrict`，只从插件 classloader 读取；保留旧方法兼容非严格调用。
- 后续：无。

### 权限字段需要文档层明确 host-policy 边界

- 背景：`manifest.permissions` / `permission: ui.prompt` 容易被读成 AI4J 自动授权或自动执行 UI/网络行为。
- 发现：当前执行边界仍是 enable / expose / allowlist、Guardrail 和宿主策略代码。
- 影响：docs-site 在 ask-user 和 plugin package 页面补充 declarative metadata 说明。
- 后续：无。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 插件资源 strict 语义 | 新增 strict 方法并只在 validator/runtime 资源消费侧使用 | 最小化 API 破坏，保留旧 fallback 方法兼容历史调用 | 改变 `readText/exists` 默认语义 | accepted |
| ask_user 超大输入处理 | 16 KiB 字符上限并标记 `argumentsTruncated` | 防止超大 tool/command args 生成不受控 envelope，同时不引入 JSON parser 依赖 | 抛异常或静默截断 | accepted |
| lifecycle hook CLI 可见性 | `inspect --runtime` 增加 `lifecycleHooks=` 行 | 与已有 tools/commands/skills/prompts/guardrails inspect 语义一致 | 新增单独 CLI 命令 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要完整权限引擎 | 不在本轮；本轮只修 docs 信任边界 | user / future task | 新权限模型任务开启时 |
