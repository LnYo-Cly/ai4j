# 2026-03-26 多 Provider Profile 实施计划

- 状态：In Progress
- 优先级：P0
- 依赖设计：`docs/plans/2026-03-26-multi-provider-profile-design.md`
- 目标模块：`ai4j-cli`

## 1. 实施目标

为 `ai4j-cli` 增加多 provider profile、本地持久化和运行时切换能力，并保证：

- 启动解析可自动吃到本地配置
- slash palette 与 in-session commands 同步可用
- 切换 provider/model 时当前 session 立即生效

## 2. 阶段拆分

### Phase 1：配置模型与启动解析

目标：

- 建立 global/workspace 配置模型
- 接入 parser 解析优先级

主要改动：

- `CliProviderProfile`
- `CliProvidersConfig`
- `CliWorkspaceConfig`
- `CliResolvedProviderConfig`
- `CliProviderConfigManager`
- `CodeCommandOptionsParser`
- `CodeCommandOptions`

验收：

- 无 CLI 参数时可从配置解析 provider/model/apiKey/baseUrl
- CLI 显式参数优先级更高
- 不匹配 provider 的 profile 不会错误复用其 credential/baseUrl

### Phase 2：session 内 runtime rebinding

目标：

- 让当前 session 可以在不中断 memory 的前提下切换 provider/model

主要改动：

- `CodingCliSessionRunner`
- `JlineCodeCommandRunner`
- `CodeCommand`

验收：

- `/provider use <name>` 后当前 session 继续可用
- `/model <name>` / `/model reset` 后当前 session 继续可用
- `/status` / `/session` 输出会更新

### Phase 3：命令与 slash completion

目标：

- 接入新的 in-session commands 与 slash candidates

主要改动：

- `CodingCliSessionRunner`
- `SlashCommandController`

验收：

- `/providers`、`/provider`、`/model` 可执行
- `/provider default <name|clear>` 可执行
- slash palette 可看到对应命令
- `/provider use ` 与 `/provider default ` 可补 profile 名称

### Phase 4：文档与自验证沉淀

目标：

- 补设计文档、实施计划和 rollout 记录
- 跑针对性测试与打包验证

主要改动：

- `docs/plans/...`
- `docs/tasks/...`
- parser/slash/command tests

验收：

- 单元和集成测试通过
- 至少完成 `ai4j-cli` 模块级构建验证

## 3. 当前进度

- [x] Phase 1：配置模型与启动解析
- [x] Phase 2：session 内 runtime rebinding
- [x] Phase 3：命令与 slash completion
- [x] Phase 4：文档与最终打包沉淀

## 4. 风险点

- 当前 runtime 切换通过“导出 state + 新 agent 绑定 + 原 sessionId 复建”实现，如果后续 `CodingSessionState` 结构发生变化，需要同步验证兼容性
- `/provider save <name>` 允许覆盖同名 profile，属于有意设计，但文档需要明确
- 当前 `/model` 已支持基于本地 runtime/config 的列表候选，但还没有接远端 provider 的实时模型目录

## 5. 验证命令

```powershell
cmd /c "mvn -pl ai4j-cli -am -Dtest=CliProviderConfigManagerTest,CodeCommandOptionsParserTest,SlashCommandControllerTest,CodeCommandTest -Dsurefire.failIfNoSpecifiedTests=false -DskipTests=false test"
```

已完成最终打包验证：

```powershell
cmd /c "mvn -pl ai4j-cli -am -DskipTests package"
```
