# UserPromptSubmit interception hook - 进度

## 状态：进行中

### [2026-06-30] - UserPromptSubmit 拦截实现（PR #155 merged）

- 库（io.github.lnyocly.ai4j.agent.interceptor）：PromptInterceptor.beforePrompt→PromptDecision(allow/block/modify)；接进 runInternal 在 input 入 memory 前——block 直接返回 PROMPT_BLOCKED 不调模型，modify 替换 input，非 String（多模态）跳过。
- CLI（io.github.lnyocly.ai4j.cli.hook）：CliPromptInterceptor 把 PromptInterceptor 桥到外部命令（userPromptSubmit 配置，同 CliHookInterceptor 语义）；CliHooksConfig.userPromptSubmit；CodingAgentBuilder.promptInterceptor；factory 同时接 tool+prompt hook。
- 文档：tool-interceptor.md 改名 "Interception Hooks (tool + prompt)" + UserPromptSubmit 段。
- 验证：4 库测试（block 不调模型、modify 改写、allow、none）+ 5 CLI 桥测试；agent+coding+cli 315 测试 0 失败。
- 证据：PR #155 MERGED

## 残余
- 其它事件（PostToolUse=observe 已被 lifecycle hook 覆盖；Stop/SubagentStop/PreCompact=niche）未做，ROI 低。

## 协调者交接
- pending-coordinator-pass
