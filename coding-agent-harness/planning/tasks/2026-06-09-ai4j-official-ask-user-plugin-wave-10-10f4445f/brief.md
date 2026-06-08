# AI4J official ask-user plugin wave 10

## Task ID

`2026-06-09-ai4j-official-ask-user-plugin-wave-10-10f4445f`

## 创建日期

2026-06-09

## 一句话结果

新增官方 `ai4j-plugin-ask-user` 插件包，让 AI4J 插件生态有一个可编译、可测试、可文档化的 host-mediated 用户提问样板。

## 完成后能得到什么

用户可以通过 Maven / BOM 引入 `ai4j-plugin-ask-user`，在 classpath 上发现并启用 `ask-user` 插件，再按 `enable -> exposeTool` 门禁把 `ask_user` 暴露给 Agent / Coding Agent。插件作者可以直接参考它的 Maven 模块、`Ai4jExtension` 实现、`META-INF/services`、tool、command、Skill、Prompt、validator 测试和 ServiceLoader 测试。这个任务同时把根 POM、BOM、README、docs-site、CI matrix、Regression SSoT、Cadence Ledger 与 harness module registry 同步到同一事实。

## 交付物

- 可见产物：`ai4j-plugin-ask-user/` 官方插件模块，docs-site 官方插件页，README / BOM / CI / harness 注册。
- 修改位置：根 POM、`ai4j-bom`、`ai4j-plugin-ask-user`、README、docs-site、`.github/workflows/java-regression.yml`、`coding-agent-harness/`、`docs/05-TEST-QA`、`docs/09-PLANNING/Feature-SSoT.md`、`docs/11-REFERENCE`。
- 验证证据：`mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test`、`mvn -DskipTests package`、docs-site typecheck/build、`git diff --check`、`npx --yes coding-agent-harness status --json .`。

## 第一眼应该看什么

先读 `ai4j-plugin-ask-user/src/main/java/io/github/lnyocly/ai4j/plugin/askuser/AskUserExtension.java`、`docs-site/docs/core-sdk/extension/ask-user-plugin.md`、`progress.md` 和 `review.md`。

## 边界

- 范围内：新增官方 ask-user 插件模块；同步根 POM、BOM、docs-site、README、CI matrix、Regression SSoT、Cadence Ledger、harness context 和 module registry。
- 范围外：远程插件市场、运行时 jar 热加载、CLI 依赖安装、真实 UI 渲染、stdin 阻塞、答案持久化、Agent 恢复执行协议。
- 停止条件：插件需要修改 `ai4j-extension-api` 公共合同、Agent/Coding Agent runtime 适配器、CLI 命令实现，或验证显示共享构建失败且不能在当前范围内解释。

## 完成判断

- 根 POM、BOM 和 CI matrix 都包含 `ai4j-plugin-ask-user`。
- 插件 manifest、ServiceLoader、validator、tool、command、Skill / Prompt 资源都有本地测试覆盖。
- docs-site 和 README 明确说明 host-mediated 边界，不承诺 UI、阻塞或远程市场能力。
- Regression SSoT / Cadence Ledger / harness module registry 已同步新增模块和 RG-011 gate。
- 目标 Maven、docs-site、diff 和 harness status 检查通过，或残余有 owner / action / status。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

补齐任务包真实记录，运行完整验证，然后提交并推送。
