# 收口记录：Agent Blueprint schema export and docs hardening

## 摘要

本任务为 `ai4j.agent/v1` Agent Blueprint 增加本地可用的 JSON Schema authoring 合同，并提供 Java accessor 与 CLI 导出命令，让用户可以在 IDE/YAML 插件中获得字段提示和基础结构校验。docs-site 同步说明 schema 的用途和边界：它是 authoring aid，不替代运行时 `AgentBlueprintLoader`、`AgentBlueprintValidator`、`AgentFactory`、host policy、插件安装和 sandbox provider 校验。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`, `ai4j-cli`, `docs-site`, task-local Harness package |
| 新增文件 | `ai4j-agent/src/main/resources/ai4j/agent-blueprint.schema.json`; `AgentBlueprintSchemas.java`; `AgentBlueprintSchemasTest.java`; `AgentBlueprintCommand.java`; `AgentBlueprintCommandTest.java` |
| 删除文件 | 无 |
| 不在范围内 | 远端 schema 托管、live provider 测试、真实 sandbox provider、插件市场发布、运行时 JSON Schema validator 依赖 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Agent targeted tests | `mvn -pl ai4j-agent -am "-Dtest=AgentBlueprintLoaderValidatorTest,AgentBlueprintFactoryTest,AgentBlueprintSchemasTest" -DskipTests=false -DfailIfNoTests=false test` | pass: 20 tests, 0 failures/errors/skips | `progress.md` E-001 |
| CLI targeted tests | `mvn -pl ai4j-cli -am "-Dtest=AgentBlueprintCommandTest,AgentBlueprintRunCommandTest,Ai4jCliTest" -DskipTests=false -DfailIfNoTests=false test` | pass: 39 tests, 0 failures/errors/skips | `progress.md` E-002 |
| docs typecheck | `npm run typecheck` in `docs-site/` | pass | `progress.md` E-003 |
| docs build | `npm run build` in `docs-site/` | pass, static files generated | `progress.md` E-004 |
| CLI package + smoke | `mvn -pl ai4j-cli -am -DskipTests package`; `Ai4jCliMain blueprint schema` | pass, schema JSON printed | `progress.md` E-005 |
| whitespace | `git diff --check` | pass with CRLF warnings only | `progress.md` E-006 |
| Harness status | `npx --yes coding-agent-harness status --json .` | failures=0 before commit; dirty warning expected | `progress.md` E-007 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 0 open material findings | 可提交进入 review；远端 schema 托管和 runtime non-authoring validation 为明确残余 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| schema `$id` URL 尚未托管 | coordinator | yes | 后续 release/docs 部署任务；当前文档默认本地导出 schema 文件 |
| schema 不能验证 provider profile、插件安装、工具存在、sandbox provider 可用 | coordinator | yes | 继续由 runtime validator、host policy 和 fake-provider tests 覆盖 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，`lesson_candidates.md` 判定 checked-none；该任务是 Blueprint schema authoring 的任务特定实现，不沉淀共享 lesson。 |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
