# Extension plugin contract hardening

## Task ID

`2026-06-10-extension-plugin-contract-hardening-272a10c4`

## 创建日期

2026-06-10

## 一句话结果

AI4J extension plugin 的公共命名、tool schema 校验、CLI 参数校验、scaffold 回归和文档信任边界被收紧到可审查状态。

## 完成后能得到什么

完成后，第三方插件作者和 SDK 使用者能拿到更明确的扩展包契约：extension id、tool/command/resource/guardrail name 会在构建对象、注册、CLI 参数和 validator 路径中尽早失败；tool schema 不再只靠字符串外观检查，而是解析 JSON 并验证 AI4J 当前 mapper 需要的基础结构；CLI scaffold 有编译和 ServiceLoader 烟测兜底；docs-site 明确 `apply(...)` 轻量注册、`enable(...)` 对非 tool 资源的整包信任边界，以及当前尚未实现细粒度 allowlist。

## 交付物

- 可见产物：extension API 契约硬化、CLI scaffold 回归、插件作者/使用者文档更新、回归治理记录。
- 修改位置：`ai4j-extension-api/`、`ai4j-cli/`、`docs-site/docs/core-sdk/extension/`、`docs/05-TEST-QA/`、`docs/09-PLANNING/Feature-SSoT.md`。
- 验证证据：`progress.md` 中记录的 extension API、agent adapter、Ask User plugin、CLI targeted、完整 CLI 依赖链、docs-site build/typecheck、monorepo package 和 diff check。

## 第一眼应该看什么

先读 `review.md` 的 Evidence Checked 和残余风险，再读 `progress.md` 的命令证据；如果要判断实现边界，读 `task_plan.md` 的范围和 `findings.md` 的技术决策。

## 边界

- 范围内：extension API 公共 ID/name contract、validator tool schema shape check、CLI extension 参数和 scaffold smoke、插件 docs-site、Regression SSoT/Cadence 和任务 closeout。
- 范围外：远程 marketplace、运行时 jar 热加载、provider 自动注册、command/Skill/Prompt/Guardrail 细粒度 allowlist、新插件权限模型。
- 停止条件：如果发现需要破坏现有公共 API、引入新的运行时依赖或改变插件权限模型，必须停止并交回 maintainer 决策。

## 完成判断

- malformed JSON schema 即使包含 `"type"` 文本也会被 validator 拒绝。
- extension id、tool/command/resource/guardrail name 使用统一公共命名契约，CLI slash-prefixed command 输入仍兼容。
- generated plugin scaffold 能被 `JavaCompiler` 编译，并能通过 `ServiceLoader` 发现 extension。
- docs-site 明确 `apply(...)` side-effect-light、`enable(...)` 整包信任和当前非 tool 资源 allowlist 边界。
- 目标回归和治理 closeout 已记录，任务已提交 Agent Review Submission。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

等待人工 review confirmation；agent 不代办 human confirmation。如果人工退回，按 `review.md` findings 或 dashboard repair prompt 继续修复。
