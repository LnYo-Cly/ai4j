# 任务产物索引

仅在任务产生较多证据或大体量产物时使用，例如命令输出、截图、fixture、生成报告、review transcript、导出的数据文件等。核心任务文件只引用这里的 ID，不粘贴长输出。

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | command | TARGET:. | `mvn -pl ai4j-extension-api -DskipTests=false test` passed with 8 tests. | coordinator |
| ART-002 | command | TARGET:. | `mvn -pl ai4j-coding -am -Dtest=CodingSkillSupportTest -DfailIfNoTests=false -DskipTests=false test` passed with 3 tests. | coordinator |
| ART-003 | command | TARGET:. | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` passed with 16 tests. | coordinator |
| ART-004 | command | TARGET:. | `mvn -DskipTests package` passed across 10 reactor modules. | coordinator |
| ART-005 | command | TARGET:docs-site | `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `npm run build` passed. | coordinator |
| ART-006 | command | TARGET:. | `git diff --check` passed; pre-commit harness status warned only about intentional dirty working tree. | coordinator |

## 使用规则

- 路径必须可复查；临时终端输出应先保存为稳定文件再登记。
- 产物如果包含敏感信息，先脱敏或改为记录复查方式，不要提交原始敏感内容。
- 与 `review.md`、`progress.md`、walkthrough 互相引用时，使用 `ART-xxx` ID。
