# 收口记录：AI4J extension runtime adapter wave 3

## Overview

本轮把 `ai4j-extension-api` 的插件资源接入 Agent / Coding Agent 运行时，并补齐 docs-site 插件包生态说明。

## Scope

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-agent`、`ai4j-coding`、`docs-site`、root `README.md`、harness task package、regression governance docs |
| 新增文件 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/extension/*`、`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/tool/RoutingToolExecutor.java`、`ai4j-agent/src/test/java/io/github/lnyocly/agent/ExtensionAgentToolsTest.java`、`docs-site/docs/core-sdk/extension/plugin-packages.md` |
| 删除文件 | none |
| 不在范围内 | marketplace、CLI 自动安装插件、运行时热加载 jar、provider 自动注册、Spring Boot 配置化插件装配、R-008 修复 |

## Key Decisions

| Decision | Choice | Reason |
| --- | --- | --- |
| Runtime adapter | `ExtensionAgentTools` 转成现有 `AgentToolRegistry` / `ToolExecutor` | 不改 Agent / Coding Agent 主循环，复用已有 tool call 和 tool result 合同 |
| Safety gate | 保持 discover / enable / expose 三段式 | 插件工具可能触发外部 API、文件系统或工作区操作，必须由宿主显式暴露 |
| Coding runtime | 复用 `ai4j-agent` adapter | `ai4j-coding` 已依赖 `ai4j-agent`，避免重复 mapper |
| Docs wording | 明确写出当前不包含 marketplace / hotload / provider plugin | 避免把尚未实现的能力包装成已有能力 |

## Verification

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| RG-010 | `mvn -pl ai4j-extension-api -DskipTests=false test` | pass, 8 tests | `progress.md` |
| RG-002 targeted | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` | pass, 4 tests | `progress.md` |
| RG-003 targeted | `mvn -pl ai4j-coding -am "-Dtest=CodingAgentBuilderTest,ExtensionAgentToolsTest" -DfailIfNoTests=false -DskipTests=false test` | pass, 4 + 7 tests | `progress.md` |
| RG-004 targeted | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | pass, 8 tests | `progress.md` |
| RG-007 | `mvn -DskipTests package` | pass, 10 reactor modules | `progress.md` |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` | pass | `progress.md` |
| RG-008 | `NODE_OPTIONS=--max-old-space-size=8192 npm run build` | pass, generated `docs-site/build` | `progress.md` |

Evidence depth reached：L1 tests + L2 local_smoke。

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self adversarial review | 0 open material findings | 可提交 Agent Review Submission；人工确认仍需用户/维护者完成 | `review.md` |

## Residual

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| Full `mvn -pl ai4j-agent -am -DskipTests=false test` 仍受既有 R-008 阻塞 | coordinator | yes | 后续单独修复 `HandoffPolicyTest` |
| Spring Boot 配置化插件装配未实现 | owner / coordinator | yes | 后续插件生态任务单独规划 |
| Marketplace / hotload 未实现 | owner / coordinator | yes | 当前 docs 明确写为不包含能力 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结论 | no-candidate-accepted，本轮没有需要 promotion 的通用流程经验 |

## Links

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Feature SSoT row | F-025 |
| Regression gates | RG-010、RG-002 targeted、RG-003 targeted、RG-004 targeted、RG-007、RG-008 |
| Branch / Worktree | `main`，未使用 dedicated worktree；用户要求在当前线性工作上继续完成并推送 |
| Commit | pending until final git commit |
