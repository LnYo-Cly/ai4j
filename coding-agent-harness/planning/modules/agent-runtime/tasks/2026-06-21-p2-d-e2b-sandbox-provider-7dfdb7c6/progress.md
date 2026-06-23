# P2-D E2B sandbox provider - 进度

## 状态：进行中

## 进度记录

### [2026-06-23 20:50] - live protocol probe

- 做了什么：用提供的 E2B key live 实测 control + envd 接口，确认 Connect 协议细节。
  - create `POST /sandboxes` + `X-API-Key` → `{sandboxID, clientID, envdVersion, templateID, alias}`（**无 envdAccessToken**）。
  - envd 端口 **49983**（不是预研笔记里的 3000）；执行 host `https://49983-{sid}.e2b.app`。
  - 请求帧唯一正确形式：`0x00 + BE uint32 len + JSON`，`Content-Type: application/connect+json` + `Authorization: Bearer {apiKey}`。
  - 响应帧：`start(pid)` / `data(stdout|stderr, base64)` / `end` / EOS trailer(flags 0x02)。
  - **exitCode 陷阱**：exit=0 时 `end` 省略数字 exitCode，只给 `"status":"exit status 0"`；非零退出才带数字 exitCode。
- 验证结果：create 201、execute 200、delete 204 全部实测成功；stdout base64 解码正确。
- 下一步：实现 provider。
- 证据：见 `findings.md`（协议实测结论）；command:G:\My_Project\java\ai4j-sdk:live E2B create/execute/delete verified

### [2026-06-23 21:10] - implementation

- 做了什么：在 `ai4j-agent/.../sandbox/e2b/` 新增 7 个源文件：
  - `E2BSandboxConfig`（fromEnvironment + spec.config 覆盖 + buildSandboxHost）。
  - `E2BSandboxClient`（create/delete REST 用 X-API-Key；execute Connect：`buildProcessFrame` + `parseConnectStream` 逐帧读 + base64 解码 + exitCode 字段/status 回退）。
  - `E2BSandboxProvider`（createSession → createSandbox → Session）。
  - `E2BSandboxSession`（命令默认 `sh -c` 包装；stdin 经 `printf '%s' '...' | ( cmd )` 管道；env 合并；close → delete）。
  - `E2BCreateSandboxResponse` / `E2BProcessResult` / `E2BApiException`。
- 验证结果：`mvn -pl ai4j-agent -am compile` BUILD SUCCESS。
- 下一步：写测试。

### [2026-06-23 21:25] - tests

- 做了什么：新增 5 个测试文件。
  - `E2BSandboxClientTest`（6）：纯帧 buildProcessFrame / parseConnectStream，含 chunked 输出、Connect 错误 trailer、exit=0 走 status 解析。
  - `E2BSandboxProviderTest`（4）：本地 HTTP 集成 create→execute→delete，校验请求帧结构与 shell-wrap/stdin 管道；deleteOnClose=false 不删；缺 key 报错。
  - `E2BSandboxConfigTest`（5）：env 默认值、image→template、spec.config 覆盖、sandboxUrl 覆盖。
  - `E2BLocalHttpServer`：支持二进制 Connect 帧的离线 HTTP 桩。
  - `E2BSandboxLiveSmokeTest`（1，@Category Live）：真实 create→execute(exit 0 + 非零 7)→close。
- 验证结果：
  - 修复 1 个 bug：envd 在 exit=0 时省略 exitCode → 加 status 字符串解析回退（带回归单测）。
  - 离线 15 测试全绿；live 烟测 1 测试全绿（9.6s，真实沙箱）。
  - ai4j-agent 全模块回归：148 tests，0 failures / 0 errors / 0 skipped。
  - `git diff --check` 无 whitespace error。
- 证据：
  - command:G:\My_Project\java\ai4j-sdk:15 offline + 1 live E2B tests pass, ai4j-agent 148 tests pass
  - command:G:\My_Project\java\ai4j-sdk:E2B_API_KEY=... mvn -pl ai4j-agent -am -Plive-provider-tests -Dtest=E2BSandboxLiveSmokeTest test -> BUILD SUCCESS

## 残余

- cancel() 返回 false（process.Process/SendSignal 未接）；listArtifacts() 空（filesystem API 未接）；
  create 不发 labels/metadata（字段名未确认）。均记为 v1 范围外，后续任务再接。
- live 烟测用的 key `e2b_c8c7...` 应在合入后轮换（已出现在会话历史里）。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass
- Registry update needed：agent-runtime, T-P2-D, review, feat/e2b-sandbox-provider, updated
- Harness Ledger update needed：closeout 后 `harness governance rebuild --apply`
- 负责人：coordinator
