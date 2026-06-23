# P2-D E2B sandbox provider

## Task ID

`2026-06-21-p2-d-e2b-sandbox-provider-7dfdb7c6`

## 创建日期

2026-06-21

## 一句话结果

agent sandbox SPI 多一个可用的 E2B provider：通过 E2B control API 创建/销毁沙箱，经 Connect
server-streaming `process.Process/Start` 执行命令，行为对齐 Daytona。

## 完成后能得到什么

agent runtime / 用户可以用 `new E2BSandboxProvider().createSession(spec)` 拿到一个能 `execute`
任意 shell 命令、返回 stdout/stderr/exitCode 的隔离沙箱会话，并在 `close()` 时销毁。这把
P2 阶段的沙箱能力从单 provider（Daytona）扩展到双 provider，验证了 SPI 对第二种执行后端
（Connect 流式协议 + base64 输出 + exitCode 在 status 字符串里）的覆盖能力。

## 交付物

- 可见产物：`ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/sandbox/e2b/`（7 个源文件）。
- 修改位置：仅 `ai4j-agent/**` 新增文件；无既有文件改动。
- 验证证据：15 离线测试 + 1 live 烟测（E2B_API_KEY）全绿；ai4j-agent 全模块 148 测试 0 失败。

## 第一眼应该看什么

1. `findings.md` — Connect 协议 live 实测结论（请求/响应帧格式、exitCode 陷阱、auth）。
2. `ai4j-agent/.../sandbox/e2b/E2BSandboxClient.java` — `buildProcessFrame` / `parseConnectStream`。
3. `progress.md` — 实现与验证证据。

## 边界

- 范围内：E2B provider 源码 + 测试；E2B API 实测确认协议。
- 范围外：cancel（SendSignal）、listArtifacts（filesystem）、create labels/metadata；core/CLI/starter 改动。
- 停止条件：协议/auth 不确定时必须 live 实测确认，不得臆测。

## 完成判断

1. E2BSandboxProvider 实现 SandboxProvider，createSession 返回能 execute 的 E2BSandboxSession。
2. Connect 帧编/解码有纯单测覆盖（含 exit=0 走 status 解析）。
3. 本地 HTTP 集成测试覆盖 create→execute→delete 请求结构。
4. live 烟测真实跑通（exit 0 与非零 exit 7 都验证）。
5. ai4j-agent 全模块回归无回归。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中（实现完成，待 PR 评审）
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

开 PR `feat/e2b-sandbox-provider` → main，跑 CI，评审后合并并推进任务到 已完成。
