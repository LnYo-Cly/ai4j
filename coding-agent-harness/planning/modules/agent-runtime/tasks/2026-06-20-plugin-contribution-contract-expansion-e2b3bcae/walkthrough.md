# 收口记录：Plugin contribution contract expansion

## 摘要

本任务为 AI4J 插件生态增加 manifest-level contribution contract。插件包现在可以声明具体贡献项，宿主可以 inspect / activation plan / validate 这些贡献；Ask User 官方插件提供真实示例；docs-site 新增技术文档解释 capability、contribution、权限和 host binding 边界。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-extension-api`、`ai4j-plugin-ask-user`、`docs-site`，并验证 `ai4j-agent` extension bridge |
| 新增文件 | `ExtensionContribution.java`、`ExtensionContributionType.java`、`docs-site/docs/agent/plugin-contribution-contract.md` |
| 删除文件 | 无 |
| 不在范围内 | 真实 sandbox/runner provider、插件市场、CLI 安装协议、live provider 测试 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Extension API | `mvn -pl ai4j-extension-api -DskipTests=false test` | passed | `progress.md` |
| Ask User plugin | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | passed | `progress.md` |
| Agent bridge | `mvn -pl ai4j-agent -am "-Dtest=ExtensionAgentToolsTest,AgentPluginLifecycleHooksTest" -DskipTests=false -DfailIfNoTests=false test` | passed | `progress.md` |
| Docs site | `npm --prefix docs-site run build` | passed after local dependency install | `progress.md` |
| Diff check | `git diff --check` | passed | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | none | 无 P0/P1/P2 open finding | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| provider-style contribution 只是 metadata | coordinator | yes | 后续 sandbox routing / runner provider task |
| worktree 缺少 engineering-standard.md | coordinator | yes | 标准同步或 rebase 任务 |
| npm audit 既有依赖提示 | docs-site owner | yes | 单独依赖升级任务 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 文档 | `docs-site/docs/agent/plugin-contribution-contract.md` |
