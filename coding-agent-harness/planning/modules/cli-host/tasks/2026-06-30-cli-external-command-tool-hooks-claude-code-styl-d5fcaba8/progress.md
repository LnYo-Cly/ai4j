# CLI external-command tool hooks (Claude-Code-style) - 进度

## 状态：进行中

## 进度记录

### [2026-06-30] - CLI hook 桥实现（PR #154 merged）

- ai4j-coding：CodingAgentBuilder 加 toolInterceptor（原来只有 toolExecutor）。
- ai4j-cli 新包 io.github.lnyocly.ai4j.cli.hook：
  - CliHookInterceptor：ToolInterceptor ↔ 外部 shell 命令。exit 2→block（Claude Code PreToolUse deny）；exit-0 JSON {decision:block|modify}→block/modify；其它→continue；hook 崩→fail-closed block。首个 block/modify 生效。
  - HookCommandRunner 接口 + ProcessHookCommandRunner（真 spawn：sh -c / cmd /c，stdin 喂 tool-call JSON）。接口让决策逻辑可用 fake runner 测（确定性、跨平台）。
  - CliHookEntry/CliHooksConfig：用户声明的 hooks（preToolUse 列表 + match）。
  - CliWorkspaceConfig：hooks 字段（从 workspace config JSON 自动解析 → 终端用户文件配置，不用写 Java）。
  - DefaultCodingCliAgentFactory.attachToolHooks：配了 hook 就接 interceptor；没配则不变。
- 终端用户效果：workspace config 里写 `hooks.preToolUse: [{command, match}]`，bash 工具调用前先跑用户脚本，脚本 exit 2/JSON 决定 allow/block/modify。任意语言，= Claude Code 心智模型。
- 验证：8 测试（exit-2 block、JSON block/modify、allow、fail-closed、match 过滤、no-hooks、+ ProcessHookCommandRunner 真进程 smoke）；ai4j-coding+ai4j-cli 310 测试 0 失败。
- 证据：command:G:\My_Project\java\ai4j-sdk:8 hook tests; coding+cli 310 tests; PR #154 MERGED

## 残余
- 只接了 preToolUse（Claude Code 12 事件里的 headline）。其它事件（UserPromptSubmit/PostToolUse/Stop...）是同模式的后续。
- hook 真实端到端（配文件→跑 CLI→hook 触发）的手测未做（自动化已覆盖决策逻辑 + 真进程 spawn）。

## 协调者交接
- Global sync status：pending-coordinator-pass
- 负责人：coordinator
