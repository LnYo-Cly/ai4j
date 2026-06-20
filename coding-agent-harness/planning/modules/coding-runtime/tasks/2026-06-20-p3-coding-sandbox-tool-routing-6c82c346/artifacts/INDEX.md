# 任务产物索引

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | command | TARGET:. | `mvn -pl ai4j-coding -am "-Dtest=BashToolExecutorTest,CodingAgentBuilderTest" -DskipTests=false -DfailIfNoTests=false test` passed; 14 coding tests covered direct bash executor and coding agent loop routing. | coordinator |
| ART-002 | command | TARGET:. | `mvn -pl ai4j-coding -am -DskipTests=false test` passed; extension API 25, core 103, agent 119, coding 61 tests. | coordinator |
| ART-003 | command | TARGET:docs-site | `npm --prefix docs-site run build` passed after restoring ignored `docs-site/node_modules` with `npm --prefix docs-site install`. | coordinator |
| ART-004 | diff | TARGET:ai4j-coding/src/main/java/io/github/lnyocly/ai4j/coding | Sandbox runtime handle, shell executor routing, builder/session wiring, and shell result execution metadata. | coordinator |
| ART-005 | diff | TARGET:docs-site/docs/coding-agent/sandbox-routing.md | Canonical docs for P3 first slice, including implemented API and explicit non-goals. | coordinator |
| ART-006 | diff | TARGET:docs/05-TEST-QA | RG-003, RG-008, and SRB-057 evidence updates for this fixed regression surface. | coordinator |

## 使用规则

- 路径必须可复查；临时终端输出应先保存为稳定文件再登记。
- 产物如果包含敏感信息，先脱敏或改为记录复查方式，不要提交原始敏感内容。
- 与 `review.md`、`progress.md`、walkthrough 互相引用时，使用 `ART-xxx` ID。
