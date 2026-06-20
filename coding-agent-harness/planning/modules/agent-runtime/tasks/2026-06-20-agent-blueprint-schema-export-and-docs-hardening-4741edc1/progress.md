# Agent Blueprint schema export and docs hardening - 进度

## 状态：审查中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

### [2026-06-20 12:50] - task-start

- 做了什么：开始实现 Agent Blueprint JSON Schema 导出、CLI schema 子命令和 docs-site 文档硬化；目标是提升 YAML Agent 的 IDE 提示、校验可见性和小白上手体验。
- 验证结果：已记录。
- 下一步：实现 schema resource、Java accessor、CLI 命令和文档。
- 证据：command:TARGET:. :`npx --yes coding-agent-harness task-start ...` succeeded

### [2026-06-20 21:19] - 实现与验证

- 做了什么：新增内置 `ai4j.agent/v1` Agent Blueprint JSON Schema、`AgentBlueprintSchemas` Java accessor、`ai4j-cli blueprint schema [--out ...]` 命令；允许 YAML 顶部 `$schema` 作为 IDE authoring hint；同步更新 docs-site 的 Agent Blueprint、真实 API 矩阵和 Coding Agent 命令参考。
- 验证结果：Agent targeted tests、CLI targeted tests、docs-site typecheck/build、CLI package + schema smoke、`git diff --check` 均通过；Harness status failures=0，仅因实现尚未提交显示 dirty-state warning。
- 下一步：提交实现；在干净工作树上推进 EXEC-01 和 Agent Review Submission。
- 证据：command:TARGET:. :`mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintLoaderValidatorTest,AgentBlueprintFactoryTest,AgentBlueprintSchemasTest" -DskipTests=false -DfailIfNoTests=false test` -> Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
- 证据：command:TARGET:. :`mvn -pl ai4j-cli -am "-Dtest=AgentBlueprintCommandTest,AgentBlueprintRunCommandTest,Ai4jCliTest" -DskipTests=false -DfailIfNoTests=false test` -> Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
- 证据：command:TARGET:docs-site :`npm run typecheck` -> pass
- 证据：command:TARGET:docs-site :`npm run build` -> generated static files in build
- 证据：command:TARGET:. :`mvn -pl ai4j-cli -am -DskipTests package` + `java -cp ... Ai4jCliMain blueprint schema` -> schema JSON printed with stable `$id`; JLine dumb-terminal warning is pipe-only smoke noise
- 证据：command:TARGET:. :`git diff --check` -> no whitespace errors; CRLF warnings only
- 证据：command:TARGET:. :`npx --yes coding-agent-harness status --json .` -> failures=0, warnings=1 dirty-state before commit

## 残余

- 远端 schema URL 尚未实际托管；本任务只提供内置 resource、Java accessor 和 CLI 导出。因此 docs-site 默认推荐本地导出文件 `$schema: ./agent-blueprint.schema.json`，不把远端 URL 当作可访问下载入口。
- 该 schema 是 authoring aid，不替代 `AgentBlueprintLoader`、`AgentBlueprintValidator`、`AgentFactory`、host policy、插件安装和 sandbox provider 的运行时校验。
- `npm run build` 生成的 `docs-site/build/` 和 Maven `target/` 均为本地验证产物，不纳入提交。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime module task remains on `feature/agent-blueprint-schema-export`; lifecycle phase/review will update generated governance after implementation commit.
- Harness Ledger update needed：clean-tree lifecycle commands should run after implementation commit: `task-phase EXEC-01 --state done --completion 100 --evidence present` then `task-review`.
- 负责人：coordinator

### [2026-06-20 13:26] - task-review

- 做了什么：Agent Blueprint schema export and docs hardening ready for review: bundled JSON Schema resource, Java accessor, CLI schema export command, docs-site authoring guidance, and targeted regressions passed.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
