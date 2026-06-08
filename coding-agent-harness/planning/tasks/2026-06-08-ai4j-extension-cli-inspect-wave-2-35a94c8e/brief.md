# AI4J extension CLI inspect wave 2

## Task ID

`2026-06-08-ai4j-extension-cli-inspect-wave-2-35a94c8e`

## 创建日期

2026-06-08

## 一句话结果

`ai4j-cli extension list/inspect` 能发现 classpath 上的 AI4J extension，并展示 manifest 与可选 runtime 贡献清单。

## 完成后能得到什么

完成后，使用者可以先通过 CLI 审查已加入 classpath 的扩展包，而不是直接把扩展能力接入 agent runtime。`extension list` 展示发现数量、id、名称、版本、能力和来源类；`extension inspect <id>` 展示 manifest、权限和配置前缀；`--runtime` 才临时执行 extension 的 `apply()`，列出贡献的 tool/command/skill/prompt/guardrail 名称。这个结果用于 Wave 3 之前的安全审查、文档演示和第三方扩展开发调试。

## 交付物

- 可见产物：`ai4j-cli extension list`、`ai4j-cli extension inspect <id> [--runtime]`
- 修改位置：`ai4j-cli/`、必要时同步 `ai4j-extension-api` regression/governance
- 验证证据：CLI JUnit tests、`mvn -pl ai4j-cli -am -DskipTests=false test`、`git diff --check`、harness status

## 第一眼应该看什么

先读 `task_plan.md` 的范围边界，再读 `findings.md` 的设计决策；实现完成后看 `progress.md` 和 `review.md`。

## 边界

- 范围内：CLI top-level `extension` 命令、classpath discovery、manifest inspect、可选 runtime contribution inspect、CLI 测试和回归记录。
- 范围外：`extension install`、持久化 enable 配置、Spring Boot properties、Agent/Coding runtime adapter、Marketplace、jar hotload。
- 停止条件：如果需要让扩展工具真正进入 agent 执行面，停止并开 Wave 3 adapter 任务。

## 完成判断

- `ai4j-cli extension list` 可列出 ServiceLoader 发现的扩展。
- `ai4j-cli extension inspect <id>` 不执行 `apply()` 也能展示 manifest。
- `ai4j-cli extension inspect <id> --runtime` 可列出贡献资源，但不持久启用。
- CLI targeted tests 和 harness status 通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：未开始
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补齐 task plan / execution strategy 后，实现 `CliExtensionCommand` 并接入 `Ai4jCli`。
