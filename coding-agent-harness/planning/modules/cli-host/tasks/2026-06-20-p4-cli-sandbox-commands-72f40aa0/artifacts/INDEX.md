# 任务产物索引

仅在任务产生较多证据或大体量产物时使用，例如命令输出、截图、fixture、生成报告、review transcript、导出的数据文件等。核心任务文件只引用这里的 ID，不粘贴长输出。

| ID | Type | Path | Summary | Produced By |
| --- | --- | --- | --- | --- |
| ART-001 | command | TARGET:. | `git status --short --branch`; `git worktree list` confirmed root `main` clean and active worktree `feature/cli-sandbox-commands`. | coordinator |
| ART-002 | command | TARGET:. | `npx --yes coding-agent-harness status --json .` passed with 0 failures before P4 planning edits; review queue still contains tasks awaiting human confirmation. | coordinator |
| ART-003 | code-inspection | TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java | Confirmed slash dispatch and runtime rebind patterns for `/mcp`, `/stream`, `/provider`, `/model`. | coordinator |
| ART-004 | code-inspection | TARGET:ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox | Confirmed sandbox SPI does not yet expose attach/resume by session id. | coordinator |

## 使用规则

- 路径必须可复查；临时终端输出应先保存为稳定文件再登记。
- 产物如果包含敏感信息，先脱敏或改为记录复查方式，不要提交原始敏感内容。
- 与 `review.md`、`progress.md`、walkthrough 互相引用时，使用 `ART-xxx` ID。
