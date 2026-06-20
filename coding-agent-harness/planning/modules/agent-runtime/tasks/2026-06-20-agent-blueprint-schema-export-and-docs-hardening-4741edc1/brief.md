# Agent Blueprint schema export and docs hardening

## Task ID

`2026-06-20-agent-blueprint-schema-export-and-docs-hardening-4741edc1`

## 创建日期

2026-06-20

## 一句话结果

为 `ai4j.agent/v1` Agent Blueprint 提供内置 JSON Schema、CLI 导出命令和 docs-site 使用说明，让 YAML Agent 具备 IDE 提示、编辑期校验和更清晰的真实运行边界。

## 完成后能得到什么

用户可以通过 `ai4j-cli blueprint schema` 打印内置 Agent Blueprint JSON Schema，或用 `--out` 导出到项目中，再通过 YAML 顶部 `$schema` 获得 IDE / YAML 插件提示。Java 宿主也可以用 `AgentBlueprintSchemas` 读取或写出同一份 schema。docs-site 会说明 schema 只负责 authoring hint，不能替代 `AgentBlueprintValidator`、`AgentFactory`、provider profile、插件启用、tool 注册或 sandbox 创建。

## 交付物

- 可见产物：`AgentBlueprintSchemas`、`ai4j/agent-blueprint.schema.json`、`ai4j-cli blueprint schema`、docs-site Blueprint/command 文档。
- 修改位置：`ai4j-agent/**`、`ai4j-cli/**`、`docs-site/docs/agent/**`、`docs-site/docs/coding-agent/command-reference.md`、本 task package。
- 验证证据：agent/cli targeted JUnit、docs-site typecheck/build、`git diff --check`、Harness status。

## 第一眼应该看什么

1. `ai4j-agent/src/main/resources/ai4j/agent-blueprint.schema.json`
2. `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/blueprint/AgentBlueprintSchemas.java`
3. `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/AgentBlueprintCommand.java`
4. `docs-site/docs/agent/agent-blueprint.md`

## 边界

- 范围内：schema resource、Java accessor、CLI schema 导出、runtime 忽略 `$schema`、相关测试和 docs-site 说明。
- 范围外：不实现完整 JSON Schema validation runtime、不创建真实 sandbox、不运行 live provider、不把 schema 发布到远端 URL、不改变 Blueprint v1 字段语义。
- 停止条件：如果需要引入新的 schema validator 依赖、改变 Blueprint v1 字段、或涉及真实 provider/sandbox，则停下另开任务。

## 完成判断

- [ ] 内置 JSON Schema 覆盖当前 `AgentBlueprintLoader` / `AgentBlueprintValidator` 的主要字段与边界。
- [ ] `$schema` 字段不会被 runtime 误报 unknown field。
- [ ] `ai4j-cli blueprint schema` 可以打印 schema，并可通过 `--out` 写入文件。
- [ ] docs-site 说明 schema 的用法、边界和真实校验链路。
- [ ] Targeted regression 和 docs build 通过并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

完成代码与 docs-site 修改后，运行 targeted JUnit、docs-site typecheck/build、diff check 和 Harness status。
