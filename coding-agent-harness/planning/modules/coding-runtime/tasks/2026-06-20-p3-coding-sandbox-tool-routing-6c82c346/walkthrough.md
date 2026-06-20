# 收口记录：P3 Coding sandbox tool routing

## 摘要

本任务完成 `ai4j-coding` sandbox routing 的第一实现切片：宿主通过 `CodingAgentBuilder.sandbox(SandboxSession)` 绑定 live sandbox 后，新建 coding session 会把非敏感 sandbox 摘要绑定到 delegate `AgentSession`，并让内置 `bash action=exec` 通过 `SandboxShellCommandExecutor` 调用 `SandboxSession.execute(SandboxCommand)`。无 sandbox 时继续本地执行。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-coding`、`docs-site`、Regression governance |
| 新增代码 | `CodingSandboxRuntime`、`SandboxShellCommandExecutor` |
| 更新代码 | `CodingAgentBuilder`、`CodingAgent`、`BashToolExecutor`、`ShellCommandResult`、`LocalShellCommandExecutor` |
| 新增/更新测试 | `BashToolExecutorTest`、`CodingAgentBuilderTest` |
| 新增/更新文档 | `docs-site/docs/coding-agent/sandbox-routing.md`、`tools-and-approvals.md`、`agent/sdk-roadmap.md`、`sidebars.ts` |
| 不在范围内 | real sandbox provider、file/write/patch/browser/git routing、CLI `/sandbox` |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Targeted coding tests | `mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` | passed, 14 coding tests | `progress.md` |
| Broad coding regression | `mvn -pl ai4j-coding -am -DskipTests=false test` | passed, extension API 25 / core 103 / agent 119 / coding 61 | `progress.md` |
| Docs build | `npm --prefix docs-site run build` | passed after restoring ignored local dependencies | `progress.md` |
| Regression governance | RG-003/RG-008/SRB-057 | updated | `docs/05-TEST-QA/` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self architecture/security review | 无阻塞发现 | 后续未实现能力作为 residual 跟踪 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| file/write/patch/browser/git/project-run 尚未 sandbox routing | coordinator | yes | 后续 P3 切片 |
| `bash start/status/logs/write/stop/list` 尚未远端化 | coordinator | yes | 后续 bash process routing task |
| CLI `/sandbox` UX 尚未实现 | coordinator | yes | P4 CLI sandbox commands |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes，当前无全局 lesson candidate |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 发现记录 | `findings.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 技术文档 | `docs-site/docs/coding-agent/sandbox-routing.md` |
