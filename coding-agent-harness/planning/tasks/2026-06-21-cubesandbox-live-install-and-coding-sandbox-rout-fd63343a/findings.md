# CubeSandbox live install and coding sandbox routing - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001：CLI 可以对 CubeSandbox 做 live attach，但不应负责 create/auth

- 背景：P4 `/sandbox attach` 原先是 metadata-only，用户希望接入真实 CubeSandbox。
- 发现：`CubeSandboxProvider` 已有 `connect(String sandboxId, SandboxSpec spec)`，语义正好适合“连接已有 sandbox session”；`createSession(...)` 涉及 template、TTL、网络与销毁策略，不适合塞进本轮 CLI attach。
- 影响：本轮只实现 `/sandbox attach cubesandbox|cube <sessionId> [workspaceId]` -> live `CubeSandboxProvider.connect(...)`；CLI 文档明确它不会部署、创建、认证或销毁外部 session。
- 后续：若需要 `/sandbox create/list/destroy/logs`，开独立任务设计 provider lifecycle command。

### F-002：非 Cube provider 必须继续 metadata-only 且不本地回退

- 背景：用户可能 attach Docker/E2B/K8s/company provider，但当前没有 resolver。
- 发现：若没有真实 provider bridge，最安全行为是 `CliAttachedSandboxSession.unsupported(binding)`，执行时报错并包含 `Command was not executed locally`。
- 影响：防止用户误以为命令进入 sandbox 时实际在宿主机执行。
- 后续：第三方 provider 可以通过后续 resolver/plugin 机制接入。

### F-003：本机无法可靠安装并运行 CubeSandbox

- 背景：用户要求“没有 CubeSandbox 就安装”。
- 发现：官方仓库可访问；但当前环境缺 Docker，可用 WSL Linux 发行版不可确认/不可用，CubeSandbox env vars 缺失，当前管理员组为 deny-only。CubeSandbox 这类本地部署需要 Linux/KVM/Docker 或等价云/裸金属底座，不能在当前非提升 Windows 会话中半安装。
- 影响：live smoke 标记为 `pending-env`，`CubeSandboxLiveProviderTest` 在 live profile 下受控 skip 1 个测试。
- 后续：operator 准备 Linux/KVM/Docker 或 WSL2+Docker + CubeAPI/template 后重跑 live smoke。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| D-001 | 新增 `CliSandboxSessionResolver` | 把 provider-specific attach 从 `CodingCliSessionRunner` 中解耦，便于测试和后续 provider 扩展 | 直接在 runner 里 `new CubeSandboxProvider()` | accepted |
| D-002 | CubeSandbox/cube live attach，其它 provider metadata-only | 避免误导，同时让已有 CubeSandbox provider 立即可用 | 全部 metadata-only；或全部失败 | accepted |
| D-003 | CLI 不创建 CubeSandbox | create 涉及模板、网络、鉴权、销毁策略；本轮目标是连接已有 session | `/sandbox create` 一起做 | accepted |
| D-004 | attach/runtime switch 失败必须回滚并关闭新 session | 避免错误 runtime、资源泄漏和安全误导 | 失败后保留 binding 让用户手工 disable | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 真实 CubeSandbox live smoke | pending-env：本机缺 Docker/WSL Linux/Cube env vars | operator | 准备 live CubeSandbox 部署后 |
| `/sandbox create/list/destroy/logs` 是否进入下一任务 | 不在本轮范围，建议独立设计 | maintainer | 下一个 sandbox lifecycle 任务 |
