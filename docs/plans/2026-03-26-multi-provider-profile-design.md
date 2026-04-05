# 2026-03-26 多 Provider Profile 设计

## 背景

当前 `ai4j-cli` 已经具备基本的 coding agent CLI/TUI 能力，但 provider/model 仍主要依赖启动参数或环境变量：

- 每次启动都要手动带 `--provider --model --api-key --base-url`
- workspace 无法声明“当前项目默认使用哪个 provider profile”
- 运行中的 session 无法像 `opencode / pi-sdk / Codex` 那样直接切换 provider 或 model
- 密钥无法稳定沉淀到本地配置

目标已经明确：CLI 需要支持多 provider profile、本地持久化，以及运行时切换。

## 目标

- 支持多个 provider profile 的全局保存
- 支持 workspace 级“当前活动 profile + model override”
- 启动时自动从配置解析 provider / protocol / model / apiKey / baseUrl
- 在交互 session 内支持：
  - `/providers`
  - `/provider`
  - `/provider use <name>`
  - `/provider save <name>`
  - `/provider remove <name>`
  - `/model`
  - `/model <name>`
  - `/model reset`
- 切换后当前 session 立即生效，而不是必须重启 CLI

## 非目标

- 本轮不做 OAuth / browser login
- 本轮不接第三方 secret manager
- 本轮不做 provider marketplace 或远程 profile 同步
- 本轮不重写 `ai4j-coding` session/state 结构

## 配置模型

### 全局配置

位置：

- `%USERPROFILE%\\.ai4j\\providers.json`

职责：

- 保存 provider profiles
- 保存全局默认 profile

结构：

```json
{
  "defaultProfile": "zhipu-main",
  "profiles": {
    "zhipu-main": {
      "provider": "zhipu",
      "protocol": "chat",
      "model": "glm-4.7",
      "baseUrl": "https://open.bigmodel.cn/api/coding/paas/v4",
      "apiKey": "..."
    }
  }
}
```

### Workspace 配置

位置：

- `<workspace>/.ai4j/workspace.json`

职责：

- 保存当前 workspace 活动 profile
- 保存 workspace 级 model override

结构：

```json
{
  "activeProfile": "zhipu-main",
  "modelOverride": "glm-4.7-plus"
}
```

## 解析优先级

运行时解析优先级如下：

1. CLI 显式参数
2. workspace `modelOverride`
3. workspace `activeProfile`
4. global `defaultProfile`
5. 旧 env / properties fallback

补充约束：

- 如果显式 `--provider` 与 profile 的 provider 不一致，则不能继续继承该 profile 的 `apiKey/baseUrl/model`，避免把 `zhipu` 配置错误带到 `openai`
- `modelOverride` 只影响 model，不改变 provider/profile 绑定

## 运行时切换设计

### 为什么不能只改字段

当前 `CodingCliSessionRunner` 启动后持有固定的：

- `CodingAgent`
- `CliProtocol`
- `CodeCommandOptions`

这些对象在旧设计中是“启动后不变”的。要做到 session 内切换，必须重建 runtime binding。

### 切换流程

1. 导出当前 `CodingSessionState`
2. 基于新配置重新解析 `CodeCommandOptions`
3. 调用 `CodingCliAgentFactory.prepare(...)` 重新构建 agent/protocol
4. 使用原 `sessionId` + 导出的 state 创建新 `CodingSession`
5. 用新 provider/protocol/model 创建新的 `ManagedCodingSession`
6. 替换 runner 当前绑定，并关闭旧 session

这样可以做到：

- 会话记忆保留
- provider/model 切换即时生效
- session lineage 不被打断

## 命令语义

### `/providers`

- 列出所有已保存 profile
- 标记 `[active]` 与 `[default]`

### `/provider`

- 显示当前：
  - `activeProfile`
  - `defaultProfile`
  - `effectiveProfile`
  - provider/protocol/model/baseUrl
  - apiKey 是否存在（只做掩码显示）

### `/provider use <name>`

- 更新 workspace `activeProfile`
- 保留当前 `modelOverride`
- 重建当前 session runtime

### `/provider save <name>`

- 将当前运行中的 provider/protocol/model/baseUrl/apiKey 保存为 profile
- 如果还没有默认 profile，则自动设为默认

### `/provider remove <name>`

- 删除全局 profile
- 如果删除的是默认 profile，则清空默认
- 如果删除的是 workspace 当前 active profile，则清空 workspace 引用

### `/provider default <name|clear>`

- 设置或清空全局默认 profile
- 只影响后续没有 workspace `activeProfile` 的解析
- 不强制切换当前 workspace 的 active profile

### `/model`

- 显示当前 effective model、workspace override、当前 profile

### `/model <name>`

- 写入 workspace `modelOverride`
- 立刻重建当前 session runtime

### `/model reset`

- 清空 workspace `modelOverride`
- 回退到 profile model
- 立刻重建当前 session runtime

## Slash Completion

slash palette 需要同步支持：

- `/providers`
- `/provider`
- `/provider use`
- `/provider save`
- `/provider default`
- `/provider remove`
- profile 名称候选
- `/model`
- `/model reset`

## 测试策略

### 单元测试

- provider/workspace 配置读写
- parser 对 config/env/CLI precedence 的解析
- provider override 不继承不匹配 profile 的密钥与 baseUrl
- slash completion 对新命令和 profile 候选的补全

### 集成测试

- `/provider save/use/remove`
- `/model <name>` / `/model reset`
- 切换后 `/status` 输出更新
- workspace/global 配置文件写入正确

## 当前结论

本方案保持现有 `ai4j-coding` session/state 不变，只在 `ai4j-cli` 层新增 profile 配置管理、runtime rebinding 和 slash/command 扩展，变更面可控，且足以满足当前“多 provider + 本地配置 + 运行时切换”的目标。
