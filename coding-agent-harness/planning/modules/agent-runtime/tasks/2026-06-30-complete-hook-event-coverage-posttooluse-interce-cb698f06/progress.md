# Complete hook event coverage - 进度

## 状态：进行中

### [2026-06-30] - 全事件覆盖（PR #156 merged）

- PostToolUse 拦截：ToolInterceptor.afterToolCall 默认方法——工具跑完后 hook 可 block 结果（替换成 TOOL_BLOCKED 回喂模型，如泄密拦截）。
- AgentBuilder.lifecycleHook(AgentLifecycleHook)——直接注册 observe hook，不用 extension SPI（修了 ergonomics 老缺口）。
- CliLifecycleHookBridge——一个通用 AgentLifecycleHook，把 AFTER_TURN/ON_COMPACT/SESSION_START/SESSION_END 路由到 stop/preCompact/sessionStart/sessionEnd 命令。副作用型，错误吞掉。
- CliHookInterceptor.afterToolCall——postToolUse 配置 → 外部命令（exit 2 block 结果）。
- CliHooksConfig：postToolUse/stop/preCompact/sessionStart/sessionEnd。
- CodingAgentBuilder.lifecycleHook；factory 接 tool+prompt+observe 三桥。
- 验证：+1 库测试（afterToolCall block）+ 4 observe-bridge 测试；agent+coding+cli 319 测试 0 失败。
- PR #156 MERGED

## 事件覆盖全表（全部文件配置可用）
PreToolUse(拦截 block/modify/routeTo) | PostToolUse(拦截 block 结果) | UserPromptSubmit(拦截 block/modify) | Stop/PreCompact/SessionStart/SessionEnd(observe)

## 协调者交接
- pending-coordinator-pass
