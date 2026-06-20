# P1-C CLI run Agent Blueprint YAML

## Task ID

`2026-06-20-p1-c-cli-run-agent-blueprint-yaml-377e1f25`

## 创建日期

2026-06-20

## 一句话结果

在 `ai4j-cli` 增加 `run <agent.yaml>` 命令，让用户可以从终端加载、校验并运行一次单 Agent Blueprint YAML，同时保持 token/profile/sandbox 边界清晰。

## 完成后能得到什么

完成后，开发者可以执行 `ai4j-cli run agent.yaml --input "..."` 直接运行 P1-A/P1-B 的单 Agent Blueprint。CLI host 负责解析 provider/profile/model/base-url/api-key 等运行时配置，并把创建好的 `AgentModelClient` 显式交给 `AgentFactory`；YAML 本身不保存 token，`AgentFactory` 也不读取本机 profile。这个结果把声明式 Agent 从 Java API 推进到终端入口，为后续 `ai4j` 主命令、TUI、sandbox 状态和远端 Runner 打基础。

## 交付物

- 可见产物：`ai4j-cli run <agent.yaml> --input <task>` 命令、run command options/model-client factory、profile 解析 guard、CLI 单测和 docs-site Blueprint 文档。
- 修改位置：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/**`、`ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/**`、`docs-site/docs/agent/**`、本任务包、Regression SSoT / Cadence Ledger。
- 验证证据：targeted `AgentBlueprintRunCommandTest,Ai4jCliTest`、broad `mvn -pl ai4j-cli -am -DskipTests=false test`、docs-site build、Harness status。

## 第一眼应该看什么

1. `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/AgentBlueprintRunCommand.java`
2. `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/command/AgentBlueprintRunCommandTest.java`
3. `docs-site/docs/agent/agent-blueprint.md` 的 “用 CLI 运行一份 Agent YAML” 小节
4. `progress.md` 与 `review.md` 的验证记录

## 边界

- 范围内：top-level `run` dispatch、YAML load/validate/factory run、CLI host provider/profile/model resolution、deterministic error rendering、docs-site 技术文档、回归治理记录。
- 范围外：真实 sandbox provider、插件安装/扫描、Team/Workflow Blueprint、TUI 全量重构、live provider 测试、把 provider token 写入 YAML/fixture/docs。
- 停止条件：如果需要真实 VM/容器 sandbox、外部 provider live 验证、CLI TUI 重构或新增 Maven 模块，应停止并拆到 P2/P4/P5 任务。

## 完成判断

- `ai4j-cli run <agent.yaml> --input <task>` 能加载单 Agent Blueprint，并通过 host-supplied `AgentModelClient` 运行一次。
- CLI 支持 `--provider`、`--protocol`、`--model`、`--profile`、`--api-key`、`--base-url`、`--workspace`、`--allow-sandbox-declaration`、`--verbose`。
- YAML `model.profile` / `--profile` 不存在或与显式 provider 不兼容时失败，避免静默回落到错误 profile。
- `sandbox.enabled=true` 默认被拒绝；`--allow-sandbox-declaration` 只允许声明通过，不创建真实 sandbox。
- targeted / broad Maven 回归、docs-site build、Harness status 均通过或 residual 明确。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`、`lesson_candidates.md`
- 完成条件：验证证据记录到 `progress.md`，并通过 `task-review` 进入 review / ready-to-confirm 状态。

## 当前下一步

运行 broad CLI 回归和 docs-site build；补齐 Regression SSoT / Cadence Ledger 后提交 P1-C review。
