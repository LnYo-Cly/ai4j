# 2026-03-23 Codex 对齐 JLine CLI Rollout

## 目标

- 将 `ai4j-cli --ui tui` 的正式主路径从 `旧实验 TUI` 迁移到 `JLine 3`
- 以 `Windows Terminal + PowerShell` 为 P0 参考环境，对齐 Codex 类 shell 体验
- 每个小点都记录：做了什么、效果、是否达标、残留缺陷

## 依赖文档

- `docs/plans/2026-03-23-codex-aligned-jline-cli-design.md`
- `docs/plans/2026-03-23-codex-aligned-jline-cli-implementation-plan.md`

## 本轮任务

- [x] 冻结 JLine 版设计与实施计划
- [x] 完成 Phase 1：JLine 主壳骨架与主路径分流
- [x] 完成编译、测试、打包自验证
- [ ] 完成 Phase 2：composer + transcript + status 主路径
- [ ] 完成 Phase 3：Codex 风格 block formatter
- [ ] 完成 Phase 4：slash palette
- [ ] 完成 Phase 5：tool / patch / approval
- [ ] 完成 Phase 6：session / compact / process
- [ ] 完成 Phase 7：体验压平与主路径切换

## 小点沉淀

### 小点 1：JLine 主壳骨架与主路径分流

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellContext.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineCodeCommandRunner.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommand.java`
  - 修改 `ai4j-cli/pom.xml`，为 `ai4j-cli` 显式引入 `org.jline:jline:3.30.0`
- 效果：
  - 当用户在系统交互终端中运行 `--ui tui` 且未提供 one-shot `--prompt` 时，默认 backend 已从 `旧实验 TUI` 切到新的 `JLine` 路径
  - 新路径当前先以 shell-first 适配层承接：`LineReader.readLine(...)` 负责输入，`printAbove(...)` 负责 transcript 输出
  - `旧实验 UI` 仍保留为兼容 fallback，不再是默认主路径
  - `legacy` 追加式会话 runner 仍保留，可通过 backend 开关回退
- 是否达标：
  - Phase 1 达标
- 仍有缺陷：
  - 当前 `JlineCodeCommandRunner` 还是通过适配层复用 `CodingCliSessionRunner`，还不是完整的独立 shell-first 编排器
  - `status / spinner / slash palette / tool block / patch block` 还没有切到新的 JLine 专用控制器
  - 真实 `Windows Terminal + PowerShell` 的人工体感终验还未执行，这一轮主要完成的是主壳接入和可编译主路径

### 小点 2：JLine 底部状态区 / spinner / main-buffer transcript 接入

- 做了什么：
  - 在 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java` 接入底部状态区刷新、spinner tick 和 session/model/workspace 上下文
  - 在 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java` 里把 `Thinking / Responding / Working / Idle` 状态桥接到 `JlineShellTerminalIO`
  - `MODEL_RESPONSE` 已不再继续往 transcript 里刷“Responding”噪音，而是只更新底部状态
  - `Status.update(...)` 后补了 `terminal.flush()`，降低 spinner 看起来“不动”的概率
- 效果：
  - main-buffer transcript 不再因为状态切换而出现多条 `Thinking / Responding / Working` 噪音输出
  - 底部状态区现在会承接 turn 生命周期、tool 运行状态和 session 元信息
  - 交互式 JLine 主路径的 reasoning 不再默认污染 transcript，状态留在底部，文本留给真正需要看的块
- 是否达标：
  - Phase 2 已达到“主路径可用并明显更接近 Codex”的阶段性目标
- 仍有缺陷：
  - 还没有做真实 `Windows Terminal + PowerShell` 人工终验，因此 spinner 的最终体感仍需要实机确认
  - 当前仍复用 `CodingCliSessionRunner` 作为编排层，UI 线程/事件桥接还不是独立控制器
  - slash palette、approval、process 等高级路径还没切到新的 JLine 原生输入链

### 小点 3：Codex 风格基础 block formatter 与空白纪律

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatter.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/TranscriptPrinter.java`
  - main-buffer 的 assistant/tool/error/compact 块改为统一 formatter 输出
  - `TranscriptPrinter` 开始负责块间只留一个空行，并避免每次 block 输出后都追加尾部空白
  - `buildMainBufferRunningStatus(...)` 改为返回纯 detail 文本，去掉底部状态区里的双 `•` 前缀
- 效果：
  - main-buffer transcript 的块级风格开始稳定：`tool / error / compact` 都变成统一的 Codex 风格块
  - 原先容易出现的大段空白换行被明显收敛，块间空白从“尾部硬回车”改成“块前单分隔”
  - auto compact 现在走独立 block，不再使用旧的 `note>` 文案
- 是否达标：
  - Phase 3 已完成第一轮基础 formatter 落地
- 仍有缺陷：
  - `session / status / replay / process / checkpoint` 等命令输出还没有全部收口到同一套 block formatter
  - `apply_patch` 目前只有基础摘要风格，离 Codex 的完整 patch 展示还差 approval/summary 细节
  - 还未做 transcript 宽度、代码块、长 markdown 输出的进一步人工压平

### 小点 4：JLine slash command controller 与候选补全

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 在 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellContext.java` 构建 `LineReader` 时接入 completer、parser 和命令相关选项
  - 在 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommand.java` 与 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineCodeCommandRunner.java` 中把 slash controller 接到 JLine 主路径，并在 runner 启动时注入 `sessionManager`
  - 新增 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
- 效果：
  - JLine 主路径现在有独立的 slash completer，内置命令、`/cmd <name>` 模板、`/theme <name>` 主题，以及部分 session 命令候选开始可用
  - `/` 被单独绑定成 slash menu widget，空 composer 下会插入单个 `/` 并尝试触发命令候选，降低重复 `/` 的概率
  - `Ctrl+P` 在 JLine 主路径下开始绑定到同一套命令候选入口
- 是否达标：
  - Phase 4 已完成第一轮命令候选接入
- 仍有缺陷：
  - 还没有做真实 `Windows Terminal + PowerShell` 的人工终验，因此 `/` 是否达到 Codex 级菜单体感仍需实机确认
  - 当前是基于 JLine completer/widget 的第一轮实现，不是完全自定义的 palette renderer；描述样式和选中反馈还没完全压到 Codex 观感
  - `/process` 目前只补全一级子命令，还没有补全过程 id、日志 limit 等更深层参数

### 小点 5：approval inline block 收口

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliToolApprovalDecorator.java`
  - 将原来的 `[approval] tool=...` + `approve [y/N]>` 改为块式 approval 文案和 inline `• Approve? [y/N] ` 提示
  - 为 `bash` 和 `apply_patch` 分别增加更贴近操作语义的摘要行，而不是直接把整段参数 JSON 或 patch 原文打到终端
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java` 中 safe approval 相关断言
- 效果：
  - approval 提示开始更接近 Codex/Claude Code 的“先给动作摘要，再做 inline 确认”节奏
  - `bash` approval 会显示 action/cwd/command 的精简信息；`apply_patch` approval 会显示 Add/Update/Delete 的文件摘要
  - prompt 本身不再是旧式 shell 标记，而是进入统一 bullet 语气
- 是否达标：
  - Phase 5 的 approval inline 已完成第一轮收口
- 仍有缺陷：
  - approval 本身仍然是 `readLine(...)` 驱动，不是完整独立的 JLine approval controller
  - 还没有把 approval 的同意/拒绝结果打印成独立 transcript block
  - 还缺一次真实交互终验，确认 JLine 状态区与 approval prompt 之间不会出现闪烁或重绘抖动

### 小点 6：patch 摘要复用与 main-buffer 命令块收口

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/PatchSummaryFormatter.java`
  - `apply_patch` 的 approval 摘要、pending preview、completed preview 开始复用同一套 patch summary 逻辑
  - `apply_patch` 在拿到 `fileChanges` 时优先显示 `Created/Edited/Deleted path (+x -y)` 风格，而不是只列文件名
  - `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatter.java` 新增通用 info/output block formatter
  - `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java` 的 main-buffer `emitOutput / emitError` 开始统一走 block formatter，`status / session / checkpoint / sessions / history / tree / events / replay / processes / process status / commands / themes` 等命令输出开始收口
  - 新增测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/PatchSummaryFormatterTest.java`
    - 扩展 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatterTest.java`
- 效果：
  - `apply_patch` 在 JLine transcript 里的 pending/result 反馈更接近 Codex 的 edit/patch 节奏
  - main-buffer 下的 slash 命令输出不再继续裸打 `status:`、`processes:`、`checkpoint:` 这类旧文本，而是进入统一块级输出纪律
  - main-buffer 下的命令错误也改为统一的 error block，而不是原始 `terminal.errorln(...)`
- 是否达标：
  - Phase 5 / Phase 6 又推进了一轮，但还没有完成真实终端人工终验
- 仍有缺陷：
  - `replay` 目前仍然是“旧事件内容 + 新 block 外壳”，还没有把 event 级内容完全重写成更强的一致性视觉
  - approval 的同意/拒绝结果块还没补
  - 仍缺一次真实 `Windows Terminal + PowerShell` 终验来确认 slash、spinner、approval 与 main-buffer 重绘是否完全稳定

### 小点 7：approval result block 与 replay 旧前缀清理

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliToolApprovalDecorator.java`
  - approval 在用户输入 `y / n` 后，会额外打印独立的 `Approved / Rejected` 结果块
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - replay 构建不再继续输出 `note>`、`error>` 这类旧前缀，错误事件、compact 事件、session resumed/forked 事件开始改走 Codex block formatter
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java` 中 approval 相关断言
- 效果：
  - approval 不再只有“请求批准”而没有“批准结果”，终端节奏更接近 Codex/Claude Code
  - replay 的事件内容开始摆脱早期日志式前缀，向统一块输出靠拢
- 是否达标：
  - 这一小点达标
- 仍有缺陷：
  - replay 仍然是按 turn 聚合的文本重放，不是完整独立的 replay renderer
  - `Rejected` 后当前仍会继续走错误链路，真实体感还需要实机看是否存在轻微冗余
  - 真实 `Windows Terminal + PowerShell` 下的 spinner / slash / approval 终验仍未完成

### 小点 8：`/process` 深层参数补全

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 为 `/process` 增加分层补全：`action -> process-id -> optional limit`
  - `/process status|follow|logs|write|stop` 的 action 候选改为带尾随空格，减少选择后还要手动补空格的摩擦
  - `SlashCommandController` 新增当前活动 session 进程候选注入能力，支持基于真实进程状态、运行模式和命令摘要做 process-id 候选描述
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`，把当前活动 session 的进程快照桥接给 slash completer
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineCodeCommandRunner.java`，让 JLine 主路径把该补全能力接上
  - 扩展 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
- 效果：
  - `/process status `、`/process follow `、`/process logs `、`/process write `、`/process stop ` 现在会补全当前活动 session 的真实 process-id
  - `/process follow <id> ` 与 `/process logs <id> ` 现在会继续提示常用 limit 候选，不再只停在一级 action
  - `/process write <id> ` 在进入自由文本区后停止补全，避免 palette 继续干扰输入
  - process-id 候选会附带 `status | mode | command` 摘要，便于在多个进程之间快速辨认
- 是否达标：
  - 这一小点达标
- 仍有缺陷：
  - 真实 `Windows Terminal + PowerShell` 下还没做人工终验，因此 `/process` 菜单的最终手感仍需实机确认
  - `/process` 目前只补到 `id` 与 `follow/logs` 的 limit，`write` 的正文区不会提供更智能的占位提示
  - 仍然基于 JLine completer/menu，而不是完全自定义的 Codex 式 palette renderer

### 小点 9：slash 重复插入防抖

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 把 `/` 键触发逻辑拆成可测试的 `INSERT_AND_MENU / MENU_ONLY / INSERT_ONLY` 三种动作
  - 当当前输入已经处于 slash 命令头部且还没进入参数区时，再按 `/` 不再继续插入第二个 `/`，而是只打开候选菜单
  - 扩展 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`，补上针对 slash 重复插入风险的纯逻辑断言
- 效果：
  - 空输入下按 `/` 仍会插入一个 `/` 并打开菜单
  - 已有 `/process`、`/theme` 这类 slash 命令头时，再按 `/` 会变成“仅开菜单”，降低出现 `//` 的概率
  - 进入参数区后，例如 `/process write <id> `，再按 `/` 仍会正常插入字符，不会误吞用户输入
- 是否达标：
  - 这一小点达标
- 仍有缺陷：
  - 这次是纯逻辑层验证，不是真实 `Windows Terminal + PowerShell` 终验；最终体感仍需实机确认
  - 是否完全消除 JLine 菜单层面的重复显示，还要结合真实终端交互再看一轮

### 小点 10：JLine 主路径颜色链路接入

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliAnsi.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 新增 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
  - 在 JLine 主路径下，把底部状态区从纯文本 `AttributedString` 改成基于 theme 的 ANSI -> `AttributedString.fromAnsi(...)` 渲染
  - `printAbove(...)` 的 transcript 输出开始按块类型自动着色：主 bullet、detail、error、approved/rejected、running 状态等会映射到 `brand/accent/success/warning/danger/muted`
  - `/theme <name>` 切换后，JLine 主路径会同步刷新新的颜色主题，不再只影响旧 TUI renderer
- 效果：
  - `Thinking / Working / Responding / Idle` 底部状态条现在有明确颜色层，不再是纯黑白文本
  - main-buffer transcript 的 `• Error`、`• Approved`、`• Rejected`、`• Running ...`、`• Applied patch` 等块在 ANSI 终端下开始有颜色区分
  - theme 不再只是配置项或旧 TUI 路径能力，JLine 主路径已经开始真正消费 `TuiTheme`
- 是否达标：
  - 这一小点达标
- 仍有缺陷：
  - 当前 transcript 颜色仍是“按行语义着色”的第一轮实现，不是更细粒度的 token 级高亮
  - 还没做真实 `Windows Terminal + PowerShell` 人工终验，因此颜色观感、亮度和对比度还需要实机再校一轮
  - assistant 正文和 markdown/code fence 目前主要还是文本色，没有做更强的代码块专门配色

### 小点 11：空回车与 `Ctrl+C` 输入语义修正

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 为 JLine 主路径增加自定义 Enter widget：当输入框为空或只有空白字符时，不再 `accept-line`，而是只重绘当前 prompt
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - `UserInterruptException` 现在不再被当成空串输入继续下一轮，而是标记 `inputClosed` 并返回 `null`，让主交互循环按退出处理
  - 扩展 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
- 效果：
  - 空输入时按回车，不再不断“接收空行 -> 进入下一轮 -> 再画一个新 prompt”
  - `Ctrl+C` 不再等价于提交空输入，而是会触发交互退出路径
  - 这两个行为都被收敛到 JLine 输入层解决，不再依赖外层会话循环兜底
- 是否达标：
  - 这一小点达标
- 仍有缺陷：
  - 这轮主要是逻辑修正与定向测试，还没有做真实 `Windows Terminal + PowerShell` 实机终验
  - `Ctrl+C` 在 approval 等二级输入场景下的退出体验还需要后续再验一轮

### 小点 12：正文内 `Thinking:` 渲染与顺序输出

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - interactive main-buffer 模式下不再压制 reasoning block，`Thinking:` 现在会进入正文 transcript
  - 在 `MODEL_REASONING -> MODEL_RESPONSE`、`MODEL_RESPONSE -> MODEL_REASONING` 切换时，增加增量 flush，避免把 reasoning/text 混成一个统一尾刷 block
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`，将 `Thinking:` 首行按 muted 灰色处理
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
- 效果：
  - 当前主路径的正文渲染顺序开始更接近 Codex / Claude Code：模型先返回 reasoning，就先打印 `Thinking: ...`；随后如果发生 tool 调用或正常 assistant 输出，会按后续事件顺序继续输出
  - `Thinking:` 不再只是底部状态栏瞬时提示，而是成为正文的一部分
  - `Thinking:` 在 ANSI 终端下会以灰色样式出现，和正文输出有视觉区分
- 是否达标：
  - 这一小点达标
- 仍有缺陷：
  - 现在是“事件边界顺序正确”的第一轮实现，还没有做到 token 级逐字 reasoning streaming
  - 真实 `Windows Terminal + PowerShell` 下的最终观感仍需要实机确认，尤其是 reasoning block 与底部状态栏同时存在时的视觉密度
  - 当前底部状态栏仍占 3 行，这会继续放大“空白感”，后续仍建议再压缩一轮

### 小点 13：JLine 主路径正文增量流式输出与空白压缩

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - main-buffer transcript 新增 live assistant / reasoning 流式打印路径；`MODEL_RESPONSE` 和 `MODEL_REASONING` delta 到来时，会直接按顺序写入正文，而不是只等事件边界或最终 flush
  - 为 live transcript 增加独立的块起止控制，保证 `Thinking -> tool -> assistant` 仍按块分隔，但同一段正文不会再被 final flush 重复打印
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - JLine 主路径输出在非 `readLine(...)` 阶段改为直接写终端并重绘 status，不再统一走 `printAbove(...)`
  - 底部状态栏由 3 行压缩为 1 行，保留 `status + model + workspace` 的核心上下文；idle 态时再追加 hint
  - 删除 main-buffer 路径在提交后额外插入的空白行
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/TranscriptPrinterTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
- 效果：
  - 用户在 JLine 主路径下输入普通问题时，不再先空出一大片区域再一次性看到整段回答
  - 正文现在会跟随流式 delta 增量落盘；如果 provider 按 chunk 推送，终端会按 chunk 连续输出
  - `printAbove(...) + 3 行 status + 提交后空白行` 叠加出来的“大片空白感”被显著压缩
  - final output 不会把已经流式打印过的正文再重复刷一遍
- 是否达标：
  - 这一小点在本地定向测试范围内达标
- 仍有缺陷：
  - 这一轮是“正文增量 streaming + 空白压缩”，不是完整的 token 级 cursor-in-place renderer；最终观感仍需真实 `Windows Terminal + PowerShell` 再看一轮
  - status 区目前已压到 1 行，但 session id 等次级上下文被折叠了；后续如果需要可以做成可切换的 compact / verbose status 模式
  - 还没有对真实智谱流做整轮人工实机回归，这一步需要用用户实际启动命令再验

### 小点 14：`/stream` 开关与代码块格式渲染

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 新增 `/stream` 内建命令补全，以及 `on / off` 参数候选
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 新增当前 CLI 会话级 `streamEnabled` 开关；支持 `/stream` 查看状态、`/stream on` 开启、`/stream off` 关闭
  - stream off 时，assistant / reasoning 会回退为完成块渲染；stream on 时继续正文增量输出
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatter.java`
  - assistant 正文新增 fenced code block 渲染：将 ```` ```lang ... ``` ```` 渲染为独立代码块边框与内容行
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`
  - 为代码块边框、代码内容行、inline code 增加独立样式规则
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommand.java`，把 `/stream [on|off]` 补进帮助文本
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatterTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
- 效果：
  - 用户现在可以直接用 `/stream` 控制当前会话是否启用正文增量流式输出
  - assistant 返回 markdown fenced code block 时，main-buffer transcript 不再把代码当普通正文，而是有独立块结构
  - stream on 和 stream off 两条路径都会保留代码块结构，不会只在非流式时才有格式
- 是否达标：
  - 这一小点在本地回归范围内达标
- 仍有缺陷：
  - 当前代码块是“结构化终端渲染”，还不是语言级语法高亮
  - inline code 目前只在整行 transcript 样式里做轻量高亮，流式 chunk 跨分片时不会做复杂重组
  - 还需要用户在真实智谱流下再看一轮观感，尤其是长代码块和混合 markdown 的实际体感

### 小点 15：代码块语法高亮

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`
  - 在现有代码块框线渲染基础上，新增 transcript 级 `TranscriptStyleState`，让代码块开头、代码行、代码块结尾之间共享语言上下文
  - 为常见语言补充轻量语法高亮：
    - Java / Kotlin / JavaScript / TypeScript / C#：关键字、字面量、字符串、注解、注释
    - Bash / PowerShell 风格：关键字、变量、字符串、注释
    - Python：关键字、字符串、注释
    - JSON：key、number、boolean / null
    - XML / HTML：tag、属性值、注释
    - YAML：轻量 key/value 结构着色
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - JLine 主路径现在为 transcript 持有会话级代码块样式状态，并在 `beginTurn / clearTransient / finishTurn` 时重置，避免跨轮串色
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
- 效果：
  - assistant 返回带语言标记的 fenced code block 时，不再只是“边框有颜色、正文纯白”，而是代码正文也会按语言类型做轻量 token 高亮
  - 这套高亮同时覆盖 stream on 和 stream off；因为状态保存在 transcript 渲染层，不依赖某一种输出路径
  - 代码块语言上下文会在 turn 生命周期内正确收口，不会把上一段代码块的语言污染到下一段正文
- 是否达标：
  - 这一小点在本地测试和打包验证范围内达标
- 仍有缺陷：
  - 当前是内建轻量高亮器，不是完整 parser；复杂多行注释、嵌套模板字符串、少数边缘语法仍可能只有基础着色
  - 代码块如果没有语言标记，目前仍以结构化块渲染为主，不做激进的自动猜测
  - 最终颜色观感仍需要在真实 `Windows Terminal + PowerShell` 与用户实际主题下再做一轮人工终验

### 小点 16：无边框代码块与 GitHub 风格代码主题

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatter.java`
  - assistant transcript 中的 fenced code block 不再输出左侧框线和收尾边框，改为轻量语言标签行加代码正文
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - stream on 路径和 stream off 路径统一改为无边框代码块，不再在代码块结束时额外打印 close 边框
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`
  - 代码块正文改为整行代码背景 + 语法 token 前景色，不再是“正文颜色 + 左侧 gutter 装饰线”
  - 修改 `ai4j-tui/src/main/java/io/github/lnyocly/ai4j/tui/TuiConfigManager.java`
  - 默认代码色板改成 GitHub Dark 风格；新增内置主题 `github-dark` 和 `github-light`
  - 新增资源：
    - `ai4j-tui/src/main/resources/io/github/lnyocly/ai4j/tui/themes/github-dark.json`
    - `ai4j-tui/src/main/resources/io/github/lnyocly/ai4j/tui/themes/github-light.json`
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatterTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
    - `ai4j-tui/src/test/java/io/github/lnyocly/ai4j/tui/TuiConfigManagerTest.java`
- 效果：
  - 代码块左侧不再出现类似截图中的竖线 / 角框装饰，主观观感更接近真正的代码区域，而不是被包在一个字符框里
  - ANSI 终端下，代码块会有独立代码背景色和 GitHub 风格 token 着色；代码阅读感知明显强于之前的“普通正文 + 彩色 token + gutter”
  - `/theme github-dark` 和 `/theme github-light` 现在都可直接使用；即使不是 GitHub 主题，缺省代码色板也会落到 GitHub Dark 风格
- 是否达标：
  - 这一小点在本地测试和打包验证范围内达标
- 仍有缺陷：
  - 代码块标题目前是轻量语言标签，不是完整 IDE 式 title bar
  - 当前代码背景依赖 ANSI 真彩色支持；在部分旧终端里可能会退化成仅前景色高亮
  - 真实智谱长代码块在 `Windows Terminal + PowerShell` 下的最终观感还需要你实机再看一轮

### 小点 17：移除可见语言标签，代码块只保留正文

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatter.java`
  - fenced code block 不再输出可见的 `[java]` / `[code]` 头部行
  - 改为给每一行代码注入不可见的语言标记，终端视觉上只看到代码正文
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - stream on 路径不再打印代码块 header 行，流式和非流式路径统一为“只输出代码正文”
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`
  - transcript 渲染改为识别不可见代码标记并做语法高亮，不再依赖可见 header 行传语言
  - 顺手修复了一个被早退逻辑掩盖的旧问题：`ansi=false` 时 bullet transcript 仍错误带 ANSI
  - 扩展 / 调整测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatterTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
- 效果：
  - 代码块区域不再出现 `[java]`、`[code]`，也不会再出现 `[javapublic class ...]` 这种拼接错误
  - 视觉上只剩代码正文；JLine 路径仍保留代码背景和语法着色
  - stream on / off 两条路径的代码块外观统一
- 是否达标：
  - 在当前本地回归范围内达标
- 仍有缺陷：
  - 为了让语言信息在无可见 header 的情况下继续传递，当前内部实现使用了不可见标记字符；视觉层面无问题，但最终仍建议你在真实终端里再做一轮复制/粘贴体验验证
  - `/stream off` 目前仍主要是 transcript 渲染开关，尚未把底层模型请求的 `stream` 参数完全绑定到同一开关

### 小点 18：撤销隐藏字符方案，切回状态化 markdown 渲染

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatter.java`
  - 撤掉代码行中的不可见语言标记，代码块重新回到纯文本输出；不再向正文注入零宽字符
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - `MainBufferTurnPrinter` 新增基于 turn 状态的 assistant markdown 打印逻辑；stream on / off 都通过状态维护 fenced code block，而不是改写文本内容
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 新增 transcript code block 进入 / 退出接口，让 JLine 主路径在不污染正文的前提下保留代码块背景和高亮
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`
  - transcript 现在支持常见 markdown 样式：
    - heading：整行强调
    - blockquote：弱化色显示
    - inline code：代码底色
    - `**bold**` / `__bold__`：粗体
    - markdown link：去掉方括号/圆括号包装，只保留可读文本
  - 同时保留代码块行的代码背景和 token 着色；没有语言信息时退化到通用代码高亮，而不是吞掉正文
  - 扩展 / 调整测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliThemeStylerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodexStyleBlockFormatterTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
- 效果：
  - 修复了真实终端里代码块正文丢失、只剩空白或缩进的问题
  - 修复了输出链路里潜在的零宽字符污染；复制/粘贴和后续再渲染更稳
  - assistant 正文的常见 markdown 现在会按主路径顺序渲染，不再只有代码块处理
- 是否达标：
  - 在当前本地定向回归和打包范围内达标
- 仍有缺陷：
  - 当前 markdown 支持仍以常见 coding-agent 输出为主，不是完整 CommonMark 渲染器；表格、嵌套列表、复杂链接样式仍未专项处理
  - `/stream off` 和底层模型请求 `stream=false` 的语义还没有完全绑定，这一块仍是下一步

### 小点 19：修复 JLine 主路径中文 / 全角字符临界换行

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliDisplayWidth.java`
  - 统一提供基于 `WCWidth` 的显示宽度计算、按列宽裁剪、ANSI 文本按终端列宽分段输出
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - JLine 非读入态输出不再直接把 styled fragment 原样 `print` 到终端，而是按当前光标列位和终端宽度做显示宽度换行，避免中文/全角字符把最后一个标点或引号挤到下一行
  - 同时把状态栏相关的 `clip(...)` 从按 `String.length()` 改成按显示列宽裁剪
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - runner 内所有摘要 / 状态裁剪逻辑切到同一套显示宽度实现，避免 `Thinking about:`、session 摘要等在中英混排时再次越界
  - 新增测试 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliDisplayWidthTest.java`
  - 覆盖中文裁剪、已有列偏移下的全角文本换行、ANSI 片段换行三个关键回归点
- 效果：
  - 修复了 `Thinking: 10.然后说："你好"` 这类场景里，末尾引号或标点被单独挤到下一行的问题
  - 修复了 JLine 主路径依赖终端软换行时，中文临界宽度下更容易出现的错位 / 多余空白风险
  - 状态栏、hint、model、workspace 等紧凑信息在中文环境下的截断结果更稳定
- 是否达标：
  - 在当前本地定向回归、打包和显示宽度专项测试范围内达标
- 仍有缺陷：
  - 这次修复的是 JLine 主路径的显示列宽问题；如果后续再接入新的渲染层或新的富文本组件，仍要继续遵守“按显示列宽而不是按字符数”的约束
  - 没有做真实录屏级别的人工终验，因此仍建议你在 `Windows Terminal + PowerShell` 下再跑一轮中文长句和中英混排实测

### 小点 20：修复流式 assistant markdown / 代码块乱序与重复刷出

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - assistant 流式正文不再对“未完成的一行”做激进逐字符打印，而是先按行缓冲，再把完整行交给 transcript markdown 渲染链处理
  - 这样可以避免 provider 把 code fence 拆成 `` + `python` 这类碎片时，前两个反引号被提前打印到正文里
  - 同时调整 final output 对账逻辑：如果 provider 在流式阶段输出了一版文本，结束时又给了一版“重写后的最终正文”，不再把整段最终正文重新刷一遍到 transcript，避免出现“前半段先打印一版、后半段再整段重复一版”
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 新增按完整 transcript 行输出的入口，让流式 assistant 的完整行也能走现有 markdown / code block 样式链路
  - 修改 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
  - 新增两类回归：
    - code fence 被拆成 ` `` ` 与后续反引号分块到达时，正文不应泄漏原始反引号
    - provider 最终输出与流式增量不完全一致时，不应把整段 assistant 正文重复打印两遍
- 效果：
  - 修复了你给出的 ` `` `、残缺代码块、正文重复刷出、markdown 行被拆坏的问题
  - 流式 assistant 现在优先保证 markdown / code block 正确性，而不是错误地逐字符抢先打印
- 是否达标：
  - 在当前本地定向回归范围内达标
- 仍有缺陷：
  - 现在 assistant 流式正文更偏“按完整行刷新”，不是 token 级 markdown 富文本流；这是为了先保证正确性
  - 如果后续要继续逼近 Codex/Claude Code 的细粒度体验，还可以再做“行内 markdown 增量状态机”，但那是下一轮优化，不是当前 bugfix 范畴

### 小点 21：补齐“流式不完整 + final output 才带代码块”场景

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 记录 final output 到达前的原始 streamed assistant 文本
  - turn 结束时，如果流式正文与 final output 在“去 markdown 包装后的语义内容”上不一致，会按行对齐计算缺失片段，并把缺失的 final 片段补回 transcript
  - 补回逻辑会识别 fenced code block 边界；如果缺失片段落在代码块中间，会自动补上必要的开/闭 fence，保证代码行还能走现有代码块渲染链
  - 扩展测试 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
  - 新增“流式只给了说明文字和空白，final output 才给出 `python hello_world.py` 代码块”的回归用例
- 效果：
  - 修复了你这次贴出来的这种现象：正文先出来了，但中间命令代码块是空白
  - 同时保留前一轮对“不要整段重复刷出 final output”的约束
- 是否达标：
  - 在当前本地定向回归范围内达标
- 仍有缺陷：
  - 这是一套面向 coding-agent 常见输出的补齐策略，不是完整的 markdown diff 引擎；极端复杂的重写型 final output 仍可能需要下一轮细化

### 小点 22：修复 JLine 主壳“最终答案在中间插入代码块”导致的重复标题与大片空白

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/AssistantTranscriptRenderer.java`
  - 把 assistant markdown 统一解析成“主缓冲区真正打印出来的 transcript 行”，包括：
    - 普通正文行
    - fenced code block 转成的缩进代码行
    - 保留内部空行，但去掉首尾空白边界
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - final output 对账不再只做“补齐缺失尾巴”的 append-only 逻辑，而是先判断 streamed transcript 是否仍然是 final transcript 的前缀
  - 如果 final output 在中间插入了新代码块、改写了已经打印过的段落顺序，就把这类 turn 标记为“需要整块重写”，不再继续盲目追加，避免出现你截图里的：
    - `你可以运行它：` 重复出现
    - 代码块被塞到错误位置
    - 由错误补写顺序带来的大片空白
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 在 JLine 正在 readLine 的主壳路径下，新增 assistant block rewrite：会按当前终端列宽计算旧 block 占用的显示行数，使用 ANSI 删除整块旧输出后，再把 final assistant block 一次性重绘到正确位置
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliThemeStyler.java`
  - 增加单行代码 transcript 的显式着色入口，保证 rewrite 后的代码块仍然走现有代码高亮，不会退回普通文本
  - 修改 `ai4j-cli/pom.xml`
  - 补上 `lombok` 依赖，恢复 `ai4j-cli` 模块的完整可编译状态；否则本轮修复无法重新打包
  - 新增测试 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/AssistantTranscriptRendererTest.java`
- 效果：
  - 修复了你这次截图里的核心问题：provider 流式先给出后半段，final output 再把中间代码块插回来时，JLine 主壳现在会重写整个 assistant block，而不是继续错位补写
  - 代码块、标题行、空行顺序会回到 final output 的真实顺序，不再出现“标题重复 + 大片空白 + 代码块落错位置”的组合问题
  - rewrite 后仍保留现有 markdown / 代码块渲染风格，不会为了纠错而退化成纯文本
- 是否达标：
  - 在当前本地定向测试与重新打包范围内达标
- 仍有缺陷：
  - 这次 rewrite 能修复 JLine 主壳里“最终答案改写已打印正文”的主要场景，但仍依赖终端支持常见 ANSI 删除行能力；后续仍建议在你实际使用的 `Windows Terminal + PowerShell` 环境做一轮人工终验
  - 当前 `/stream off` 和底层 provider `stream=false` 的语义绑定仍是独立问题，不在本小点里

### 小点 23：修复 Windows Terminal 下 JLine rewrite 使用 Delete Line 导致旧空白块残留

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - assistant block rewrite 不再使用 `CSI n M`（Delete Line）批量删行
  - 改成更保守的“逐行上移 + `CSI 2K` 清空整行”策略，再从首行位置重绘 final assistant block
- 效果：
  - 针对你截图里的实际现象：旧的空白代码块区域没有被删掉，只是在下面又追加了一份正确代码块
  - 新策略更适配 `JLine printAbove + Windows Terminal + PowerShell` 组合，避免 delete-line 在这个链路里失效
- 是否达标：
  - 在当前定向测试、重新打包范围内达标
- 仍有缺陷：
  - 这类问题强依赖真实终端行为，最终仍要以你本机实测为准

### 小点 24：修复 `printAbove()` 路径下 assistant block rewrite 与输入行/状态栏抢占导致的空白代码块

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - assistant block rewrite 不再把“光标上移 + 擦行 + 重绘”的 ANSI 串重新塞回 `lineReader.printAbove(...)`
  - 改成在 rewrite 场景下直接写入底层 `Terminal`，先清空当前输入行，再逐行回退清理旧 assistant block，最后一次性打印 replacement block
  - rewrite 完成后显式触发 `LineReader.REDRAW_LINE` 与 `LineReader.REDISPLAY`，把输入框重新画回去
  - 同时把 `redrawStatus()` 纳入 `outputLock`，避免 spinner/status 在 rewrite 过程中并发刷新终端，打断块级重绘
- 效果：
  - 修复了你截图里的核心残留形态：上方留下大块空白代码区，下方又追加一份正确代码块
  - rewrite 和 prompt/status 的职责重新分离：assistant block 由底层终端直写，输入行由 JLine 负责重绘，状态栏不再并发抢占同一段终端输出
- 是否达标：
  - 在本地编译与定向测试范围内达标
- 仍有缺陷：
  - 这是针对 `Windows Terminal + PowerShell + JLine 主壳` 的保守修复，最终表现仍应以你本机 jar 实测为准
  - 这次修的是“重绘链路冲突”，不是 markdown 语义层；如果后续还有个别 provider 返回顺序异常，需要继续看 agent 事件序列

### 小点 25：修复 assistant block rewrite 因旧块高度估算不准导致的“双份输出”

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 不再在 rewrite 时根据 `previousMarkdown` 重新估算旧 assistant block 的显示高度
  - 改为在 JLine 实际输出层为当前 assistant block 开启 tracking，按真实输出路径累计“已经打印出来的终端显示行数”
  - assistant 行输出现在在写入时直接累计真实 rows；rewrite 成功后再把 tracking rows 更新为 replacement block 的实际 rows
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - assistant block 开始流式输出或整块输出时，显式启动 assistant block tracking
  - `printAssistantBlock(...)` 在 JLine 主壳下改为统一走 `shellTerminal.printTranscriptLine(...)`，让非流式和流式路径都走同一套实际行数统计
  - `replaceAssistantBlock(...)` 改为把“当前 assistant block 已实际打印的 rows”传给 JLine 重写逻辑，而不是再传 markdown 让底层二次猜测
- 效果：
  - 针对你最新截图里的现象：上面残留一份旧 assistant block，下面又追加一份新的完整 block
  - 现在 rewrite 的回退高度不再依赖 markdown 结构推断，而是依赖实际已经输出到终端的 rows，修正了“旧块没完全擦掉，最终块又整份重打一次”的根因
- 是否达标：
  - 在本地定向测试与重新打包范围内达标
- 仍有缺陷：
  - 这次修复聚焦在“assistant 最终重写”链路；如果后续你还能在真实终端复现双份输出，下一步就要对 rewrite 前后的 ANSI 串做实机日志抓取，继续检查 JLine prompt redisplay 是否仍然带来额外偏移

### 小点 26：修复 tool turn 中 speculative assistant preamble 残留，避免最终答案再次整块追加

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 在一个 turn 第一次进入 `TOOL_CALL` 时，除了继续禁止 tool 之后的 assistant body streaming，还会主动丢弃当前已经流式画到主缓冲区的 assistant block
  - 这样 tool turn 会保留 `Thinking` / tool feedback，但不会把“我来帮你创建...”这类 speculative preamble 留在 transcript 里，等最终 `FINAL_OUTPUT` 再打印一份
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 新增 `clearAssistantBlock()`，在 JLine 主壳真实终端层直接回退并擦除已追踪的 assistant rows，然后重绘输入行/状态栏
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/TranscriptPrinter.java`
  - assistant block 被清掉后，同时重置 transcript printer 的 `printedBlock` 状态，避免下一段 tool block / final block 前额外再插入一个空白分隔行
  - 新增 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java`，覆盖 assistant block 清理后的 tracking reset 与 ANSI 擦除序列输出
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/TranscriptPrinterTest.java`，覆盖 cleared block 之后不再错误插入空行
- 效果：
  - 针对你最新截图里的形态：上方残留一份没有代码块内容的 preamble/半成品，下方又追加一份完整 markdown 最终答案
  - 现在 tool turn 一旦进入工具阶段，前面那份 speculative assistant block 会先被清掉，最终答案只保留一份完整 block
- 是否达标：
  - 在当前定向测试与重新打包范围内达标
- 仍有缺陷：
  - 这次修复针对的是 `JLine main-buffer + tool turn` 的残留 block；最终视觉效果仍应以你本机 PowerShell / Windows Terminal 的 jar 实测为准

### 小点 27：停止 main-buffer 的 fragment 级正文重写，改为稳定的 append-only transcript

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - main-buffer 模式下不再做 assistant 正文的增量 rewrite / replay；assistant 最终正文统一在 `FINAL_OUTPUT` 时按原始 markdown 一次性落块
  - main-buffer 模式下 reasoning 也不再按 fragment 即时刷到 `printAbove(...)`，而是在稳定边界统一 flush 成 `Thinking:` block
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - JLine read 模式输出前，先按显示宽度做 `wrapAnsi(...)`，避免中文/标点/引号在 `printAbove(...)` 路径里被终端自己软换行，造成你截图里的断裂
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - `printAssistantBlock(...)` 不再依赖 fence 行二次猜测 code state，而是直接基于 `AssistantTranscriptRenderer.render(...)` 的 code/text 语义输出，修复 final block 丢失代码语义后只剩缩进、没有语法高亮的问题
- 效果：
  - 去掉了最脆弱的链路：`JLine printAbove + 手写回退擦除 + markdown replay`
  - 正文不再留下大块空白洞
  - final code block 恢复按原始 markdown 语义渲染，代码颜色不会因为 replay 过程中丢掉 fence 语义而退化成普通缩进文本
  - reasoning 不再以碎片 token 形式打到主缓冲区，中文长句的断行稳定性明显提高
- 是否达标：
  - 在当前自动化测试和重新打包范围内达标
- 仍有缺陷：
  - 这个改法优先保证主缓冲区稳定，不再追求 assistant 正文逐 token 可见；如果后续要恢复更接近 Codex 的正文渐进式输出，需要单独做“按视觉行增量提交”的 retained transcript，而不是再回到 cursor rewrite

### 小点 28：修复 final assistant block 前出现大段中空区域

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - main-buffer 模式下打印 `FINAL_OUTPUT` 前，不再先调用 `clearTransient()`
  - 原因是这一步会先触发 idle/status 重绘，再让 final block 从新的终端锚点开始输出；在 JetBrains Terminal + JLine 状态栏链路里，这会把最终正文压到靠近底部的位置，中间留下大块空白
- 效果：
  - final assistant block 会直接接在上一段 transcript 后面打印，不再先被一次 idle/status redraw 改写光标锚点
- 是否达标：
  - 在当前自动化测试与重新打包范围内达标
- 仍有缺陷：
  - 如果 JetBrains Terminal 仍然保留中空区域，下一步就不是继续调 `clearTransient()` 了，而是要把主缓冲区彻底切到 retained transcript 视图，绕开 JLine status 锚点行为

### 小点 29：修复 JLine read 模式下空行通过 `printAbove("")` 放大成大段留白

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 在 JLine `isReading()` 路径里，`newline == true` 且包裹后的输出文本为空时，不再调用 `lineReader.printAbove("")`
  - 改为传一个安全的非空占位符 `" "`，只保留视觉上的空行，不再触发 JetBrains Terminal + JLine 对空字符串 `printAbove` 的异常留白行为
  - 新增 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java` 定向验证：read 模式下打印空行时，底层 `printAbove(...)` 收到的是 `" "` 而不是空串
- 效果：
  - assistant 最终正文里的段落空行、代码块前后空行、transcript block 分隔空行，都会继续显示，但不会再在输出前被放大成一整片空白区域
  - 这次修复命中的是最底层 JLine 输出适配层，所以不需要再到每个上层 block formatter / markdown renderer 单独打补丁
- 是否达标：
  - 在当前定向测试与重新打包范围内达标
- 仍有缺陷：
  - 这是针对 JetBrains Terminal / JLine 空串 `printAbove` 行为的窄修复；如果你本机仍有残余空洞，下一步就要抓真实终端 ANSI 序列继续看 status redraw 是否也参与了行数膨胀

### 小点 30：把 main-buffer transcript 从“逐行 `printAbove`”改成“整块一次性 `printAbove`”

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 新增 transcript block 输出路径：
    - 普通 transcript block 先拼成一个完整字符串，再一次性输出
    - assistant markdown final block 直接按完整 markdown 着色后，一次性输出
  - 同时在 JLine reading 模式下，对 block 内部的空白行也统一标准化成 `" "`，避免只修单独空行、不修 block 内部空段落
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/TranscriptPrinter.java`
  - JLine 主壳下的 reasoning/tool block 不再逐行 `terminal.println(...)`，而是委托给 JLine block 输出
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - main-buffer final assistant block 在 JLine 主壳下不再逐行打印，而是整块 markdown 着色后一次性输出
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java`
  - 增加回归测试，验证 multiline transcript block 在 reading 模式下只触发一次 `printAbove(...)`
- 效果：
  - 避开了 JLine `printAbove(...)` 每调用一次都要“清 display + 重画 prompt”的路径放大效应
  - reasoning / tool / final assistant block 都变成一次性上屏，JetBrains Terminal 下更接近 Codex/Claude Code 的整块稳定落版
  - 大片留白的根因从“单个空行”进一步收敛到“逐行 `printAbove` 重复触发的 prompt/display 重绘”
- 是否达标：
  - 在当前定向测试与重新打包范围内达标
- 仍有缺陷：
  - 这次修复已经把 transcript 输出粒度下沉到 block 级；如果 JetBrains Terminal 仍能复现大块空洞，下一步就需要直接抓真实终端 ANSI 序列，确认是不是 JLine `Status` 小组件本身和 JetBrains 的 scroll-region 支持还存在兼容问题

### 小点 31：默认关闭 JLine footer status，先切断 `Status.update(...)` 对滚动区的持续重绘

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 新增 `statusComponentEnabled` 开关，默认值改为关闭
  - 仅当显式设置 `-Dai4j.jline.status=true` 或环境变量 `AI4J_JLINE_STATUS=true` 时才重新启用 JLine footer status
  - busy/idle 状态更新、spinner 线程、`Status.update(...)` 在默认路径下都不再触发
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java`
  - 新增默认关闭状态区的回归测试，并保留显式开启时的 redraw 去重测试
- 效果：
  - 把最后一个仍在“等待期间持续刷终端”的 JLine 组件直接切掉，避免 footer redraw 干扰主 transcript 区
  - 这一步是诊断型收口：如果空白区仍然存在，就说明根因不在 status footer，而在 transcript 输出路径本身
- 是否达标：
  - 在定向测试范围内达标
- 仍有缺陷：
  - 默认不再显示 JLine footer spinner/status；需要后续用更稳的方式重接

### 小点 32：在 reading 模式下绕开 `printAbove(...)`，直接写终端并重绘输入行

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 对 JLine `isReading()` 且 `newline == true` 的主路径，不再调用 `lineReader.printAbove(...)`
  - 改为：
    - 直接向 `Terminal` 写入内容
    - 先清掉当前输入行
    - 输出 transcript block / assistant markdown block
    - 再调用 `REDRAW_LINE` + `REDISPLAY` 恢复输入框
  - 保留 `newline == false` 的窄路径继续走 `printAbove(...)`，避免影响未来可能恢复的 token 级增量输出
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java`
  - 回归测试改成验证：multiline block 和空行在 reading 模式下不再触发 `printAbove(...)`，而是直接写终端并触发输入框重绘
- 效果：
  - 这一步直接绕开了 JetBrains Terminal + JLine `printAbove(...)` 的不稳定渲染链路
  - 用户看到的“先一行一行长空白、最后正文才一起出现”的现象，根因大概率就在这里
  - 现在正文 block 会直接落到终端，再由 JLine 单独重绘输入框，而不是先挪出空白区域再回填正文
- 是否达标：
  - 代码与定向测试层面达标
- 仍有缺陷：
  - `newline == false` 的 token 级 reading 输出还保留旧路径；如果后面要恢复真正的 token 级 main-buffer 流式输出，还需要把这条窄路径也改成 direct-terminal 方案

### 小点 33：把主缓冲区 prompt 改成异步常驻，并把正文块重新收口到 `printAbove(...)`

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - `runCliLoop(...)` 不再在每个 turn 里同步阻塞 `terminal.readLine("> ")`
  - 新增 `AsyncPromptReader`：
    - 后台线程持续持有下一次 `readLine("> ")`
    - 当前 turn 执行时，JLine 仍保持 `isReading() == true`
    - 非退出命令会在 dispatch 前立刻预取下一次 prompt
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 在确认 `isReading()` 已真实保持为 `true` 后，`newline == true` 的正文块重新改回 `lineReader.printAbove(...)`
  - 删除 reading/newline 路径下的 direct-terminal 写法，避免与 JLine 自己的 prompt redisplay 打架
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
  - 新增主缓冲区异步 prompt 回归测试，验证当前请求执行期间已经重新挂起下一次 prompt
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIOTest.java`
  - reading 模式下的空行 / multiline block 回归测试改为验证 `printAbove(...)` 路径，而不是 direct write
- 效果：
  - 之前真实 Windows/PowerShell 终验里那种“先长出很多空白行，正文最后才出现”的现象，根因被收敛成两段：
    1. prompt 生命周期是阻塞式，agent 输出发生时 `isReading() == false`
    2. 在错误生命周期上又叠加了 direct write 方案，和 JLine 的输入行重绘发生冲突
  - 现在主缓冲区进入更接近 Codex/Claude Code 的状态：
    - 当前 turn 在跑时，下一次 prompt 已经挂起
    - reasoning / assistant / transcript block 都通过 JLine 的 “print above current prompt” 语义落版
    - 实机回归里大块留白已经消失，只剩 prompt 正常重绘的控制序列
- 是否达标：
  - 定向测试达标
  - 重新打包达标
  - 真实 provider 回归达标：使用 `java -jar .\\ai4j-cli\\target\\ai4j-cli-2.0.0-jar-with-dependencies.jar tui --provider zhipu --protocol chat --model glm-4.7 --base-url https://open.bigmodel.cn/api/coding/paas/v4 --workspace G:\\My_Project\\java\\ai4j-sdk` 进行主缓冲区实测，`hello` 与代码块请求都不再出现之前那种整片空白区
- 仍有缺陷：
  - PTY 日志里仍能看到 prompt 重绘的 ANSI 控制字符；这属于终端抓取层可见的 control sequence，不是用户之前看到的“大段空白正文”
  - `/exit` 在当前 JLine slash-menu 绑定下还有额外行为，和本次 blank-area 修复无关，后续单独收口

### 小点 34：把下一次 prompt 改成“懒激活”，并移除 streaming block 的自动空行分隔

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - `AsyncPromptReader.start()` 不再一启动就立刻挂起下一次 prompt
  - 主循环改成：
    - 每次真正需要读用户输入前，才 `requestNextPrompt()`
    - 当前 turn 执行期间，通过 `JlineShellTerminalIO` 的 deferred activator 在“第一次正文真正要输出时”再懒激活下一次 prompt
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineShellTerminalIO.java`
  - 新增 `setDeferredPromptActivator(...)`
  - 当 JLine 当前不在 reading 状态、但正文即将输出时，先激活下一次 prompt，再决定是否走 `printAbove(...)`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/TranscriptPrinter.java`
  - `beginStreamingBlock()` 不再自动插入 block separator 空行
  - reasoning -> assistant / assistant -> code block 这类连续正文，不再被 transcript printer 额外塞入一个空白分隔
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
  - 新增验证：慢请求在正文输出开始前，不会提前挂出下一次 prompt
  - 更新 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/TranscriptPrinterTest.java`
  - streaming block 分隔行为改成新的最小空行策略
- 效果：
  - 你按回车后，不会再立刻先出现一个空 prompt 行在那里“等输出”
  - 主体空白问题进一步收敛：先前那种“用户消息后先空一块，再开始出正文”的现象被直接打掉
  - reasoning 和 assistant 正文之间不再被 transcript printer 额外再塞一行空白
- 是否达标：
  - 定向测试达标
  - 重新打包达标
  - 真实 provider 再次回归达标：`hello` 请求下，用户输入后不再先挂出一条空 prompt 再等正文
- 仍有缺陷：
  - PTY 抓取里仍能看到 JLine prompt 重绘的控制序列，这和用户肉眼看到的终端内容不是一回事
  - 某些“Thinking 后直接进入纯代码块”的回复，在 PTY 日志里仍能看到一次换行控制；如果用户本机视觉上还能感知成空洞，再继续收这条更窄的路径

## 验证

- 已验证：`mvn -pl ai4j-cli -am -DskipTests compile`
- 已验证：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,UnicodeTextInputElementTest' test`
- 已验证：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=SlashCommandControllerTest,CodeCommandTest,CodexStyleBlockFormatterTest,TranscriptPrinterTest,PatchSummaryFormatterTest' test`
- 已验证：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=SlashCommandControllerTest,CodeCommandTest' test`
- 已验证：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CliThemeStylerTest,SlashCommandControllerTest,CodeCommandTest,CodexStyleBlockFormatterTest,TranscriptPrinterTest,PatchSummaryFormatterTest' test`
- 已验证：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=SlashCommandControllerTest,CodeCommandTest,CliThemeStylerTest' test`
- 已验证：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,CliThemeStylerTest,SlashCommandControllerTest' test`
- 已验证：`mvn -pl ai4j-cli -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,TranscriptPrinterTest,SlashCommandControllerTest' test`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,TuiConfigManagerTest,SlashCommandControllerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,TuiConfigManagerTest,SlashCommandControllerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,CodeCommandTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=JlineShellTerminalIOTest,TranscriptPrinterTest,AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest,JlineShellTerminalIOTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,JlineShellTerminalIOTest,SlashCommandControllerTest,TranscriptPrinterTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,JlineShellTerminalIOTest,SlashCommandControllerTest,TranscriptPrinterTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,JlineShellTerminalIOTest,SlashCommandControllerTest,TranscriptPrinterTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=AssistantTranscriptRendererTest,CodeCommandTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=AssistantTranscriptRendererTest,CodeCommandTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：`mvn -pl ai4j-cli,ai4j-tui -am '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=CodeCommandTest,JlineShellTerminalIOTest,TranscriptPrinterTest,AssistantTranscriptRendererTest,CliDisplayWidthTest,CliThemeStylerTest,CodexStyleBlockFormatterTest,SlashCommandControllerTest,TuiConfigManagerTest' test`
- 已验证：`mvn -pl ai4j-cli -am -DskipTests package`
- 已验证：真实 provider 主缓冲区回归：`hello`、代码块输出都不再出现“先刷一大段空白，再一起出正文”的症状
- 已验证：真实 provider 主缓冲区回归（第二轮）：`hello` 在用户消息提交后不再先挂出空 prompt 行等待输出

## 当前状态

- JLine 主壳已经接入，`--ui tui` 在系统交互终端中的默认 backend 已改为 `JLine`
- 业务层没有重写，迁移风险被控制在 `ai4j-cli` 交互层
- Phase 2 已完成第一轮状态区与 transcript 接入，Phase 3 已完成第一轮基础 block formatter 落地
- Phase 4 已完成第二轮 slash command controller 与候选补全接入，`/process` 深层参数补全已落地
- Phase 5 的 approval inline 已完成第一轮收口
- JLine 主路径的 theme/颜色链路已接入，`/theme` 切换会同步作用到状态栏与 transcript
- 当前主问题已经从“正文前持续长空白区”收口到更窄的交互尾项；下一步重点是继续做 `Windows Terminal + PowerShell` 的 slash/exit 细节终验
