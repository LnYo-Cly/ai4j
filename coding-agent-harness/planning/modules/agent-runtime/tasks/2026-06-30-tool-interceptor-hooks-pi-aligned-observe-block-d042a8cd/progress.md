# Tool interceptor hooks (pi-aligned) - 进度

## 状态：进行中

## 进度记录

### [2026-06-30] - 库层拦截 hook 实现（PR #151 merged）

- 做了什么（包 io.github.lnyocly.ai4j.agent.interceptor，与 observe-only lifecycle hook 并存）：
  - ToolInterceptor.beforeToolCall(call,ctx) -> ToolCallDecision
  - ToolCallDecision: allow / block(reason) / modify(newCall) / routeTo(sandboxSpec)
    - block：veto，reason 回喂模型（Claude Code PreToolUse exit-2 语义）
    - modify：改写 call 后执行改写版
    - routeTo：转沙箱——超越 pi 的点（pi/Claude Code 无 sandbox SPI，ai4j 有 Daytona/E2B）。v1 带 SandboxSpec；沙箱 session 执行接线 = 后续。
  - 接进 BaseAgentRuntime.executeTool（无 interceptor => 行为不变）；AgentBuilder.toolInterceptor(...)（顺手修 control-flow hook 注册要走 extension SPI 的缺口）。
- 依据：pi（~18-25 hook，observe/block/modify/redirect）+ Claude Code（12 事件，exit-2 block + JSON modify）趋同；非 speculative。
- 验证：4 确定性 agent-loop 测试（block 回喂、modify 改写、allow、none==allow）；ai4j-agent 全模块 175 测试 0 失败；diff 干净。
- 证据：command:G:\My_Project\java\ai4j-sdk:4 interceptor tests pass; ai4j-agent 175 tests; PR #151 MERGED

## 残余（后续独立 PR）

- #2 routeTo 接到绑定的 sandbox session（真正的超越-pi demo）
- #3 CLI 外部命令桥（终端用户 Claude-Code 式配 hook 脚本）
- #4 docs-site 页

## 协调者交接
- Global sync status：pending-coordinator-pass
- 负责人：coordinator
