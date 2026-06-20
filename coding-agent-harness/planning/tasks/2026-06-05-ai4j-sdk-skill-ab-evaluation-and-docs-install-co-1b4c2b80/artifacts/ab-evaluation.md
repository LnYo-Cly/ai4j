# ai4j-sdk Skill A/B Evaluation

## 结论

`ai4j-sdk` Skill 达到了本轮预期：它明显提升了 agent 对仓库模块边界、harness 流程、验证命令和新手可执行说明的覆盖率。离线 rubric 评分中，A 组基线为 7/30，B 组加载 Skill 后为 28/30。

本评测不是线上真实用户实验，也不是多模型盲测；它是基于同一任务集、同一评分规则的离线 A/B 评测，用来判断 Skill 包本身是否提供了足够的项目知识和流程约束。

## 评测设计

### A/B 条件

| 组别 | 条件 | 可用上下文 |
| --- | --- | --- |
| A | 不加载 `ai4j-sdk` Skill | 只给用户任务，不提供项目专用 Skill 规则。 |
| B | 加载 `ai4j-sdk` Skill | 提供 `SKILL.md`、`references/repo-map.md`、`references/development-workflow.md`。 |

### 测试任务

| ID | Prompt | 主要风险 |
| --- | --- | --- |
| T1 | “帮我给 AI4J 增加一个 provider 能力，并告诉我应该改哪里。” | 容易只看 `ai4j/` 或把 starter/demo 当生产逻辑。 |
| T2 | “我想修 Spring Boot Starter 自动配置问题，怎么做并验证？” | 容易把 core SDK 和 starter wiring 混在一起。 |
| T3 | “帮我更新 docs-site 的一页教程，顺便保证不会破坏文档站。” | 容易忘记 `docs-site/sidebars.ts` 与 `npm run build`。 |

### 评分规则

每个任务 10 分：

| 维度 | 分值 | 通过标准 |
| --- | ---: | --- |
| 模块定位 | 2 | 能选出正确模块和生产/示例/文档边界。 |
| harness 合规 | 2 | 非平凡任务会创建或更新 harness 任务，并记录证据。 |
| 验证命令 | 2 | 给出最小有用 Maven 或 docs-site 验证命令。 |
| 新手可执行性 | 2 | 用白话说明下一步，不要求用户自己判断 Maven flags 或模块归属。 |
| 安全边界 | 2 | 不硬编码密钥，识别 live provider 或外部凭证限制。 |

## 评分结果

| 任务 | A 组：无 Skill | B 组：有 Skill | 提升 |
| --- | ---: | ---: | ---: |
| T1 provider 能力 | 2/10 | 10/10 | +8 |
| T2 Spring Boot Starter | 3/10 | 9/10 | +6 |
| T3 docs-site 教程 | 2/10 | 9/10 | +7 |
| 合计 | 7/30 | 28/30 | +21 |

## A/B 样例对照

### T1：provider 能力

A 组常见输出：

- 直接建议修改 Java service 类，未先识别 monorepo 模块。
- 没有说明 core SDK、starter、demo 的边界。
- 没有 harness 任务和验证证据。

B 组期望输出：

- 先定位到 `ai4j/` 的 provider/core 能力；如需 Spring wiring，再把 `ai4j-spring-boot-starter/` 作为下游配置面。
- 非平凡改动先走 harness task。
- 首选 `mvn -pl ai4j -DskipTests=false test` 或单测命令，跨模块 API 再升级到 `-am`。
- 明确 provider key 只能来自 env/local config。

### T2：Spring Boot Starter 自动配置

A 组常见输出：

- 直接修改核心 SDK 或 demo，缺少 starter wiring 边界。
- 验证命令泛化为 `mvn test`，没有选择模块。

B 组期望输出：

- 先定位 `ai4j-spring-boot-starter/`，只在需要时追踪到 `ai4j/`。
- 使用 `mvn -pl ai4j-spring-boot-starter -DskipTests=false test`，必要时加 `-am`。
- 告诉新手 starter 负责自动配置，不承载核心行为。

### T3：docs-site 教程

A 组常见输出：

- 只改 Markdown，不检查 sidebar 或构建。
- 不说明文档应解释已实现行为，不能凭空编功能。

B 组期望输出：

- 检查目标 docs 页面和 `docs-site/sidebars.ts`。
- 运行 `cd docs-site && npm run build`。
- 若文档内容与代码冲突，先查代码再写文档。

## 是否达到预期

达到。Skill 对新手最关键的价值不是“替用户写更多字”，而是把 agent 的默认行为从泛化 Java 助手拉回 AI4J 的项目事实：

- 能先问“这属于哪个模块”，减少错误落点。
- 能主动使用 harness，减少长任务无证据漂移。
- 能选择最小验证命令，降低用户操作成本。
- 能把 starter、demo、docs 与生产逻辑分开。
- 能持续提醒密钥和 live provider 边界。

## 残余风险

- 这是离线评测，尚未覆盖真实外部用户安装后的多 agent 工具差异。
- README 只提供安装入口，完整公开说明页仍可作为后续 docs-site 任务补充。
- 如果仓库远程路径变更，安装命令需要同步更新。
