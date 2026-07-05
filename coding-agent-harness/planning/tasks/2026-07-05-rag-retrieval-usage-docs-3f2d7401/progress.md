# rag retrieval usage docs - 进度

## 状态：已完成

## 进度记录

证据使用 `type:path:summary` 格式。

### [2026-07-05 11:02] - docs-update

- 做了什么：补充 Hybrid Retrieval 文档的默认 dense、BM25、Dense + BM25 hybrid 最短用法，并在 overview 加入口提示。
- 验证结果：文档 diff 已完成。
- 下一步：运行 docs-site gate。
- 证据：diff:TARGET:docs-site/docs/core-sdk/search-and-rag/hybrid-retrieval.md:added dense/BM25/hybrid usage snippets and query-planner cost note

### [2026-07-05 11:07] - docs-site-gate

- 做了什么：worktree 缺 ignored `node_modules`，先运行 `npm ci`，再运行 docs-site typecheck/build。
- 验证结果：`npm run typecheck` 与 `npm run build` 均通过。
- 下一步：更新 RG-008/SRB-064。
- 证据：command:TARGET:docs-site:npm run typecheck && npm run build -> PASS after npm ci restored ignored node_modules

### [2026-07-05 11:09] - regression-record

- 做了什么：更新 `Regression-SSoT.md` RG-008 与 `Cadence-Ledger.md` SRB-064。
- 验证结果：记录已写入。
- 下一步：运行 diff hygiene。
- 证据：diff:TARGET:docs/05-TEST-QA:RG-008 and SRB-064 updated

### [2026-07-05 11:10] - diff-hygiene

- 做了什么：运行提交前 diff hygiene。
- 验证结果：`git diff --check` 通过；仅 CRLF 工作区提示。
- 下一步：提交、PR、合并清理。
- 证据：command:TARGET:.:git diff --check -> PASS, CRLF warnings only

### [2026-07-05 11:12] - task-complete

- 做了什么：Completed RAG retrieval usage docs with docs-site validation
- 验证结果：已完成。
- 下一步：提交 lifecycle 修正并创建 PR。
- 证据：n/a

## 残余

- 无阻塞残余。docs-site `npm ci` 报既有 npm audit vulnerabilities，未在本任务处理。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced
- Registry update needed：不适用
- Harness Ledger update needed：已由 lifecycle CLI 同步
- 负责人：coordinator
