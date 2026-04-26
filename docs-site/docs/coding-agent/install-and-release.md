# Install and Release

这一页负责回答一个非常实际的问题：怎么把 `Coding Agent` 从源码能力变成可分发、可安装、可执行的工具入口。

## 1. 先分清三件事

- 构建：把 `ai4j-cli` 打成可运行产物
- 分发：把 jar、脚本、release 资产组织成别人能拿到的形式
- 安装：把启动命令和本地环境接起来，降低使用门槛

这三件事相关，但不是同一层。

## 2. 对应到当前模块

- `ai4j-coding`：提供 runtime
- `ai4j-cli`：提供可执行入口
- release / installer：负责把入口真正交付给用户

所以“发布 Coding Agent”本质上不是只发一个模型配置，而是发一个可运行宿主。

## 3. 当前最稳的落地顺序

建议按下面顺序推进：

1. 先保证源码构建和 fat jar 稳定
2. 再补启动脚本与平台包装
3. 再做 GitHub Release 资产
4. 最后再做更完整的安装体验

这样可以先把“能交付”做实，再逐步把“更好安装”补齐。

## 4. 推荐下一步

1. [Coding Agent Quickstart](/docs/coding-agent/quickstart)
2. [CLI / TUI](/docs/coding-agent/cli-and-tui)
3. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
4. [Command Reference](/docs/coding-agent/command-reference)
