# Install and Release

这一页讲的是一个非常现实的问题：怎么把 `Coding Agent` 从“仓库里能跑的源码能力”变成“别人拿到就能执行的工具入口”。

## 1. 先分清三件事

- 构建：把 `ai4j-cli` 打成稳定产物
- 分发：把 jar、脚本、压缩包、校验信息组织成 release 资产
- 安装：把启动入口和用户本地环境接起来

这三件事相关，但不是同一层。

## 2. 当前仓库里已经具备什么

当前仓库中可直接交付的主要形态仍然是 fat jar。

构建命令：

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

这样做的意义很直接：

- `ai4j-coding` 提供 runtime
- `ai4j-cli` 提供可执行宿主
- jar 是当前最接近“可交付入口”的统一产物

## 3. 为什么发布这一层不能只谈模型配置

发布 `Coding Agent` 的本质，不是发一份 provider profile，而是发一个可运行宿主。

它至少要交付：

- 启动入口
- 运行时依赖
- 基础配置样例
- 与平台相关的启动脚本

否则外部用户依然只能把它当源码仓来用。

## 4. 推荐的 release 资产结构

如果你要把它做成面对外部用户的正式入口，建议 release 至少包含：

- `ai4j-cli-<version>-jar-with-dependencies.jar`
- `checksums.txt`
- 平台包装压缩包，例如 `windows-x64.zip`、`linux-x64.tar.gz`、`macos-x64.tar.gz`
- `README` / `release-notes`

推荐目录结构：

```text
ai4j-cli-<version>/
  bin/
    ai4j
    ai4j.cmd
  lib/
    ai4j-cli-<version>-jar-with-dependencies.jar
  conf/
    providers.example.json
    workspace.example.json
```

这样更适合长期维持稳定命令入口。

## 5. 安装层真正应该做什么

安装层应该负责：

- 定位 `java`
- 找到 `lib/` 下的 jar
- 暴露稳定命令入口
- 给用户一份最小配置起点

它不应该负责：

- 把业务 provider 写死
- 修改用户项目内容
- 在启动脚本里塞入不可维护的环境特判

稳定的做法是：启动器只做“启动”，配置仍然交给显式文件或命令参数。

## 6. 什么时候需要 repo 级自动化

当你希望：

- release 产物稳定可复现
- 每次 tag 自动生成资产
- 多平台压缩包和 checksum 一起发布

这时就该引入 repo 级 GitHub Actions 作为发布与回归流水线。

它的价值不是“多一个 CI”，而是让安装入口和发布资产变得可重复、可回溯。

## 7. 推荐下一步

1. [CLI and TUI](/docs/coding-agent/cli-and-tui)
2. [Configuration](/docs/coding-agent/configuration)
3. [MCP and ACP](/docs/coding-agent/mcp-and-acp)
4. [Command Reference](/docs/coding-agent/command-reference)

如果你关心“运行起来之后怎么和宿主通信”，下一页建议看 [MCP and ACP](/docs/coding-agent/mcp-and-acp)。
