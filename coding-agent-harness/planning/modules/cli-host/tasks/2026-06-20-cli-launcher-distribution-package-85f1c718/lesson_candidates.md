# CLI launcher distribution package - Lesson Candidates

## Decision

checked-none: cli-distribution-bounded-slice

## Rationale

本任务发现的主要经验是 `.gitignore` 的通用 `dist/` 规则会误伤 `ai4j-cli/src/main/dist`。这属于本仓库本次发行包布局的局部实现细节，已经在 `findings.md` 和 `.gitignore` 精确反忽略中处理，不需要沉淀为跨项目 Harness lesson。

## Candidates

| ID | Candidate | Decision | Reason |
| --- | --- | --- | --- |
