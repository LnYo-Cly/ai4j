# CLI `/permissions` command UX 执行方案

> 记录日期：2026-06-20
> 任务：`MODULES/cli-host/2026-06-20-cli-permissions-command-ux-7bbbc71d`
> 主模块：`ai4j-cli`

## 1. 背景

`origin/dev` 已有 `--approval <auto|safe|manual>`、CLI/TUI inline approval、ACP `session/request_permission`、agent runtime permission policy 文档，但 CLI 交互内没有一等 `/permissions` 命令。

这会导致用户在 TUI 中无法快速确认当前 approval 策略与 tool permission 边界，只能从启动参数、文档或 ACP 行为推断。

## 2. 目标

新增只读 `/permissions` 诊断命令：

```text
/permissions
/permissions status
```

输出当前 CLI session 的 approval mode、配置来源提示、tool-call gate 行为、ACP gateway 关系、sandbox 关系和安全边界。

## 3. 非目标

- 不新增权限编辑器；
- 不在运行中切换 `--approval`；
- 不读取或打印 raw tool input；
- 不打印 provider key、baseUrl credential、prompt 或工具输出全文；
- 不改 `ai4j-agent` permission policy API；
- 不改 `ai4j-coding` tool runtime。

## 4. 输出设计

示例：

```text
permissions:
- approvalMode=safe
- source=--approval / AI4J_APPROVAL / ai4j.approval / default(auto)
- toolGate=safe prompts before high-impact local tools; manual prompts before every tool call; auto delegates without prompt
- acp=manual/safe approval uses session/request_permission when running through ACP
- sandbox=sandbox changes where tools execute, not whether they are allowed
- note=summary only; raw tool input, prompts, provider keys, and tool output are not printed
```

`/permissions status` 是别名。其它子命令返回明确错误。

## 5. 实现点

| 文件 | 改动 |
| --- | --- |
| `SlashCommandController.java` | 注册 `/permissions`，补全 `status`，加入 executable root。 |
| `CodingCliSessionRunner.java` | 增加 dispatch、help 行、`printPermissions` / `renderPermissionsOutput`。 |
| `AcpSlashCommandSupport.java` | 增加 ACP available command 和 `renderPermissions`。 |
| `CodeCommand.java` | 顶层 help 增加 `/permissions [status]`。 |
| `CodexStyleBlockFormatter.java` | 如 formatter 需要标题识别，加入 `permissions:` 信息块。 |
| docs-site | 命令参考和 approval/tools 文档补充 `/permissions` 定位。 |

## 6. 测试计划

```powershell
mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodeCommandTest,AcpSlashCommandSupportTest,CodexStyleBlockFormatterTest" -DskipTests=false -DfailIfNoTests=false test
mvn -pl ai4j-cli -am -DskipTests=false test
npm --prefix docs-site run build
git diff --check
npx --yes coding-agent-harness status --json .
```

## 7. Regression 判断

属于 RG-004 CLI slash command parity。若同步 docs-site，则触发 RG-008。当前 RG-004 / Cadence 已包含 fixed slash commands 的 root completion、runtime dispatch、ACP command surface、help/palette、formatter、docs reference parity，本任务沿用并在 SRB 中补证据。
