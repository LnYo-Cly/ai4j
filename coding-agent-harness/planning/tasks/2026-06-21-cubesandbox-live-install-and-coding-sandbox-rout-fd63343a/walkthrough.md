# 收口记录：CubeSandbox live install and coding sandbox routing

## 摘要

本任务把 CLI `/sandbox attach cubesandbox|cube <sessionId> [workspaceId]` 升级为连接已有 CubeSandbox live session。`CodingCliSessionRunner` 现在在 attach 前解析 live `SandboxSession`，runtime switch 失败会回滚 binding/session 并关闭新 session；`disable` 和 `run()` 退出会关闭 CLI 持有的 session handle。非 Cube provider 继续 metadata-only，执行时明确失败，不会回退到宿主机本地执行。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-cli`、`docs-site`、`docs/05-TEST-QA`、Harness task package |
| 新增文件 | `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/sandbox/CliSandboxSessionResolver.java`; `DefaultCliSandboxSessionResolver.java`; `DefaultCliSandboxSessionResolverTest.java`; `CodingCliSessionRunnerSandboxTest.java` |
| 删除文件 | 无 |
| 不在范围内 | `/sandbox create/list/destroy/logs`; Docker/E2B/K8s provider; file/git/browser/long process 全量 sandbox routing; 真实 CubeSandbox 本机部署 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| CLI 最小 sandbox 回归 | `mvn -pl ai4j-cli -am "-Dtest=DefaultCliSandboxSessionResolverTest,CodingCliSessionRunnerSandboxTest,CliAttachedSandboxSessionTest,DefaultCodingCliAgentFactoryTest" -DskipTests=false -DfailIfNoTests=false test` | pass, 18 tests | `progress.md` E-001 |
| 扩大 sandbox 回归 | `mvn -pl ai4j-cli -am "-Dtest=*Sandbox*Test,DefaultCodingCliAgentFactoryTest,CodingCliSessionRunnerArgumentParsingTest" -DskipTests=false -DfailIfNoTests=false test` | pass, agent 16 + CLI 21 tests | `progress.md` E-002 |
| 最终 CLI/Slash 回归 | `mvn -pl ai4j-cli -am "-Dtest=*Sandbox*Test,DefaultCodingCliAgentFactoryTest,CodingCliSessionRunnerArgumentParsingTest,SlashCommandControllerTest" -DskipTests=false -DfailIfNoTests=false test` | pass, CLI 70 + agent 16 tests | `progress.md` E-003 |
| docs-site build | `npm --prefix docs-site ci`; `npm --prefix docs-site run build` | pass; first build failed only because ignored `node_modules` missing | `progress.md` E-004 |
| live provider opt-in | `mvn -pl ai4j-agent -am -P live-provider-tests "-Dtest=CubeSandboxLiveProviderTest" -DskipTests=false -DfailIfNoTests=false test` | build success, 1 skipped due missing live env | `progress.md` E-005 |
| 环境探测 | `Get-Command docker,wsl,...`; env var checks; `git ls-remote` | CubeSandbox repo reachable; local Docker/WSL/env/admin requirements missing | `findings.md` F-003 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 无 P0/P1/P2 material finding | 提交待人工确认 | `review.md` |
| subagent attempt | 未完成；TroveBox 502 | 记录为非阻塞备注 | `review.md` N-001 |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 当前机器未跑真实 CubeSandbox live smoke | operator | yes | 准备 Linux/KVM/Docker 或 WSL2+Docker + CubeAPI/template/env 后重跑 `CubeSandboxLiveProviderTest` |
| CLI 尚无 create/list/destroy/logs | maintainer | yes | 开独立 sandbox lifecycle UX 任务 |
| file/git/browser/长进程未全量 sandbox routing | maintainer | yes | 后续 coding tool routing 切片 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | checked-none；本任务无新增治理 lesson |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| 经验候选 | `lesson_candidates.md` |
