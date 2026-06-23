# P2-C Daytona sandbox provider - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001：显式 `createIfMissing=false` 不能被普通 `SandboxSpec` 覆盖

- 背景：`DaytonaSandboxProvider(DaytonaSandboxConfig)` 支持显式 config，同时 `createSession(spec)` 还会应用 `SandboxSpec` 覆盖项。
- 发现：如果 merge 时把普通 spec 当成完整 config，未显式设置的 spec 默认值可能把显式 `createIfMissing=false` 变回默认 `true`。
- 影响：attach miss 时可能误创建 sandbox，与用户预期不符。
- 后续：已通过 `DaytonaSandboxConfig.withSpecOverrides(spec)` 保留显式值，并增加 `explicitCreateIfMissingFalseShouldNotBeOverriddenByPlainSpec` 覆盖。

### F-002：Daytona live smoke 应只要求 API key，API URL 可以使用 SDK 默认值

- 背景：`DaytonaSandboxConfig` 已定义 `DEFAULT_API_URL`。
- 发现：live smoke 早期同时要求 `DAYTONA_API_KEY` 和 `DAYTONA_API_URL`，会让只配置 key 的用户无法直接运行默认 Daytona smoke。
- 影响：与“一个 key 即可测试默认 Daytona 接入”的使用体验不一致。
- 后续：已调整 `DaytonaSandboxLiveSmokeTest` 只要求 `DAYTONA_API_KEY`，并用 deterministic config test 覆盖缺省 URL 行为。

### F-003：真实 provider 证据要和本地回归分层

- 背景：Daytona 是真实远端 sandbox，不能成为默认 Maven baseline 的隐式外部依赖。
- 发现：默认 surefire 通过 `LiveProviderTest` category 排除 live smoke；`-P live-provider-tests` 才会执行。
- 影响：RG-002 仍保持 deterministic local baseline；真实 sandbox 可用性进入 LV-004 opt-in gate。
- 后续：Regression SSoT / Cadence Ledger 已记录 LV-004 和 SRB-058。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Provider 放置位置 | `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/daytona` | Daytona provider 实现的是 agent sandbox SPI，不依赖 CLI/TUI 或 Spring wiring | 放到 `ai4j-coding` 或独立 Maven | accepted |
| HTTP 客户端 | Java 8 `HttpURLConnection` + fastjson2 | 避免新增运行时依赖，保持 Java 8 module 边界 | 引入 OkHttp/Daytona SDK | accepted |
| Secret 来源 | env 优先，`SandboxSpec.config` 兼容但 docs 推荐 env | 避免 token 进入 Blueprint、snapshot、日志或文档示例 | 在 YAML / fixture 写 key | accepted |
| Live 验证 | `@Category(LiveProviderTest.class)` + `-P live-provider-tests` | 保持默认回归 deterministic，同时提供真实 smoke | 默认 test 阶段直接访问 Daytona | accepted |
| Close 语义 | `deleteOnClose=false` 默认不删除 | attach/创建后的 sandbox 默认保守保留，避免误删 | close 默认删除 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| `cancel(...)` 与 artifact 列表 | 本轮不实现，已作为 residual；后续应跟 Daytona process/artifact API 单独接入 | coordinator | P4 `/sandbox` 或后续 provider hardening |
| provider registry / plugin contribution | 本轮直接 new provider；自动发现和第三方贡献 provider 属于后续 extension/provider registry 任务 | coordinator | 下一个插件生态切片 |
| live smoke 复跑 | 当前 shell 没有 `DAYTONA_API_KEY`，不在命令日志里重放密钥；保留已通过的 sanitized surefire 证据 | coordinator | 用户重新设置 env 后可复跑 |
