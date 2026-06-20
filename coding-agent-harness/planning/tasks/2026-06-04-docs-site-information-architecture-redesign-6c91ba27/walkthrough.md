# 收口记录：docs site information architecture redesign

## 摘要

本轮完成 docs-site 信息架构重构设计。结论是：docs-site 不应减少功能说明，而应分层承载。首页和 Start Here 负责低门槛成功路径；Feature Map 负责完整列出特色功能；Capability / Reference / Solution 页面负责讲清楚和讲完整；Agent、Coding Agent、FlowGram 等较新能力需要状态标签。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | harness task materials only |
| 新增文件 | `references/docs-site-current-inventory.md`、`references/docs-site-redesign-design.md`、`references/docs-site-page-contracts.md` |
| 删除文件 | 无 |
| 不在范围内 | docs-site 正文改写、sidebar/config 修改、URL/redirect 处理、docs build |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| docs count | `Get-ChildItem -Path docs-site/docs -Recurse -File -Filter *.md` | 232 markdown files | `progress.md` |
| section distribution | 一级目录 markdown 计数 | `core-sdk` 63, `ai-basics` 37, `agent` 36 等 | `references/docs-site-current-inventory.md` |
| sidebar/config scan | 读取 `docs-site/sidebars.ts` 和 `docusaurus.config.ts` | 发现新 sidebar 主线与旧 include 目录并存 | `findings.md` |
| design review | self review | 0 material findings | `review.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self review | 0 | 可提交用户确认 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 未改 docs-site 源文件 | coordinator | yes | 用户确认后开 Wave 1 实施。 |
| 未运行 docs-site build | coordinator | yes | 实施阶段运行 `npm run build`。 |
| 旧目录迁移可能影响外链 | coordinator | yes | 先做 legacy mapping，不直接删除。 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | yes |
| 经验候选详情文件 | `lesson_candidates.md` |
| 是否需要提升共享 lesson？ | no; 本设计尚需实施验证，先保留为 task-local reference。 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 当前盘点 | `references/docs-site-current-inventory.md` |
| 重构设计 | `references/docs-site-redesign-design.md` |
| 页面合同 | `references/docs-site-page-contracts.md` |

Closeout Status: closed
