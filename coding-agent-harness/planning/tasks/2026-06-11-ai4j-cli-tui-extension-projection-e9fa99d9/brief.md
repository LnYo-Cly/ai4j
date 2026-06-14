# AI4J CLI TUI extension projection

## Task ID

`2026-06-11-ai4j-cli-tui-extension-projection-e9fa99d9`

## 创建日期

2026-06-11

## 一句话结果

AI4J CLI 的 TUI 现在可以直接发现并执行现有 extension 命令，`/extensions` 与 `/extension ...` 入口、帮助、命令面板和补全都已经接到同一条命令链路上。

## 完成后能得到什么

用户在 TUI 里可以直接查看扩展列表、检查单个扩展、预览/校验激活方案，以及通过同一条命令链路运行扩展命令或读取扩展资源。下一轮 agent 不需要再猜扩展命令入口，也不需要重复实现 extension 解析逻辑，只要继续沿用 `CliExtensionCommand` 即可。这次结果可直接用于后续扩展生态、插件作者文档和更完整的 TUI 交互升级。

## 交付物

- 可见产物：`/extensions`、`/extension ...`、TUI 帮助和命令面板中的 extension 入口
- 修改位置：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`、`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java`、`ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
- 验证证据：`mvn -pl ai4j-cli -am -DskipTests=false test`

## 第一眼应该看什么

先看 `progress.md`、`review.md` 和最近的 `git diff`。如果要继续扩展 TUI，先读 `CodingCliSessionRunner.java` 里 `/extension` 的薄适配层，再看 `SlashCommandController.java` 的补全规则。

## 边界

- 范围内：ai4j-cli 的 TUI slash command、命令面板、帮助文案、extension 命令投影、相关单元测试和任务包收口。
- 范围外：extension API 核心实现、扩展注册机制重写、Pi/Claude 级别的 TUI 全面重构、docs-site 重写。
- 停止条件：如果需要改 extension 核心协议、引入新命令语义或改动更广泛的 CLI runtime 行为，必须先回到 coordinator。

## 完成判断

1. TUI 根建议和补全里能看到 `/extensions` 与 `/extension `。
2. `/extensions` 会在 TUI 中直接展示扩展列表。
3. `/extension inspect|plan|check|validate|run|resource ...` 复用现有 `CliExtensionCommand` 执行。
4. 命令帮助和命令面板都已经暴露 extension 入口。
5. `ai4j-cli` 带依赖回归通过。

## 执行合同

- Owner：coordinator
- 生命周期状态：未开始
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

提交审查材料并等待人工确认。
