# AI4J extension command execution wave 5

## Task ID

`2026-06-09-ai4j-extension-command-execution-wave-5-3b0bed77`

## 创建日期

2026-06-09

## 一句话结果

AI4J CLI 可以通过 `extension run --enable <extension-id> <command> [arguments...]` 显式执行已启用插件包提供的 command。

## 完成后能得到什么

使用者把第三方插件 jar 放到 classpath 后，不只能 `extension list / inspect` 查看插件，还能在 CLI 中显式启用插件并运行插件 command。这让第三方插件从“可被发现和审阅”进入“可被人手动调用”的阶段，同时仍保留安全边界：classpath 发现不会执行 command，`run` 必须带 `--enable`，模型可见工具仍然只走 `exposeTool` / Spring Boot `ai.extensions.tools.expose`。

## 交付物

- 可见产物：`ai4j-cli extension run --enable <id> <command> [arguments...]`
- 修改位置：`ai4j-cli`、`docs-site/docs/core-sdk/extension/plugin-packages.md`、harness task package、Regression SSoT / Cadence Ledger
- 验证证据：`Ai4jCliTest` targeted regression、monorepo package smoke、docs-site typecheck/build、diff check、harness status

## 第一眼应该看什么

先读 `task_plan.md` 的边界和验收标准，再看 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java` 与 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/Ai4jCliTest.java`。文档侧看 `docs-site/docs/core-sdk/extension/plugin-packages.md` 的 CLI 命令执行路径。

## 边界

- 范围内：CLI `extension run` 子命令、显式 `--enable` 门禁、command handler 调用、CLI tests、插件文档和治理记录。
- 范围外：CLI 自动安装插件、远程 marketplace、运行时 jar 热加载、provider plugin、Agent/Coding Agent 新能力、Spring Boot 新属性。
- 停止条件：如果需要改变 `ai4j-extension-api` 公共合同或让插件自动执行，必须停止并重新确认范围。

## 完成判断

- CLI 未显式 `--enable` 时拒绝执行插件 command。
- CLI 显式启用插件后能执行 command handler 并输出结果。
- CLI 支持 slash-prefixed command 名称和 command 参数中的 `--flag`。
- 未知 command 返回稳定 extension error。
- 相关 Java/docs/harness 验证通过并记录到 `progress.md`。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补齐 Wave 5 CLI command execution 实现、测试、文档和验证证据。
