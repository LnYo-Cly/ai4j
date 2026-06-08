# AI4J extension CLI inspect wave 2 - 进度

## 状态：审查中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

## 残余

- RG-004 完整命令 `mvn -pl ai4j-cli -am -DskipTests=false test` 仍被既有 R-008 阻塞：`ai4j-agent` 的 `HandoffPolicyTest.testAllowedToolsPolicyDeniesUnexpectedSubagent` 和 `testNestedHandoffBlockedByMaxDepth` 失败；本轮新增 CLI extension tests 已通过 targeted gate。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：synced-before-review
- Registry update needed：`cli-host` module plan updated with `CLI-EXT-01`
- Harness Ledger update needed：task-review 后由 lifecycle CLI / status projection 刷新
- 负责人：coordinator

### [2026-06-08 11:28] - task-start

- 做了什么：Start Wave 2 CLI extension inspection: add top-level extension list/inspect over classpath ServiceLoader discovery, without install/enable/runtime adapter.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-08 19:51] - implementation and focused review

- 做了什么：新增 `CliExtensionCommand`，接入 `Ai4jCli` top-level `extension/extensions` 路由；CLI `pom.xml` 增加 `ai4j-extension-api` 依赖；新增 CLI ServiceLoader fixture 和 `Ai4jCliTest` 覆盖 list/inspect/runtime/unknown-id/list-extra-arg。
- 验证结果：代码复核通过；默认 inspect 不调用 `apply()`，`--runtime` 使用临时 registry state；新增 `ExtensionInspectionSnapshot` 避免向 CLI 暴露 executor/handler。
- 下一步：同步回归台账、模块计划和 task review packet。
- 证据：diff:TARGET:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/command/CliExtensionCommand.java:extension list/inspect implementation; diff:TARGET:ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionInspectionSnapshot.java:read-only runtime inspection snapshot

### [2026-06-08 20:08] - targeted validation

- 做了什么：运行 diff hygiene、extension API tests、CLI targeted tests。
- 验证结果：`git diff --check` pass，只有 CRLF warnings；`mvn -pl ai4j-extension-api -DskipTests=false test` pass，8 tests；`mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` pass，8 tests。
- 下一步：运行 package smoke 和完整 RG-004 gate。
- 证据：command:TARGET:.:git diff --check pass with CRLF warnings only; command:TARGET:.:mvn -pl ai4j-extension-api -DskipTests=false test pass 8 tests; command:TARGET:.:mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test pass 8 tests

### [2026-06-08 20:12] - package and RG-004 evidence

- 做了什么：运行 `mvn -DskipTests package` 和完整 CLI gate `mvn -pl ai4j-cli -am -DskipTests=false test`。
- 验证结果：package smoke pass，10 reactor modules；完整 CLI gate 在 `ai4j-agent` 上游既有 `HandoffPolicyTest` 两个失败处停止，CLI 模块未执行到；失败与本轮新增 extension CLI 无直接关系，按 R-008 路由。
- 下一步：补齐 task materials、回归记录和 review packet。
- 证据：command:TARGET:.:mvn -DskipTests package pass across 10 reactor modules; command:TARGET:.:mvn -pl ai4j-cli -am -DskipTests=false test fails in existing ai4j-agent HandoffPolicyTest R-008 before cli module

### [2026-06-08 12:36] - task-review

- 做了什么：AI4J Extension CLI Wave 2 ready: extension list/inspect implemented, default inspect is manifest-only, runtime inspect uses read-only snapshot, targeted API/CLI tests and package smoke passed; full RG-004 remains blocked by existing R-008 upstream agent tests.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
