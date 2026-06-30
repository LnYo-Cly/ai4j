# AgentHooks facade - 进度

## 状态：进行中

### [2026-07-01] - AgentHooks 门面（PR #158 merged）

- AgentHooks 门面：preToolUse/postToolUse/userPromptSubmit/stop/preCompact/sessionStart/sessionEnd，每个收正确函数式接口（编译期安全）；pre+post 组合成单 ToolInterceptor，observe 组合成单 lifecycle hook。首个非-allow 决策生效；observe 全跑、错误吞掉。
- AgentBuilder.hooks(Consumer<AgentHooks>)——pi 式"一处注册所有事件"+ IDE 可发现，不丢 Java 类型安全。纯糖，不改 runtime。
- 新 PostToolUseHook + ObserveHook 函数式接口（让 post/observe 接 lambda）。
- docs：tool-interceptor.md 加 "Quick start: hooks facade"（推荐 API + 事件/能力表）。
- 验证：4 门面测试（preToolUse/postToolUse/userPromptSubmit block + stop observe 组合）；ai4j-agent 186 测试 0 失败。
- PR #158 MERGED

## 协调者交接
- pending-coordinator-pass
