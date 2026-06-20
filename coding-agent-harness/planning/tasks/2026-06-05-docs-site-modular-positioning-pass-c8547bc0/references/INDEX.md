# 任务参考资料索引

| ID | Type | Path | Summary | Used By |
| --- | --- | --- | --- | --- |
| REF-001 | code | `pom.xml` | 根 Maven 模块列表，用于确认 docs-site 模块取用表不脱离工程事实。 | coordinator / reviewer |
| REF-002 | code | `ai4j-agent/pom.xml`、`ai4j-coding/pom.xml`、`ai4j-cli/pom.xml` | Agent、Coding、CLI 的逐层依赖关系。 | coordinator / reviewer |
| REF-003 | code | `ai4j-spring-boot-starter/pom.xml`、`ai4j-flowgram-spring-boot-starter/pom.xml`、`ai4j-flowgram-demo/pom.xml` | Spring Boot 和 FlowGram 路径的依赖关系。 | coordinator / reviewer |
| REF-004 | code | `ai4j-bom/pom.xml` | BOM 管理的 artifact 列表。 | coordinator / reviewer |
| REF-005 | code | `docs-site/docs/intro.md` | 首页 modular positioning 修改目标。 | coordinator / reviewer |
| REF-006 | code | `docs-site/docs/start-here/why-ai4j.md` | Why AI4J modular positioning 修改目标。 | coordinator / reviewer |
| REF-007 | code | `docs-site/docs/start-here/feature-map.md` | Feature Map 模块取用表修改目标。 | coordinator / reviewer |
| REF-008 | private-plan | `coding-agent-harness/planning/tasks/2026-06-04-docs-site-wave-1-entrance-redesign-54198b78/walkthrough.md` | 前一轮入口改造边界和残余。 | coordinator / reviewer |

## 使用规则

- 本任务无新增外部资料。
- POM 只作为模块关系事实来源，不在本任务修改。
- reviewer 重点检查 REF-001..REF-004 是否支撑三页文案中的模块关系表述。
