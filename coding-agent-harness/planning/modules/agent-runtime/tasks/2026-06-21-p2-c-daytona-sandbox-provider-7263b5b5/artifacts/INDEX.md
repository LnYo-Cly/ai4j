# 任务产物索引

仅在任务产生较多证据或大体量产物时使用，例如命令输出、截图、fixture、生成报告、review transcript、导出的数据文件等。核心任务文件只引用这里的 ID，不粘贴长输出。

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | report | TARGET:ai4j-agent/target/surefire-reports/io.github.lnyocly.agent.daytona.DaytonaSandboxProviderTest.txt | deterministic Daytona provider tests: 5 run, 0 failures, 0 errors, 0 skipped | coordinator |
| ART-002 | report | TARGET:ai4j-agent/target/surefire-reports/io.github.lnyocly.agent.daytona.DaytonaSandboxLiveSmokeTest.txt | opt-in Daytona live smoke: 1 run, 0 failures, 0 errors, 0 skipped; sanitized report contains no secret values | coordinator |
| ART-003 | command | TARGET:. | `mvn -pl ai4j-agent -am -DskipTests=false -Dtest=DaytonaSandboxProviderTest -DfailIfNoTests=false test` passed | coordinator |
| ART-004 | command | TARGET:. | `mvn -pl ai4j-agent -am -DskipTests=false test` passed with extension API 25, core 103, agent 124 | coordinator |
| ART-005 | command | TARGET:docs-site | `npm --prefix docs-site run build` passed after local ignored dependency restore | coordinator |

## 使用规则

- 路径必须可复查；临时终端输出应先保存为稳定文件再登记。
- 产物如果包含敏感信息，先脱敏或改为记录复查方式，不要提交原始敏感内容。
- 与 `review.md`、`progress.md`、walkthrough 互相引用时，使用 `ART-xxx` ID。
