# AI4J Extension Check Gate

## Task ID

`2026-06-11-ai4j-extension-check-gate-d3f91b18`

## 创建日期

2026-06-11

## 一句话结果

新增一个可放进插件作者 CI 或宿主接入前检查流程的 `ai4j-cli extension check` 门禁命令。

## 完成后能得到什么

插件作者和使用者不再需要人工阅读 `validate` 与 `plan` 两段输出后自行判断接入 recipe 是否可用。`extension check` 会先复用现有 `ExtensionValidator`，再复用 activation plan 检查命令行中显式声明的 `--expose-tool` 与 `--allow-*` 资源是否已经 active。验证错误、未启用插件、请求的资源不存在或仍 inactive 时，命令返回非零退出码；未请求的资源不会被强制启用，保持最小权限接入模型。

## 交付物

- 可见产物：`ai4j-cli extension check <id> --enable ...`
- 修改位置：`ai4j-cli` CLI 命令、CLI 测试、extension docs、Regression SSoT / Cadence Ledger、Feature SSoT
- 验证证据：CLI targeted test、docs-site typecheck/build、harness status、diff check

## 第一眼应该看什么

先读 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java` 中 `check` 分支和 `Ai4jCliTest` 里新增的 passing / failing gate tests，再看 docs-site 中 `Plugin Author Cookbook` 与 `Plugin Recipes` 的命令说明。

## 边界

- 范围内：新增 CLI check gate；复用现有 validator / activation plan；补测试、文档、回归记录和任务材料。
- 范围外：远程 marketplace、自动安装插件依赖、运行时 jar 热加载、provider 自动注册、插件仓库托管、改变现有 `plan` 退出码语义。
- 停止条件：如果 `check` 需要改变公共 extension API 或要求所有贡献资源都 active，必须暂停并重新确认，因为这会扩大最小权限设计。

## 完成判断

- `extension check` 校验通过时返回 0，并打印 validation、activation plan 与 check summary。
- validation error、未 `--enable`、或命令行显式请求的资源 inactive 时返回非零。
- 未请求的资源不会导致 check 失败，避免把最小权限 recipe 变成全资源启用。
- docs-site 和 scaffold README 说明 check gate 的推荐用法。
- CLI targeted tests、docs-site typecheck/build 和 harness status 通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

实现 `check` 命令和 targeted CLI tests。
