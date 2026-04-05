# 2026-03-23 Codex 对齐 JLine CLI 设计

## 背景

当前 `ai4j-cli` 的交互式 TUI 主路径已经尝试迁到 `旧实验 TUI`，但实际体验与目标存在明显偏差：

- 输入链在 Windows Terminal + PowerShell + 中文输入法场景下不稳定
- composer、spinner、slash palette、tool/patch/compact 块之间的节奏仍不够像 Codex
- 框架天然更接近 widget app，不是 shell-first 的 transcript + composer 交互模型
- 为了修复输入、光标、刷新、空白等细节，需要持续绕过框架默认行为

目标已经明确：`ai4j-cli` 必须在 `Windows Terminal + PowerShell` 下尽可能细节对齐 Codex 体验，而不是只做到“可用”。

## 目标

- 将 `--ui tui` 主路径切换为 `JLine 3` 驱动的 shell-first 架构
- 在 `Windows Terminal + PowerShell` 下优先对齐 Codex 体验
- 保留现有 `agent / session / tool / patch / compact` 业务逻辑
- 用新的 CLI UI 壳替换 `旧实验 TUI` 主路径，而不是重写整个 coding agent
- 为后续人工终验提供稳定、可迭代的体验契约和验收标准

## 非目标

- 本轮不追求在 `cmd / Git Bash / IDE terminal` 下完全同体验
- 本轮不重写 `ai4j-coding`、`agent runtime`、`tool executor`
- 本轮不尝试用全屏 alternate-screen 界面模拟 Codex
- 本轮不保留 `旧实验 TUI` 作为正式主路径

## 方案备选

### 方案 A：继续深度修补 `旧实验 TUI`

优点：

- 已有代码可复用更多
- 变更表面上较小

缺点：

- 底层交互模型不适合 Codex 类 shell 体验
- 输入、光标、动态刷新、空白控制、Windows/IME 兼容会继续成为长期负担
- 每修一个问题都在继续加深对框架内部行为的耦合

结论：不采用。

### 方案 B：改用 `Lanterna` / `Jexer` 一类全屏 TUI 框架

优点：

- 组件和布局体系成熟
- 面板、弹窗、列表等现成功能较多

缺点：

- 更偏终端桌面应用，不是 shell-first 交互
- transcript + composer + print-above 这种 Codex 样式不是其主战场
- 做出来更像“全屏 TUI 管理台”，不像 Codex CLI

结论：不作为主线。

### 方案 C：基于 `JLine 3` 重建 shell-first CLI 主壳

优点：

- 最符合 Codex 类 CLI 的交互模型
- `LineReader`、`printAbove`、`Status`、widgets、Unicode 输入、Windows provider 支撑更直接
- 更容易精确控制输入、输出、状态、空白、动态效果

缺点：

- UI 层很多行为需要自己定义和收口
- 需要重建现有 `旧实验 TUI` runner

结论：本轮采用。

## 体验契约

### 1. 终端与运行环境

- P0 参考环境固定为 `Windows Terminal + PowerShell`
- 默认运行在 main buffer，不使用 alternate screen
- transcript 进入 scrollback，不做全屏历史重绘
- 其它终端允许降级，不承诺完全同体验

### 2. Composer

- 底部永远只有一个活的 composer
- 使用终端原生光标，不再手动画假光标
- 支持英文、数字、符号、中文、粘贴、`Backspace/Delete/Left/Right/Home/End`
- 提交后立即清空并恢复可输入状态
- `Esc` 优先关闭 slash palette，其次清空输入
- `Ctrl+C` 在空闲时退出，在忙碌时仅提示不可退出

### 3. 动态状态

- 状态仅允许 `Idle / Thinking / Working / Responding`
- spinner 只在底部状态区更新
- transcript 不因 spinner 或状态刷新而抖动
- hint 只保留一行，按当前上下文切换

### 4. Transcript 与块级输出

- 历史输出只增不改，进入 scrollback
- 块之间只保留一个空行
- 不显示 `assistant` / `user` 标签
- `tool / patch / compact / error / session` 必须都是块级输出
- 长文本需要有宽度控制和换行纪律

### 5. Slash Palette

- 只有在命令上下文中显示
- 同一时刻只能出现一份
- 支持 `Up/Down/Tab/Enter/Esc`
- 补全后光标必须留在末尾
- 关闭后不能留残影、空白和重复命令文本

### 6. Approval / Session / Compact

- approval 走 inline，不弹全屏伪窗口
- session 的 opened/saved/resumed/forked/compacted 都有简洁块
- compact 要显式但克制，不污染主路径
- 高级元数据默认收敛，仅在命令中展开

## 总体架构

`ai4j-cli` 的新交互层采用 shell-first 分层：

### 1. Input Layer

只负责：

- `LineReader` 和 composer 行为
- slash 命令输入与补全
- 审批输入
- 快捷键处理

### 2. Render Layer

只负责：

- transcript block 打印
- status line
- spinner
- 空白与换行纪律

### 3. State Layer

只负责：

- 当前会话元信息
- 当前 turn 状态
- 忙碌状态
- palette 状态
- pending tool 状态

### 4. Bridge Layer

只负责：

- 将 `AgentEvent` / `SessionEvent` 转为状态变化和块级输出
- 保证 UI 线程串行消费事件

## 关键类设计

建议新增或替换的主类如下：

- `JlineCodeCommandRunner`
  - `--ui tui` 主入口
  - 管理启动、输入循环、事件桥接、退出
- `JlineShellContext`
  - 持有 `Terminal`、`LineReader`、`Status`、能力信息
- `ComposerController`
  - 处理输入、提交、焦点、slash 模式、快捷键
- `SlashCommandController`
  - 命令注册、补全、palette 行为、命令帮助
- `CliStatusController`
  - `Idle / Thinking / Working / Responding` 状态机和 spinner
- `TranscriptPrinter`
  - 统一走 `printAbove`
  - 控制块间空白、换行、打印顺序
- `CodexStyleBlockFormatter`
  - 生成用户块、回复块、tool 块、patch 块、compact 块、error 块
- `ApprovalController`
  - 审批提示与确认输入
- `AgentEventBridge`
  - 消费后台事件并投递到 UI 线程
- `TerminalCapabilityDetector`
  - 检测 ANSI、raw input、Windows provider 能力并决定降级

## 对现有代码的处理

### 保留

- `CodeCommand`
- `CodeCommandOptions`
- `CodingSessionManager`
- `SessionEventStore`
- `CustomCommandRegistry`
- `ai4j-coding` 内的 session、tool、patch、process 逻辑

### 替换主路径

- `旧实验 runner` 退出 `--ui tui` 主路径
- `旧自定义输入组件` 不再是正式交互路径依赖

### 过渡策略

- 新 runner 成熟前，保留旧 runner 作为隐藏 fallback
- 默认用户路径逐步切到 `JLine 3`

## 线程模型

- `LineReader` / `Terminal` 只能由 UI 线程串行访问
- agent 在后台线程执行
- 后台线程不能直接写终端，只能投递 UI 事件
- UI 线程负责：
  - 更新状态栏
  - 输出 transcript block
  - 恢复 composer 行

这条约束是保证不出现吞字、错位、重复刷新和线程争用的基础。

## 平台与降级策略

- `Windows Terminal + PowerShell`：完整 Codex 对齐路径
- `cmd / Git Bash / IDE terminal`：降级模式
- 降级触发条件包括：
  - 缺少 raw input
  - ANSI / cursor / status 支持不足
  - provider 不满足期望

降级后必须保证可用性，并给出一次明确提示，但不承诺完整体验一致。

## 验收标准

### 自动化验收

- slash 逻辑
- block formatter
- session / compact / approval 流程
- tool / patch 结果展示
- 状态机与状态切换

### 人工验收

- 中文输入法
- 粘贴
- 光标闪烁
- spinner 体感
- slash palette 单实例
- tool / patch / compact 的视觉节奏
- 多轮连续会话的稳定性

### 主路径达标定义

当用户可以连续完成以下流程，并且没有重复 `/`、吞字、空白污染、焦点丢失、异常闪烁时，视为主路径达标：

- 输入中文
- 使用 slash palette 执行命令
- 运行一次 tool
- 查看一次 patch 结果
- 触发一次 compact
- 继续输入下一条消息

## 风险

- `LineReader` 与异步 transcript 协调不当会导致输入错位
- Windows 输入法和粘贴边界需要真实终端人工终验
- 过早追求复杂子界面会拖慢主路径稳定

## 当前决策

- 停止继续在 `旧实验 TUI` 主路径上做体验级补丁
- 采用 `JLine 3` shell-first 架构重建 `--ui tui`
- 保留业务层，重建 UI 层
- 先以 `Windows Terminal + PowerShell` 为 P0 达标环境
