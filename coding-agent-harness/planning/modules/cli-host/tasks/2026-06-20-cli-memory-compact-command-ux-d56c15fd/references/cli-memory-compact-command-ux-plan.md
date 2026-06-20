# CLI `/memory` + compact command UX 执行方案

> 记录日期：2026-06-20  
> 任务：`MODULES/cli-host/2026-06-20-cli-memory-compact-command-ux-d56c15fd`  
> 主模块：`ai4j-cli`  
> 关联模块：`docs-site`；只有发现 CLI 无法从现有 snapshot 取到必要字段时才扩大到 `ai4j-coding`

## 1. 当前判断

`origin/dev` 上已经有 `/compact`、`/compacts`、`/checkpoint`：

- `SlashCommandController` 已注册 `/compact`、`/compacts`、`/checkpoint`；
- `CodingCliSessionRunner` 已有 `/compact` dispatch、compact result 输出、`/compacts` 历史输出和 `/checkpoint` 输出；
- `AcpSlashCommandSupport` 已暴露 `compacts`、`checkpoint`；
- docs-site 已有 `compact-and-checkpoint.md` 与 `command-reference.md`。

所以本任务不做“compact 基础实现”。真正缺口是：用户没有一个一眼看懂当前 memory/compact 健康状态的 `/memory` 入口，只能在 `/status`、`/session`、`/compacts`、`/checkpoint` 之间拼信息。

## 2. 命令设计

建议新增：

```text
/memory
/memory status
```

其中 `/memory status` 是 `/memory` 的显式别名，不引入复杂子命令树。原因：

- 用户预期 `/memory` 就应该能看当前状态；
- 未来可以扩展 `/memory compact` 或 `/memory inspect`，但当前不能让 MVP 变成 memory 管理器；
- 避免和已有 `/compact` 重叠。

## 3. 输出内容

默认输出只展示摘要，不打印 raw memory item。

建议字段：

```text
memory:
- mode=persistent | memory-only
- items=<count>, estimatedTokens=<n>
- checkpointGoal=<goal or (none)>
- compact=<none|manual|auto>, tokens=<before->after>, strategy=<strategy>
- autoCompactFailures=<n>, breaker=<open|closed|unknown>
- processes active=<n>, restored=<n>
- note=summary only; raw memory and tool output are not printed
```

字段来源优先级：

| 字段 | 首选来源 | 备注 |
| --- | --- | --- |
| mode | `CodeOptions.isNoSession()` | 已在 `/status` 使用 |
| items | `CodingSessionSnapshot.getMemoryItemCount()` | 已在 `/status` 使用 |
| estimatedTokens | `CodingSessionSnapshot.getEstimatedContextTokens()` | 已在 `/status` 使用 |
| checkpointGoal | `CodingSessionSnapshot.getCheckpointGoal()` | 已在 `/status` 使用 |
| last compact | `CodingSessionSnapshot.getLastCompactMode()` 与 token 字段 | 已在 `/session` 使用 |
| process counts | `CodingSessionSnapshot` active/restored process count | 辅助判断 memory 与进程状态 |
| auto compact failure/breaker | 仅在现有 snapshot/export state 可取时展示 | 不为了这个字段强行改 public API |

如果当前 snapshot 不能提供 auto-compact breaker 状态，输出 `breaker=unknown` 或省略该字段，不阻塞 MVP。

## 4. 文件级实现计划

### 4.1 Slash command 注册

文件：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`

- `BUILT_IN_COMMANDS` 增加 `/memory`，描述为 `Show current memory and compact status`；
- `EXECUTABLE_ROOT_COMMANDS` 增加 `/memory`；
- 如实现 `/memory status` 二级补全，则增加一个很小的 `MEMORY_ACTIONS = ["status"]`，只在 `/memory ` 时补全；
- 更新 `SlashCommandControllerTest` 根命令、补全和 palette 行为断言。

### 4.2 CLI runtime dispatch

文件：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/runtime/CodingCliSessionRunner.java`

- 在 `/session`、`/status`、`/compacts` 附近新增 `/memory` dispatch；
- 新增 `printMemory(...)`，复用 `emitOutput(...)` 和 `renderPanel(...)`，保证 classic CLI、main-buffer TUI、append-only TUI 输出一致；
- 不在 `printMemory` 里触发 compact、save、provider call 或 event mutation；
- 只读取 snapshot/export state。

### 4.3 Help / palette / top-level usage

文件：

- `CodingCliSessionRunner.java`
- `CodeCommand.java`
- `CodexStyleBlockFormatter.java`

要求：

- `/help` 与 `ai4j-cli code --help` 都列出 `/memory`；
- TUI palette 增加 `/memory`；
- main-buffer 输出格式化时把 `memory:` 识别为信息块，风格与 `session:`、`checkpoint:`、`compacts:` 一致。

### 4.4 ACP command surface

文件：`ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/acp/AcpSlashCommandSupport.java`

- `COMMANDS` 增加 `memory`；
- `execute(...)` 增加 `renderMemory(context)`；
- ACP 输出保持纯文本摘要，适合 IDE/headless 集成；
- 不暴露 raw memory items。

### 4.5 Docs-site

文件：

- `docs-site/docs/coding-agent/command-reference.md`
- `docs-site/docs/coding-agent/compact-and-checkpoint.md`
- 可选：`docs-site/docs/coding-agent/session-runtime.md`

要写清：

| 命令 | 定位 |
| --- | --- |
| `/memory` | 看当前 memory/compact 健康概览 |
| `/compact` | 主动压缩当前 session memory |
| `/compacts` | 看历史 compact 事件和诊断字段 |
| `/checkpoint` | 看结构化 checkpoint 内容 |
| `/status` | 看运行状态大盘 |
| `/session` | 看 session 元信息和 store 状态 |

## 5. 测试计划

Targeted tests：

```powershell
mvn -pl ai4j-cli -am "-Dtest=SlashCommandControllerTest,CodeCommandTest,AcpSlashCommandSupportTest,CodexStyleBlockFormatterTest" -DskipTests=false -DfailIfNoTests=false test
```

Broad CLI tests：

```powershell
mvn -pl ai4j-cli -am -DskipTests=false test
```

Docs build：

```powershell
npm --prefix docs-site run build
```

Harness / static：

```powershell
git diff --check
npx --yes coding-agent-harness status --json .
```

## 6. Regression SSoT 判断

如果只是把 `/memory` 纳入现有 CLI command surface regression，可扩展现有 CLI host gate；如果 `Regression-SSoT.md` 当前没有覆盖 slash command parity，则新增或更新一条：

- CLI slash command surface：root completion、runtime dispatch、ACP available commands、docs reference 必须一致；
- Evidence Depth：L1 targeted tests + docs build；
- Cadence：触碰 `SlashCommandController`、`CodingCliSessionRunner`、`AcpSlashCommandSupport`、`docs-site/docs/coding-agent/command-reference.md` 时触发。

## 7. 不采用方案

| 方案 | 不采用原因 |
| --- | --- |
| 直接增强 `/status`，不加 `/memory` | 用户想看 memory/compact 时仍然需要在大盘状态里找字段，不够一等入口 |
| `/memory inspect` 打印 raw memory | 容易泄露用户 prompt、工具输出、路径和敏感业务上下文，不适合默认命令 |
| 把 `/memory` 改成 memory 编辑器 | 当前目标是诊断，不是状态编辑；编辑器会引入权限、持久化和审计复杂度 |
| 为 auto-compact breaker 强改 `ai4j-coding` public API | 这会扩大任务范围；MVP 可展示 unknown 或从已有 state 读取 |

## 8. 下一步命令

```powershell
git fetch origin dev
git worktree add -b feature/cli-memory-compact-ux .worktrees/feature/cli-memory-compact-ux origin/dev
cd .worktrees/feature/cli-memory-compact-ux
npx --yes coding-agent-harness status --json .
```
