# 收口记录：docs site wave 1 entrance redesign

## 摘要

Wave 1 已把 docs-site 入口从泛泛功能罗列改成低门槛接入导向：首页提供普通 Java、Spring Boot 和 Feature Map 三条入口；Why AI4J 说明项目现实定位和与大生态框架的边界；Feature Map 作为完整能力索引，按成熟度承接 Core SDK、RAG、MCP、Spring Boot、Agent、Coding Agent、FlowGram 和 Solutions。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `docs-site/` 和当前 harness task package |
| 新增文件 | `docs-site/docs/start-here/feature-map.md` |
| 删除文件 | 无；旧页面未迁移、未删除 |
| 不在范围内 | README、Docusaurus 主题、Core SDK 深页重写、Java 代码、全站目录迁移 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| docs-site production build | `npm run build` in `docs-site/` | 通过；生成 `docs-site/build`，无断链或编译错误 | `progress.md` / ART-005 |
| diff whitespace check | `git diff --check` | 通过；仅 LF/CRLF warning | `progress.md` / ART-006 |
| generated build output status | `git status --short` | `docs-site/build` 未进入 status | `progress.md` |
| harness status | `npx --yes coding-agent-harness status --json .` | 通过；当前任务 `ready-to-confirm`、材料齐全、git clean | `progress.md` / ART-007 |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| coordinator self-review | 0 | 可进入人工审查 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| docs-site 深页仍需要分波次补强 | coordinator / user | yes | 后续 Wave 2/3 按 Feature Map 补 Core SDK、RAG、MCP、Agent 等深页 |
| Feature Map 成熟度标记需要随实现维护 | coordinator | yes | 后续能力稳定或变更时同步更新 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 候选结论 | checked-none；本轮没有新增可复用 harness 治理规则 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| 参考索引 | `references/INDEX.md` |
| 证据索引 | `artifacts/INDEX.md` |
