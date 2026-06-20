# CubeSandbox sandbox provider adapter - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001：CubeSandbox 控制面与 envd 数据面分离

- 背景：AI4J Sandbox SPI 需要能创建/连接远端 sandbox，并执行命令。
- 发现：CubeSandbox CubeAPI 提供 `GET /health`、`POST /sandboxes`、`POST /sandboxes/{sandboxID}/connect`、`DELETE /sandboxes/{sandboxID}`；命令执行走 sandbox 数据面的 envd `POST /process.Process/Start`。
- 影响：adapter 需要一个控制面 JSON client 和一个数据面 Connect client；不能只封装 REST JSON。
- 后续：files/Jupyter/snapshot/browser 留给后续任务。

### F-002：envd Connect 协议必须按 5-byte envelope 处理

- 背景：stdout/stderr/exitCode 不是普通 JSON response。
- 发现：官方 Go SDK 使用 `application/connect+json`、`Connect-Protocol-Version: 1`，每帧为 1 byte flags + 4 byte big-endian size，stdout/stderr 是 base64。
- 影响：本地测试必须验证二进制 envelope、error end-stream、partial frame，而不是只 mock Java 方法。
- 后续：如果后续支持 streaming UI，应把帧级事件外抛到 Agent/Coding event stream。

### F-003：ProxyNode IP 直连要保留 sandbox Host

- 背景：本地/内网部署常不能解析 `49983-<sandboxID>.<domain>` wildcard DNS。
- 发现：官方 SDK 在 `CUBE_PROXY_NODE_IP` 存在时改写 dial target，但请求 Host 仍是 sandbox host；CubeSandbox Python/envd/BYOI 文档使用 `49983` 作为 envd 端口，当前 Go SDK `newEnvdRequest` 路径使用 `JupyterPort=49999`，不同模板/部署可能存在端口差异。
- 影响：Java `HttpURLConnection` 不能可靠覆盖 restricted Host header；adapter 使用 Java 8 `Socket` 手写 HTTP/1.1 请求实现 http proxy-node 直连，并新增 `CUBE_ENVD_PORT` / `spec.config.envdPort` 覆盖，默认保留 envd 文档端口 `49983`。
- 后续：`proxyNodeIp + https` 需要 TLS/SNI 设计，当前明确不支持。

### F-004：密钥不能从 SandboxSpec 读取

- 背景：`SandboxSpec` 会被复制、绑定到 session、可能进入 snapshot 或 YAML。
- 发现：如果允许 `spec.config.apiKey` 覆盖 provider key，会把 secret 引入持久化边界。
- 影响：`CubeSandboxConfig.withSpec(...)` 有意忽略 `apiKey`；真实 key 只来自 provider constructor 或 env vars。labels/metadata 对敏感 key 做过滤。
- 后续：后续 provider 也应沿用该密钥边界。

### F-005：Live smoke 当前是 pending-env，不得伪造通过

- 背景：用户要求真实性验证，不要 mock-only claims。
- 发现：当前 shell 中 `AI4J_CUBESANDBOX_LIVE`、`CUBE_API_URL`/`E2B_API_URL`、`CUBE_TEMPLATE_ID` 都不存在。
- 影响：本轮可以提供真实协议级本地 HTTP server 测试和 opt-in live test；不能声明 live CubeSandbox 已通过。
- 后续：拿到 live env 后运行 `CubeSandboxLiveProviderTest` 并记录 sanitized evidence。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Module placement | 放在 `ai4j-agent` 内的 `sandbox.cubesandbox` package | 最小维护成本，复用现有 Sandbox SPI；不新增 module | 新建 `ai4j-sandbox-cubesandbox` module | accepted |
| Dependencies | 不新增第三方依赖 | Java 8 + JDK HTTP/Socket + fastjson2 足够；减少 BOM/发布复杂度 | 引入官方 SDK 或 OkHttp | accepted |
| Command API | `/bin/bash -l -c` | 与 CubeSandbox Go/Python SDK commands 行为一致 | 直接执行 raw command/args | accepted |
| Secret source | provider/env only，不从 `SandboxSpec.config` 读 apiKey | 避免 secret 持久化 | 允许 spec config 覆盖 apiKey | accepted |
| Existing sandbox close | `connect(...)` session close 不销毁远端 | 最小惊讶：连接已有实例不应默认 delete | 所有 close 都 kill | accepted |
| Live verification | opt-in category + env gates | 避免 CI/本地默认跑外部服务；证据真实且可复现 | 默认 live test | accepted |
| envd port | 默认 49983，可通过 `CUBE_ENVD_PORT`/`envdPort` 改为 49999 | CubeSandbox envd/BYOI/files 文档指向 49983；Go SDK 当前 process path 使用 49999，配置化兼容两种部署 | 硬编码 49999 或 49983 | accepted |
| raw socket header safety | CR/LF 拒绝 + host part 校验 | `proxyNodeIp` 路径必须手写 HTTP/1.1；不能让 remote domain/token/spec domain 注入 header | 依赖 HttpURLConnection 覆盖 Host | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否有可用 CubeSandbox live endpoint/template | 当前 shell 未提供 env，记录 pending-env | user/operator | PR 合并前若必须要 L3 live，则补 env 后运行；否则作为 opt-in residual |
| 是否需要 files/Jupyter/snapshot/browser | 本轮不做，docs 明确边界 | product/coordinator | 后续 Sandbox provider expansion 任务 |
| 是否接入 `ai4j-coding` 全量 tool routing | 本轮不做，属于 coding-runtime 后续 | coordinator | CubeSandbox adapter merge 后另开任务 |
