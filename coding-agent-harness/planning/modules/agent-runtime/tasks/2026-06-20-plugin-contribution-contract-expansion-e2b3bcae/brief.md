# Plugin contribution contract expansion

## Task ID

`2026-06-20-plugin-contribution-contract-expansion-e2b3bcae`

## 创建日期

2026-06-20

## 一句话结果

为 `ai4j-extension-api` 增加 manifest-level 插件贡献契约，让第三方插件可以声明 Tool、CLI Command、Memory、Compact、Sandbox Provider、Remote Runner Provider 等贡献项，并在 inspect / activation / validator / docs-site 中可见。

## 完成后能得到什么

完成后，插件作者不需要等待 AI4J 为每一种 provider 都实现 runtime registry，就可以在 `ExtensionManifest` 中稳定声明具体贡献项。使用者和宿主应用可以通过 `ExtensionRegistry.inspectRuntime(...)` 与 `activationPlan(...)` 查看插件影响面，判断是否启用、暴露、绑定或授予权限。docs-site 也会明确写清 capability 与 contribution 的区别，以及 sandbox / runner 这类 provider-style 插件为什么只是声明元数据、不会自动读取密钥或创建真实云资源。

## 交付物

- 可见产物：`ExtensionContribution`、`ExtensionContributionType`、manifest contribution builder API、inspection / activation / validator 投影、Ask User 示例贡献元数据、docs-site Plugin Contribution Contract 页面。
- 修改位置：`ai4j-extension-api/`、`ai4j-plugin-ask-user/`、`docs-site/docs/agent/`、`docs-site/sidebars.ts`、本 Harness task package。
- 验证证据：extension-api tests、ask-user plugin tests、agent extension targeted tests、docs-site build、`git diff --check`、Harness status。

## 第一眼应该看什么

1. `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionContribution.java`
2. `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/ExtensionContributionType.java`
3. `docs-site/docs/agent/plugin-contribution-contract.md`
4. `progress.md` 的验证命令记录
5. `review.md` 的 self-review 和残余风险

## 边界

- 范围内：manifest-level contribution metadata、inspection/activation/validation 投影、官方 ask-user 示例、docs-site 技术文档。
- 范围外：不实现真实 MemoryStore/SandboxProvider/RunnerProvider 注册表，不改 CLI 安装/市场协议，不接入任何真实云 sandbox 或 provider token。
- 停止条件：如果需要新增 Maven 模块、引入真实 provider 实现、改变现有 enable/expose 安全语义，必须另开任务。

## 完成判断

- [x] 插件 manifest 可以声明具体 contribution metadata。
- [x] inspection / activation plan 可以展示 contribution。
- [x] validator 可以检查 metadata-only capability 与 contribution 的一致性。
- [x] ask-user 官方插件有 contribution 示例。
- [x] docs-site 解释 capability / contribution / provider-style binding 边界。
- [ ] 本任务 review / PR / merge 完成。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`，并提交 feature branch。

## 当前下一步

提交实现 commit，运行 Harness status，随后推送分支并创建 PR 到 `dev`。
