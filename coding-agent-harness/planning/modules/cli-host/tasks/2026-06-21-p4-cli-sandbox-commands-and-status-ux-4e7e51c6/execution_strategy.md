# P4 CLI sandbox commands and status UX - 执行策略

## 策略摘要

采用 coordinator 单线实现 + 后置审查。原因是本任务核心文件集中在 `CodingCliSessionRunner`、`SlashCommandController`、`CodingCliAgentFactory`，并行写入容易冲突；可并行的部分主要是只读 review 与验证。

## 实现顺序

1. 修复 Harness task package，占位全部替换成当前设计事实。
2. 新增 CLI sandbox model/controller，优先保持纯 Java 8、无额外依赖。
3. 扩展 factory overload，让已有 `CodingAgentBuilder.sandbox(SandboxSession)` 成为唯一 runtime 接入点。
4. 在 `CodingCliSessionRunner` 接入 `/sandbox` dispatch、status 和 runtime rebind。
5. 更新 SlashCommandController built-ins/completion 与 command palette/help。
6. 写 targeted tests；先跑窄测试，再跑 `ai4j-cli -am` 模块测试。
7. 更新 Regression SSoT/Cadence Ledger、review、walkthrough、lesson decision。

## 安全策略

- 不提供 `/sandbox ... --api-key`；凭证只能来自 env/local config 注入。
- 输出中只显示 provider/session/status/spec 摘要，不显示 API key、Authorization header、完整 env。
- enable/attach 失败时保留原 agent/runtime/sandbox state，不 silent fallback。
- disable 时 close 当前 sandbox session，然后回到 direct-host runtime；close 失败要给出 diagnostic。

## 测试策略

- Unit：解析 `/sandbox` 参数、status rendering、no-secret binding。
- Integration-ish：fake `CodingCliAgentFactory` 验证 `switchSessionRuntime` 收到 live `SandboxSession`。
- Regression：`SlashCommandControllerTest` 覆盖 `/sandbox` palette/completion。
- Live：只有 env 中存在 Daytona 凭证时才运行 live-provider smoke；缺失时记录 opt-in residual，不阻断本地 baseline。

## 不使用写入型 subagent 的原因

CLI shared files 写入面重叠，且用户要求快速做完。后续如果 agent 槽位可用，使用只读 review subagent 做对抗性审查。

## 回滚策略

所有修改保持在窄范围。若 factory overload 影响现有测试，回滚到不改 public interface 的 CLI-local prepared factory wrapper；若 Daytona live provider 不可用，保留 deterministic local baseline 并记录 live residual。
