# 收口记录：CubeSandbox sandbox provider adapter

## 摘要

已在 `ai4j-agent` 新增命令级 CubeSandbox Sandbox SPI Provider：宿主可以显式创建或连接 CubeSandbox session，并通过 envd Connect `/process.Process/Start` 执行 `/bin/bash -l -c <command>`。本任务没有把 `ai4j-coding` 的所有 file/git/browser 工具迁入沙箱，也没有实现 files/Jupyter/snapshot/browser；这些已作为后续边界记录。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent` sandbox provider；`docs-site` Agent docs；`docs/05-TEST-QA` regression governance；task-local Harness materials |
| 新增文件 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/cubesandbox/*.java`; `ai4j-agent/src/test/java/io/github/lnyocly/agent/cubesandbox/*.java`; `docs-site/docs/agent/cubesandbox-provider.md` |
| 删除文件 | 无 |
| 不在范围内 | `ai4j-coding` 全量 sandbox routing；files API；Jupyter/code-interpreter；snapshot/rollback；browser capability；云端 sandbox runner 产品化 |

## 主要实现

- `CubeSandboxProvider`：实现 `SandboxProvider`，支持 `createSession(SandboxSpec)`、`connect(String, SandboxSpec)`、`health()`。
- `CubeSandboxConfig`：支持 env / builder / non-secret spec config；密钥只来自 provider/env，不从 `SandboxSpec.config` 读取。
- `CubeSandboxClient`：实现 CubeAPI create/connect/delete 与 envd Connect command execution；支持 `proxyNodeIp` 的 raw HTTP/1.1 Host preservation；新增 header CR/LF 拒绝。
- `CubeSandboxSession`：把命令执行结果映射到 `SandboxResult`，并区分新建 sandbox close destroy 与 connect-existing close no-delete。
- `CubeSandboxProviderTest`：协议级本地 HTTP server 测试，不是 Java 方法 mock；覆盖 create/connect/delete、Connect envelope、stdout/stderr/error、partial frame、49999 envdPort override、invalid domain/header safety、requestTimeoutMillis、metadata/label filtering。
- `CubeSandboxLiveProviderTest`：真实 live opt-in hook，缺 env 时 JUnit Assume skip。

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| CubeSandbox protocol targeted | `mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest" -DskipTests=false -DfailIfNoTests=false test` | passed, 8 tests | 本地 Maven 输出；`progress.md` |
| CubeSandbox + Sandbox SPI combined | `mvn -pl ai4j-agent -am "-Dtest=CubeSandboxProviderTest,AgentSandboxSpiModelTest,AgentSessionSandboxBindingTest" -DskipTests=false -DfailIfNoTests=false test` | passed, 16 tests | 本地 Maven 输出；`progress.md` |
| Agent broad regression | `mvn -pl ai4j-agent -am -DskipTests=false test` | passed, extension API 31 + core 103 + agent 137 tests | 本地 Maven 输出；`progress.md` |
| Docs build | `npm --prefix docs-site run build` | passed, static files generated in `docs-site/build` | 本地 npm/Docusaurus 输出 |
| Live CubeSandbox opt-in | `mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test` | build success, 1 skipped because required live env absent | 本地 Maven output；LV-002 pending-env |
| Diff hygiene | `git diff --check` | passed with CRLF warnings only | 本地 git output |
| Secret scan | changed Java/Markdown/TS/XML/YAML/JSON/properties files scanned for common key/token patterns | no real secret found; matches were code header names / lesson token text | local Select-String output |
| Harness status | `npx --yes coding-agent-harness status --json .` | failures=0, one dirty-state warning before commit; current task `materialsReady=true`, lesson complete | local Harness JSON summary |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| Subagent `019ee672-860b-7593-9738-d3fd17d84b39` | P1 envd port discrepancy; P1 raw header injection; P1 ignored docs page; P2 metadata/labels overexposure | closed/mitigated: added `CUBE_ENVD_PORT`, header safety, host validation, force-add plan for docs page, metadata/label filtering | `review.md` MF-001..MF-004; `CubeSandboxProviderTest` |
| Self review | live env absent; docs must not claim full coding-agent sandbox | accepted residual; docs explicitly scope command-level provider only | `docs-site/docs/agent/cubesandbox-provider.md`; `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 当前 shell 无真实 CubeSandbox endpoint/template/key，live execution 未运行 | user/operator | yes | 设置 `AI4J_CUBESANDBOX_LIVE=true`、`CUBE_API_URL`/`E2B_API_URL`、`CUBE_TEMPLATE_ID`、必要 key/proxy/`CUBE_ENVD_PORT` 后运行 `CubeSandboxLiveProviderTest` |
| `proxyNodeIp + https` 直连未实现 | coordinator | yes | 后续 hardening 任务设计 TLS/SNI/Host preservation |
| files/Jupyter/snapshot/browser/coding tool routing 未实现 | coordinator | yes | 后续 sandbox expansion 与 `ai4j-coding` routing 任务 |
| `docs-site/docs/agent/cubesandbox-provider.md` 被 `.gitignore` 忽略 | coordinator | mitigated | 本次提交必须 `git add -f` 纳入版本库 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | `checked-none:task-specific-cubesandbox-provider-adapter`；本任务无新增通用 Harness lesson，已有 testing/live-provider/secret 规则足够覆盖。 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 发现记录 | `findings.md` |
| 进度记录 | `progress.md` |
| 经验候选 | `lesson_candidates.md` |
