# plugin ecosystem hardening fixes

## Task ID

`2026-07-05-plugin-ecosystem-hardening-fixes-bcef4a36`

## 创建日期

2026-07-05

## 一句话结果

修复插件生态 review 中确认的版本、可见性、资源隔离、payload 尺寸和权限文档问题，让 ask-user 官方插件和 CLI/plugin runtime 与 2.4.0 当前主线一致。

## 完成后能得到什么

用户和下一轮 agent 可以直接拿到一个可合并的插件生态 hardening 分支：官方 ask-user 插件声明 2.4.0 版本并限制超大输入；CLI runtime inspect 能展示 lifecycle hooks；插件 Skill/Prompt 资源读取默认不会从宿主 classloader 偶然兜底；docs-site 明确 manifest permissions 只是宿主策略元数据。验证证据覆盖 extension-api、ask-user、CLI、coding resource、monorepo package 和 docs-site。

## 交付物

- 可见产物：代码修复、docs-site 文档更新、Regression SSoT / Cadence Ledger 更新、任务 walkthrough。
- 修改位置：`ai4j-extension-api/`、`ai4j-plugin-ask-user/`、`ai4j-cli/`、`ai4j-coding/`、`docs-site/`、`docs/05-TEST-QA/`、本任务目录。
- 验证证据：`progress.md` E-002、`docs/05-TEST-QA/Cadence-Ledger.md` SRB-060。

## 第一眼应该看什么

先看 `walkthrough.md` 的范围/验证表，再看 `review.md` 的无重要发现声明和 `docs/05-TEST-QA/Cadence-Ledger.md` 的 SRB-060 证据。

## 边界

- 范围内：前次 review 点名的插件生态 polish/hardening 问题、相应回归和 docs-site 说明。
- 范围外：新增插件市场协议、真实插件安装器、生命周期 hook 可变拦截、完整权限引擎、非插件相关 API 设计。
- 停止条件：若验证暴露跨模块行为回归，先收窄到 owning module 修复；若需要真实发布/远端凭据，回到用户确认。

## 完成判断

- [x] 版本引用与 ask-user manifest/scaffold/docs 对齐到 2.4.0。
- [x] CLI runtime inspect 输出 lifecycle hooks 并有测试覆盖。
- [x] 插件资源 strict read 不从宿主 classloader 兜底，并更新 validator/CLI/coding 消费侧。
- [x] ask_user 超大 argumentsRaw 被截断并带 `argumentsTruncated` 标记。
- [x] Maven、docs-site、Regression SSoT / Cadence 证据已更新。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`、`walkthrough.md`
- 完成条件：验证证据必须记录到 `progress.md`，并在 closeout 前提交 review。

## 当前下一步

提交 review/closeout，创建最终实现提交，然后按远端检查情况决定 PR/合并/清理。
