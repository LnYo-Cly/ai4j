---
sidebar_position: 13
title: 生命周期钩子与工作区信任
description: 讲清 Claude-Code 风格的 shell-command 生命周期钩子（PreToolUse/PostToolUse/UserPromptSubmit/Stop/PreCompact/SessionStart/SessionEnd）的配置、拦截/观察分流、退出码协议，以及让这些钩子能安全加载的工作区信任门与 ai4j-cli trust 命令。
tags: [how-to]
---

# 生命周期钩子与工作区信任

AI4J CLI 支持把 **外部 shell 命令** 挂到 coding agent 的生命周期事件上，即 Claude-Code 风格的 end-user hook。

但钩子会执行任意 shell 命令，所以它前面必须有一道信任门：**未经用户显式信任的工作区目录，其钩子不会被加载。** 这两件事是一对设计，必须放在一起理解。

---

## 1. 七类钩子事件，分成两档

钩子声明在 workspace 配置 `<workspace>/.ai4j/workspace.json` 的 `hooks` 字段里，由 `CliHooksConfig` 解析。当前支持七类事件，按“能不能阻断流程”分成两档：

| 事件 | 对应 Claude Code | 是否可拦截阻断 | 实现入口 |
| --- | --- | --- | --- |
| `preToolUse` | PreToolUse | 是（可 block / modify） | `CliHookInterceptor.beforeToolCall` |
| `postToolUse` | PostToolUse | 是（可 block 反馈） | `CliHookInterceptor.afterToolCall` |
| `userPromptSubmit` | UserPromptSubmit | 是（可 block / modify） | `CliPromptInterceptor.beforePrompt` |
| `stop` | Stop | 否（仅观察） | `CliLifecycleHookBridge`（AFTER_TURN） |
| `preCompact` | PreCompact | 否（仅观察） | `CliLifecycleHookBridge`（ON_COMPACT） |
| `sessionStart` | SessionStart | 否（仅观察） | `CliLifecycleHookBridge`（SESSION_START） |
| `sessionEnd` | SessionEnd | 否（仅观察） | `CliLifecycleHookBridge`（SESSION_END） |

最关键的区分：

- **拦截型**（`preToolUse` / `postToolUse` / `userPromptSubmit`）能改变流程：可以 block 这次调用、或 modify 它的入参。
- **观察型**（`stop` / `preCompact` / `sessionStart` / `sessionEnd`）只跑副作用：命令的输出会被丢弃，抛异常也被吞掉。**观察型钩子绝不能中断正常执行。**

把这两档分开，是 Claude-Code 钩子模型在 AI4J 里的真实落地，不是同一套回调。

---

## 2. 怎么配置一个钩子

钩子声明在 `<workspace>/.ai4j/workspace.json` 的 `hooks` 字段。每个钩子是一个对象：

- `command`：要执行的 shell 命令（必填）
- `match`：要匹配的工具名（仅对 `preToolUse` / `postToolUse` 有意义）

`match` 的语义在 `CliHookEntry.matches(...)` 里：

- 省略 / 空字符串 / `"*"`：匹配所有工具
- 否则要求精确等于工具名（如 `"bash"`）

一个最小配置例子：

```json
{
  "hooks": {
    "preToolUse": [
      { "command": "python ~/.ai4j/hooks/guard.py", "match": "bash" }
    ],
    "userPromptSubmit": [
      { "command": "python ~/.ai4j/hooks/prompt_guard.py" }
    ],
    "sessionStart": [
      { "command": "echo 'session started' >> ~/.ai4j/session.log" }
    ]
  }
}
```

上面这个配置会：

- 仅在模型准备调用 `bash` 工具时，运行 `guard.py`
- 在每次用户提交 prompt 时，运行 `prompt_guard.py`
- 在 session 启动时，往日志追加一行（观察型，不影响流程）

---

## 3. 命令是怎么被实际执行的

真正 spawn 进程的是 `ProcessHookCommandRunner`：

- Unix 走 `sh -c <command>`
- Windows 走 `cmd /c <command>`
- 把事件 JSON（工具调用 / prompt / lifecycle event）写到子进程 stdin
- 捕获 exit code、stdout、stderr 并返回

也就是说钩子是**真实的子进程**，不是 JVM 内回调。这让用户可以用任意语言（python / node / bash 脚本）写钩子，而不必写 Java。

决策逻辑（怎么把退出码映射成 allow/block/modify）刻意放在 `CliHookInterceptor` / `CliPromptInterceptor` 里，runner 只负责“跑命令、收结果”，便于测试时用假 runner 替换。

---

## 4. 退出码协议（拦截型钩子）

拦截型钩子的退出码和 stdout 决定流程走向。这套协议和 Claude Code 的 PreToolUse 一致：

| 退出情况 | 结果 |
| --- | --- |
| **exit 2** | block（拒绝执行）；reason 取 stderr，为空则取 stdout |
| **exit 0 + stdout JSON** `{"decision":"block","reason":"..."}` | block |
| **exit 0 + stdout JSON** `{"decision":"modify","name":"...","arguments":"..."}`（工具钩子） | modify 这次工具调用的入参 |
| **exit 0 + stdout JSON** `{"decision":"modify","input":"..."}`（prompt 钩子） | modify 用户输入 |
| **exit 0 / 其它** | 继续评估下一个钩子（暂时 allow） |
| **钩子本身抛异常 / 崩溃** | fail-closed：block（带原因） |

合并规则是：**第一个 block 生效；否则第一个 modify 生效；否则放行。**

:::warning 钩子崩溃 = 拒绝
拦截型钩子是安全钩子。一个会崩的钩子不能让工具“蒙混过关”——所以钩子抛异常时按 fail-closed 处理为 block，而不是 allow。如果你写了一个会偶发失败的钩子，请确保它退出码用 0/1（soft error，会继续），而不是抛异常。
:::

---

## 5. 观察型钩子为什么绝不能 block

`CliLifecycleHookBridge` 把 `stop` / `preCompact` / `sessionStart` / `sessionEnd` 路由到外部命令，但它的语义是纯副作用：

- 命令的 stdout 被丢弃
- 命令抛的异常被吞掉（`catch (Exception ignored)`）

这是刻意的设计。这几类事件在 Claude Code 里本就是“事后通知 / 上下文注入”，不是“决策点”。如果你需要阻断工具调用，应该用 `preToolUse`，而不是 `stop`。

典型观察型用法：

- `stop`：一轮结束发个通知、写审计日志
- `preCompact`：compact 前把上下文快照备份
- `sessionStart` / `sessionEnd`：记录会话生命周期指标

---

## 6. 工作区信任门（Workspace Trust Gate）

钩子会执行任意 shell 命令，所以加载钩子前必须过一道信任门：`WorkspaceTrustGate`。

### 6.1 信任流程

`DefaultCodingCliAgentFactory.attachToolHooks(...)` 在装配钩子前会调用信任门，流程是：

1. 如果 workspace 没有声明任何钩子 → `NO_HOOKS`，直接放行（不需要信任）。
2. 如果目录已在 `~/.ai4j/trusted-dirs.txt` 里 → `TRUSTED`，放行。
3. 否则，把完整的钩子配置打印出来供审查，并提示 `y/n`：
   - 输入 `y`：把目录持久化进 `trusted-dirs.txt`，返回 `TRUSTED`
   - 输入 `n`：返回 `UNTRUSTED`，**钩子本次不会加载**
   - 读到 EOF / 读失败：返回 `UNTRUSTED`（fail-closed）

也就是说：**只有当用户在终端显式确认过，或之前已经信任过这个目录，工作区钩子才会真正生效。**

### 6.2 显示钩子时会剥掉 ANSI 转义

`WorkspaceTrustGate.sanitizeForDisplay(...)` 在打印钩子命令前，会剥掉 ANSI 转义序列（包括设置终端标题的 OSC 序列）。

这是防注入措施：恶意的 workspace.json 不能靠终端控制码把真实命令藏在显示文本里。你在审查提示里看到的命令字符串，就是子进程真正会执行的字符串，不会被转义码伪装。

### 6.3 信任记录存在哪里

信任记录由 `TrustedDirsStore` 管理，落在 `~/.ai4j/trusted-dirs.txt`：

- 每行一个规范化后的绝对路径
- `#` 开头是注释
- 文件不存在 = 没有任何目录被信任
- 路径按 `toAbsolutePath().normalize()` 规范化后比较

---

## 7. `ai4j-cli trust` 命令

手动管理信任目录用 `ai4j-cli trust`（对应 `TrustCommand`）。这在 CI / 自动化场景特别有用——可以预先信任一个 workspace，避免交互式 `y/n` 提示。

```text
ai4j-cli trust --dir <path>      信任一个工作区目录
ai4j-cli trust --revoke <path>   撤销对一个目录的信任
ai4j-cli trust --list            列出所有已信任目录
```

示例：

```bash
# 预先信任 CI 里的工作区，这样钩子能无交互加载
ai4j-cli trust --dir /home/runner/work/my-repo

# 撤销信任（下次进入该工作区，钩子会再次触发审查）
ai4j-cli trust --revoke /home/runner/work/my-repo

# 查看当前机器上信任了哪些目录
ai4j-cli trust --list
```

说明：

- `--dir` 和 `--add` 等价；`--revoke` 和 `--remove` 等价
- 路径会被规范化成绝对路径后再写入
- `--revoke` 一个从未信任过的目录不会报错，只会提示 “was not trusted”
- 信任记录始终落在 `~/.ai4j/trusted-dirs.txt`

---

## 8. 钩子在 agent builder 上挂在哪里

从装配链路看，信任门通过后，三类钩子分别挂到 `CodingAgentBuilder` 的不同扩展点：

```text
DefaultCodingCliAgentFactory.attachToolHooks(...)
  -> WorkspaceTrustGate.checkTrust(...)         # 不过门就不挂
  -> builder.toolInterceptor(CliHookInterceptor)        # preToolUse / postToolUse
  -> builder.promptInterceptor(CliPromptInterceptor)    # userPromptSubmit（若有）
  -> builder.lifecycleHook(CliLifecycleHookBridge)      # 观察型（若有）
```

注意三个细节：

1. **没钩子就不挂**：`hasPromptHooks()` / `hasObserveHooks()` 为假时，对应的扩展点根本不会被装配，不会有空转开销。
2. **信任门只挡钩子**：信任门失败只影响钩子加载，不会阻止 session 本身启动。session 仍会正常运行，只是没有外部钩子。
3. **拦截型走 ToolInterceptor / PromptInterceptor，观察型走 AgentLifecycleHook**——这是两套不同的扩展 SPI，不要混淆。

---

## 9. 一个完整的“禁止危险命令”例子

下面是一个用 `preToolUse` 钩子拦截危险 bash 命令的最小例子。

`<workspace>/.ai4j/workspace.json`：

```json
{
  "hooks": {
    "preToolUse": [
      { "command": "python ~/.ai4j/hooks/block_rm_rf.py", "match": "bash" }
    ]
  }
}
```

`~/.ai4j/hooks/block_rm_rf.py`（读到 stdin 的工具调用 JSON，命中危险模式就退出 2）：

```python
import sys, json

call = json.load(sys.stdin)
args = json.loads(call.get("arguments", "{}"))
cmd = args.get("command", "")

if "rm -rf" in cmd:
    # exit 2 = Claude-Code 风格的 deny
    sys.stderr.write("blocked: refused destructive command -> " + cmd)
    sys.exit(2)

# 其它情况退出 0，继续评估 / 放行
sys.exit(0)
```

第一次进入该 workspace 时，CLI 会打印出这条钩子并要求 `y/n` 确认；用 `ai4j-cli trust --dir <workspace>` 可以预先跳过这一步。

---

## 10. 最容易踩坑的点

### 10.1 把观察型钩子当拦截型用

`stop` / `preCompact` / `sessionStart` / `sessionEnd` 的输出和异常都会被吞。想阻断流程，必须用 `preToolUse` / `postToolUse` / `userPromptSubmit`。

### 10.2 以为钩子默认就会加载

未信任的工作区，钩子不会被加载。这是安全默认，不是 bug。要预加载就 `ai4j-cli trust --dir`。

### 10.3 让安全钩子抛异常

拦截型钩子抛异常 = fail-closed block。如果你的钩子只是“可选检查”，请在失败时退出 1（soft error，继续），而不是让进程崩溃。

### 10.4 以为 `match` 能写正则

`match` 当前只支持精确工具名匹配（或 `*` / 空），不支持正则、不支持逗号分隔多工具名。要拦多个工具，声明多条钩子。

### 10.5 在 hook 命令里依赖交互输入

钩子是子进程，stdin 已经被事件 JSON 占用，不能再用它读用户输入。需要交互的逻辑应该留在 CLI 主体里。

---

## 11. 这页最该记住的结论

- 钩子是**外部 shell 命令**，分**拦截型**（preToolUse / postToolUse / userPromptSubmit，可 block/modify）和**观察型**（stop / preCompact / sessionStart / sessionEnd，纯副作用）两档。
- 拦截型遵循 Claude-Code 退出码协议：exit 2 = block，stdout JSON 可 block/modify，崩溃 = fail-closed block。
- 钩子声明在 `<workspace>/.ai4j/workspace.json` 的 `hooks` 字段。
- **钩子加载前必须过 `WorkspaceTrustGate`**：未信任的工作区钩子不会生效。预先信任用 `ai4j-cli trust --dir`，撤销用 `--revoke`，记录在 `~/.ai4j/trusted-dirs.txt`。
- 信任门在打印钩子时会剥 ANSI 转义，防止配置用终端控制码隐藏真实命令。

---

## 12. 继续阅读

1. [CLI / TUI 使用指南](/docs/products/coding-agent/cli-and-tui)
2. [Tools 与审批机制](/docs/products/coding-agent/tools-and-approvals)
3. [命令参考](/docs/products/coding-agent/command-reference)
4. [配置体系](/docs/products/coding-agent/configuration)
