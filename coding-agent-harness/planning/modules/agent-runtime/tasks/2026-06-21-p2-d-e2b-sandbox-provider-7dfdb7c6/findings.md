# P2-D E2B sandbox provider - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### F-001 envd 端口与 host（预研错误已修正）

- 背景：预研笔记写 envd 端口 3000，需实测确认。
- 发现：实测 **49983**。E2B python-sdk `connection_config.py`：`envd_port = 49983`，
  `get_host = "{port}-{sandboxID}.{domain}"`。执行 host `https://49983-{sandboxID}.e2b.app`。
- 影响：实现以 49983 为默认；记忆已修正。
- 后续：无。

### F-002 create 响应不含 envdAccessToken

- 背景：预研笔记写响应含 envdAccessToken。
- 发现：实测此 key/流程**不含**；响应只有 `{sandboxID, clientID, envdVersion, templateID, alias}`。
  执行 host 用 API key 作 Bearer 即可（实测通过）。
- 影响：执行 auth 用 `Authorization: Bearer {apiKey}`；保留 envdAccessToken 前向兼容。
- 后续：无。

### F-003 鉴权分离

- 背景：control API 与执行 host 是否同一鉴权。
- 发现：control 用 `X-API-Key`（Bearer 单用会间歇 401）；执行 host 用 `Authorization: Bearer {apiKey}`。
- 影响：Client 对两类请求分别设 header。
- 后续：无。

### F-004 Connect 请求帧唯一正确形式

- 背景：`process.Process/Start` 是 Connect server-streaming，请求帧格式需实测。
- 发现：请求体必须是**单个 envelope** `0x00 + BE uint32 len + JSON`，CT `application/connect+json`。
  其他形式全报错（flags=EOS / 多帧 / 裸 JSON）。
- 影响：`buildProcessFrame` 固定此格式。
- 后续：无。

### F-005 Connect 响应帧与 exitCode 陷阱（关键）

- 背景：响应事件结构与 exitCode 取值需实测。
- 发现：响应逐帧 envelope。`start(pid)` / `data(stdout|stderr, base64)` / `end` / EOS trailer(flags 0x02, `{}`)。
  **exit=0 时 `end` 省略数字 exitCode，只给 `"status":"exit status 0"`**；非零才带 exitCode 数字。
  错误 trailer = `flags=2` 载荷 `{"error":{...}}`。
- 影响：`parseConnectStream` exitCode 字段优先、否则从 status 解析尾部整数（带回归单测）。
- 后续：无。

### F-006 探测环境陷阱

- 背景：初期用 curl + python 混合实测，间歇出现 "zero messages"。
- 发现：native Python `/tmp/` = `C:\tmp\`，MSYS bash/curl `/tmp/` 不同挂载点 → curl 读不到 python 写的文件 → 发空 body。
  改全用 python urllib 实测后协议完全确定。是环境问题，非协议问题。
- 影响：实测结论可信。
- 后续：无。

### F-007 SPI 对齐结论

- 背景：E2B 与 Daytona 差异是否需要改 SPI。
- 发现：E2B 无 attach/start 轮询（create 后立即可用）、无 toolbox proxy、执行用 Connect 流式。
  映射到同一 SandboxSession/SandboxResult 即可，**无需改 SPI**。
- 影响：纯新增，零既有文件改动。
- 后续：无。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| 命令执行方式 | 默认 `sh -c` 包装 | 匹配 Daytona shell 语义，支持管道/重定向/多语句 | 直接 exec（tokenize） | accepted（提供 useShellWrap=false 逃生口） |
| stdin 支持 | `printf '%s' '<stdin>' \| ( cmd )` 管道 | 避免 Connect bidi 流式 stdin 的额外复杂度，保留退出码 | Connect 客户端流式 stdin | accepted（v1） |
| exit=0 取值 | status 字符串解析回退 | envd 实测在 exit=0 省略数字 exitCode | 假定 exitCode 总在 | accepted |
| cancel/listArtifacts | 返回 false / 空 | v1 范围外，SendSignal/filesystem 未接 | 接 SendSignal/filesystem API | deferred |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| create 是否支持 labels/metadata 字段 | 未确认，v1 不发 | coordinator | 后续接 filesystem/labels 时确认 |
