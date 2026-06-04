# 收口记录：docs site modular positioning pass

## 摘要

本轮把 AI4J 的模块独立性从“仓库目录事实”改写成 docs-site 可直接感知的产品卖点：按需取用、最小引入、逐步升级。首页新增 `用多少，取多少` 表；Why AI4J 新增“不是全家桶，而是可渐进升级的 Java AI SDK”；Feature Map 新增 `按模块取用` 表，并用当前 Maven 模块依赖关系支撑文案。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `docs-site/` 和当前 harness task package |
| 新增文件 | 无 |
| 删除文件 | 无 |
| 不在范围内 | Java 代码、Maven 依赖、README、全站深页重写、视觉样式 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| POM module scan | `rg -n "<module>|<artifactId>ai4j|<version>\\$\\{project\\.version\\}" -g "pom.xml"` | 完成；确认模块和内部依赖关系 | `findings.md` / ART-004 |
| docs-site production build | `npm run build` in `docs-site/` | 通过；生成 `docs-site/build`，无断链或编译错误 | `progress.md` / ART-005 |
| diff whitespace check | `git diff --check` | 通过；仅 LF/CRLF warning | `progress.md` / ART-006 |
| generated build output status | `git status --short` | `docs-site/build` 未进入 status | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| coordinator self-review | 0 | 可进入人工审查 | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 每个模块尚未补最小 Maven dependency 示例 | coordinator / user | yes | 后续 docs-site 模块深页任务 |
| 尚未做 artifact 级依赖瘦身审计 | coordinator / user | yes | 需要时开 Maven dependency audit 任务 |

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
