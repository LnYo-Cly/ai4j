# 任务产物索引

本任务的证据主要保存在 `progress.md` 与 `review.md` 中；大段终端输出没有另存为独立文件。下表登记可复查的稳定证据入口。

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | diff | TARGET:.gitignore | `output/` 被加入忽略规则，避免本地生成输出进入提交边界。 | coordinator |
| ART-002 | diff | TARGET:pom.xml; TARGET:ai4j/pom.xml; TARGET:ai4j-agent/pom.xml; TARGET:ai4j-coding/pom.xml; TARGET:ai4j-cli/pom.xml; TARGET:ai4j-bom/pom.xml; TARGET:ai4j-spring-boot-starter/pom.xml; TARGET:ai4j-flowgram-spring-boot-starter/pom.xml | release GPG executable 从本机绝对路径改为 `${gpg.executable}`，默认 `gpg`。 | coordinator |
| ART-003 | command | TARGET:progress.md | 记录 `rg` 路径复查、`mvn -DskipTests package` 和 harness status 的通过结果。 | coordinator |
| ART-004 | review | TARGET:review.md | Agent review submission `ARS-202606040835` 和无重要发现声明。 | coordinator |

## 使用规则

- 路径必须可复查；临时终端输出未单独提交时，以 `progress.md` 的命令摘要为准。
- 产物如果包含敏感信息，先脱敏或改为记录复查方式，不提交原始敏感内容。
- 与 `review.md`、`progress.md`、walkthrough 互相引用时，使用 `ART-xxx` ID。
