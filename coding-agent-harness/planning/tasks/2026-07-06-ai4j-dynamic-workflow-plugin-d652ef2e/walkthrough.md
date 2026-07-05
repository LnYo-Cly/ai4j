# 收口记录：ai4j dynamic workflow plugin

## 摘要

本任务完成了 AI4J dynamic workflow 样板插件的独立仓库实现，并把 ai4j-sdk docs-site 调整为“独立仓库、单独发布、host-mediated envelope”的口径。首版参考 Michaelliv/pi-dynamic-workflows 的最小核心 contract；QuintinShaw/pi-dynamic-workflows 的后台管理、resume、模型 tier、worktree isolation 等能力记录为后续 host/runtime 方向，不进入 extension-api-only 插件。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | ai4j-sdk docs-site；独立仓库 `G:\My_Project\java\ai4j-plugin-dynamic-workflow` |
| 新增文件 | `docs-site/docs/core-sdk/extension/dynamic-workflow-plugin.md`；独立仓库 Maven / Java / resource / test / CI 文件 |
| 删除文件 | ai4j-sdk 内未保留 reactor module；此前临时模块已移除 |
| 不在范围内 | workflow script executor、subagent scheduler、worktree isolation、模型 tier routing、BOM 收录、Maven Central 发布 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| 独立插件测试 | `mvn -DskipTests=false test` in `G:\My_Project\java\ai4j-plugin-dynamic-workflow` | 通过，7 tests / 0 failures / 0 errors | `progress.md` E-001 |
| clean Maven 兼容 | clean local repo 先安装 ai4j parent POM + extension-api，再执行插件测试 | 通过，证明 GitHub Actions 前置安装方案可用 | `progress.md` E-003 |
| docs typecheck | `npm run typecheck` in docs-site | 通过 | `progress.md` E-004 |
| docs build | `npm run build` in docs-site | 通过，生成 static files | `progress.md` E-004 |
| whitespace | `git diff --check` in ai4j-sdk worktree and standalone plugin repo | 通过，无 whitespace error | `progress.md` E-005 / E-006 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | clean Maven cache 不能直接解析 unpublished `ai4j-extension-api:2.4.0` | 修复 GitHub Actions / README，先安装 ai4j parent POM + extension-api，并用 clean local repo 重验 | `review.md` |
| self review | 无 P0/P1/P2 阻塞发现 | 可提交当前 scope | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 远程 GitHub repo 创建 / push 依赖 auth 和远程命名可用性 | coordinator | yes | 本地 commit 完成后尝试创建 / push；失败则报告本地路径和后续命令 |
| `ai4j-extension-api:2.4.0` 未发布到 Maven Central 前，插件用户需先本地安装 extension API | coordinator | yes | README / CI 已记录；发布后可移除前置安装步骤 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 已完成 |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| Lesson routing | `lesson_candidates.md` |
