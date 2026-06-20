# AI4J CLI TUI extension projection - 进度

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

### [2026-06-11 15:28] - 实现 TUI extension 投影

- 做了什么：在 `SlashCommandController` 中补齐 `/extensions` 和 `/extension` 的补全规则，在 `CodingCliSessionRunner` 中接入 `/extensions`、`/extension ...` 的实际执行，并把 extension 入口写进帮助和命令面板。
- 验证结果：`SlashCommandControllerTest` 通过；随后跑通 `ai4j-cli` 带依赖回归。
- 下一步：补齐任务包收口与审查材料。
- 证据：diff:ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java;ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java;ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java:extension TUI projection

### [2026-06-11 15:30] - 定向回归

- 做了什么：运行 `mvn -pl ai4j-cli -am -Dtest=SlashCommandControllerTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test`，确认 slash 建议测试通过。
- 验证结果：44 个测试通过，0 failure，0 error。
- 下一步：跑完整 `ai4j-cli` reactor 回归。
- 证据：command:G:\My_Project\java\ai4j-sdk:reactor test for slash controller

### [2026-06-11 15:32] - 完整模块回归

- 做了什么：运行 `mvn -pl ai4j-cli -am -DskipTests=false test`。
- 验证结果：reactor 中 `ai4j-extension-api`、`ai4j`、`ai4j-agent`、`ai4j-coding`、`ai4j-cli` 全部 SUCCESS；`ai4j-cli` 自身 272 个测试通过。
- 下一步：提交 review 材料并等待人工确认。
- 证据：command:G:\My_Project\java\ai4j-sdk:full ai4j-cli reactor test

### [2026-06-11 16:03] - 原始资源输出保真修正

- 做了什么：把 `CapturingTerminalIO` 的输出改成原样透传，避免 extension resource 的首尾空白被 `trim()` 掉。
- 验证结果：再次运行 `mvn -pl ai4j-cli -am -DskipTests=false test`，reactor 仍然全绿。
- 下一步：等待人工确认。
- 证据：command:G:\My_Project\java\ai4j-sdk:full ai4j-cli reactor retest after raw-output fix

### [2026-06-14 14:59] - 参数解析边界修复与定向回归

- 做了什么：把 TUI `/extension ...` 的 shell-like 参数解析收紧为只转义反斜杠、引号和空白，避免 Windows 路径里的普通 `\` 被误删；新增 `CodingCliSessionRunnerArgumentParsingTest` 覆盖 Windows 路径和带空格/转义引号参数。
- 验证结果：`mvn -pl ai4j-cli -am "-Dtest=TuiSessionViewTest,JlineShellTerminalIOTest,CliThemeStylerTest,SlashCommandControllerTest,CodingCliSessionRunnerArgumentParsingTest" -DskipTests=false -DfailIfNoTests=false test` 通过；93 tests，0 failures，0 errors，0 skipped。
- 下一步：等待人工确认。
- 证据：command:G:\My_Project\java\ai4j-sdk:targeted CLI TUI and extension argument regression passed with 93 tests
- 证据：diff:ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunnerArgumentParsingTest.java:Windows path and quoted argument parsing regression

## 残余

- 人工 review 确认仍待 Dashboard 完成。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：`review.md`、`walkthrough.md`
- 负责人：coordinator
