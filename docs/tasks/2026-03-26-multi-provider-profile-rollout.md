# 2026-03-26 多 Provider Profile Rollout

## 目标

- 让 `ai4j-cli` 支持多个 provider profiles
- 密钥与默认 runtime 配置本地持久化
- workspace 可以引用当前 profile，并独立覆盖 model
- 当前 session 内可以直接切换 provider/model

## 依赖文档

- `docs/plans/2026-03-26-multi-provider-profile-design.md`
- `docs/plans/2026-03-26-multi-provider-profile-implementation-plan.md`

## 本轮任务

- [x] 完成配置模型与启动解析
- [x] 完成 runtime rebinding
- [x] 完成 `/providers` `/provider` `/model` 命令
- [x] 完成 `/provider add` `/provider edit` 命令
- [x] 完成 `/provider default <name|clear>` 命令
- [x] 完成 slash completion 扩展
- [x] 完成协议入口收敛为 `chat|responses`
- [x] 完成测试自验证
- [x] 完成最终打包验证

## 小点沉淀

### 小点 1：全局/工作区配置模型落地

- 做了什么：
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliProviderProfile.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliProvidersConfig.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliWorkspaceConfig.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliResolvedProviderConfig.java`
  - 新增 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliProviderConfigManager.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommandOptionsParser.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommandOptions.java`
- 效果：
  - `providers.json` 与 `workspace.json` 已落地
  - CLI 启动时会自动解析 provider/profile/model/apiKey/baseUrl
  - 显式 `--provider` 不会再误继承不匹配 profile 的 credential/baseUrl
- 是否达标：
  - 达标
- 仍有缺陷：
  - 目前还没有单独的“设置默认 profile”命令，默认 profile 主要通过文件写入和第一次保存建立

### 小点 2：session 内 provider/model 热切换

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/JlineCodeCommandRunner.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommand.java`
  - 新增 runtime rebinding：导出 state -> 重建 agent -> 用原 sessionId 恢复
- 效果：
  - `/provider use <name>` 与 `/model <name>` 会立即切换当前 session runtime
  - 当前 memory/process state 不需要通过重启 CLI 才生效
  - `/status` 与 `/session` 开始显示 `profile` / `modelOverride`
- 是否达标：
  - 达标
- 仍有缺陷：
  - 当前实现仍基于 CLI 层 rebinding，不是 `ai4j-coding` 底层的原生 runtime swap API

### 小点 3：命令与 slash palette 扩展

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 扩展 slash 命令：
    - `/providers`
    - `/provider`
    - `/provider use`
    - `/provider save`
    - `/provider remove`
    - `/model`
    - `/model reset`
  - 接入 profile name 候选
- 效果：
  - slash palette 能直接看到 provider/model 相关命令
  - `/provider use ` 可补 profile 名称
  - `/model` 至少支持 `reset` 的候选
- 是否达标：
  - 达标
- 仍有缺陷：
  - `/model` 的自定义 model 名称仍然是自由输入，不是模型列表候选

### 小点 4：测试自验证

- 做了什么：
  - 新增 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliProviderConfigManagerTest.java`
  - 扩展 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandOptionsParserTest.java`
  - 扩展 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
  - 扩展 `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
- 效果：
  - 覆盖了配置读写、parser precedence、slash completion、runtime 切换与 workspace/global 持久化
  - 当前已通过 71 个相关测试
- 是否达标：
  - 达标
- 仍有缺陷：
- 还没有做真实 provider 的联网 smoke test；本轮验证主要以模块测试为主

### 小点 5：默认 profile 管理与真实 Zhipu smoke test

- 做了什么：
  - 扩展 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 扩展 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 扩展 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommand.java`
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
  - 使用真实 `ZHIPU_API_KEY` 做了一次 config-driven smoke test
- 效果：
  - 新增 `/provider default <name|clear>`，可以设置或清空全局默认 profile
  - slash completion 已支持 `/provider default `
  - 用临时 `user.home` + `providers.json` + `workspace.json` 启动 `ai4j-cli`，成功通过配置解析连接到真实 Zhipu coding endpoint，并拿到有效模型回复
- 是否达标：
  - 达标
- 仍有缺陷：
  - 真实 smoke test 目前验证的是“配置解析 + 建连 + 基本回复”，不是长会话稳定性或复杂工具调用

### 小点 6：`/model` 与 provider 子命令列表候选补齐

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
- 效果：
  - `/model` 不再只有 `reset` 候选，还会基于当前 runtime、workspace override 和同 provider 的已保存 profiles 给出模型列表
  - `/provider use` / `/provider default` 在 action 已经补全但还没输入空格时，也能直接进入下一层候选
  - slash palette 的补全链路更接近 Codex/Claude Code 这类“命令 -> 参数”的连续选择体验
- 是否达标：
  - 达标
- 仍有缺陷：
  - 当前模型候选来源是本地 runtime/config，不是远端 provider 的实时官方模型目录

### 小点 7：真实交互式 rebinding smoke

- 做了什么：
  - 使用临时 `user.home` 和临时 workspace 生成两份 Zhipu profiles：
    - `zhipu-main -> glm-4.7`
    - `zhipu-flash -> GLM-4.5-Flash`
  - 通过管道输入真实跑了一轮 `ai4j-cli code` 交互 smoke，覆盖：
    - `/provider`
    - `/provider use zhipu-flash`
    - 多轮记忆连续性验证
    - `/model glm-4.7`
    - `/model reset`
- 效果：
  - `/provider use` 后当前 session runtime 立即切到 `GLM-4.5-Flash`
  - 切换 provider profile 后，模型仍能正确记住切换前用户要求记忆的 `alpha-77`
  - `/model glm-4.7` 后 workspace override 生效
  - `/model reset` 后 effective model 正确回落到 profile model `GLM-4.5-Flash`
- 是否达标：
  - 达标
- 仍有缺陷：
  - 这轮验证的是 CLI 交互链路，不是 TUI 可视层交互链路

### 小点 8：`/provider add|edit` 与显式协议收敛

- 做了什么：
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliProtocol.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CliProviderConfigManager.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodeCommandOptionsParser.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/DefaultCodingCliAgentFactory.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/CodingCliSessionRunner.java`
  - 修改 `ai4j-cli/src/main/java/io/github/lnyocly/ai4j/cli/SlashCommandController.java`
  - 扩展测试：
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliProviderConfigManagerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandOptionsParserTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/DefaultCodingCliAgentFactoryTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/SlashCommandControllerTest.java`
    - `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CodeCommandTest.java`
- 效果：
  - 用户侧不再暴露 `auto` 协议，只接受 `chat` 或 `responses`
  - `--protocol auto` 会直接报错，避免“看起来像协议，实际上只是本地猜测规则”的歧义
  - 旧的 `providers.json` 若仍存 `auto`，加载时会自动归一化成显式协议，并写回规范化配置
  - `/provider add` 支持用显式参数新建 profile，未传 `--protocol` 时会按 provider/baseUrl 推导并保存为显式协议
  - `/provider edit` 支持更新 provider/protocol/model/baseUrl/apiKey，以及 `--clear-*` 清空字段
  - slash completion 的协议候选已同步收敛为 `chat` / `responses`
- 是否达标：
  - 达标
- 仍有缺陷：
  - 协议默认值仍是本地规则推导，不是对远端 provider 能力做在线探测

## 当前验证结果

已执行：

```powershell
cmd /c "mvn -pl ai4j-cli -am -Dtest=CliProviderConfigManagerTest,CodeCommandOptionsParserTest,SlashCommandControllerTest,CodeCommandTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test"
```

结果：

- 通过
- 历史轮次：`Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`

本轮补充验证：

```powershell
cmd /c "mvn -pl ai4j-cli -am -Dtest=CliProviderConfigManagerTest,CodeCommandOptionsParserTest,SlashCommandControllerTest,DefaultCodingCliAgentFactoryTest,CodeCommandTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test"
```

结果：

- 通过
- `Tests run: 88, Failures: 0, Errors: 0, Skipped: 0`

真实 smoke test：

- 基于临时 `user.home` 写入 `providers.json`
- 使用 workspace `workspace.json` 指向 `zhipu-main`
- 启动 `ai4j-cli` one-shot prompt，成功返回有效中文回复

最终打包验证：

```powershell
cmd /c "mvn -pl ai4j-cli -am -DskipTests package"
```

结果：

- 通过
- 产物：`ai4j-cli/target/ai4j-cli-2.0.0-jar-with-dependencies.jar`

## 下一步

- 视需要补：
  - TUI 模式下的 provider/model 热切换 smoke
  - provider/model 的远端模型目录联动与可选在线探测
