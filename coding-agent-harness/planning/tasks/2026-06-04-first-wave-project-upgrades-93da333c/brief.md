# first wave project upgrades

## Task ID

`2026-06-04-first-wave-project-upgrades-93da333c`

## 创建日期

2026-06-04

## 一句话结果

完成第一波低风险工程升级：移除 release POM 中的本机 GPG 绝对路径，补齐本地生成输出的 Git 边界，并留下可复查的验证与审查材料。

## 完成后能得到什么

本任务让 `ai4j-sdk` 的 release 配置不再依赖某一台 Windows 机器上的 `D:\Develop\DevelopEnv\GnuPG\bin\gpg.exe`，默认使用 `gpg`，同时仍允许通过 Maven 属性 `gpg.executable` 覆盖。仓库也把本地生成的 `output/` 目录从 Git 候选变更中排除，避免烟测图片等临时产物污染提交边界。下一轮 agent 可以直接基于本任务继续拆分模块并行 harness 和回归基线升级。

## 交付物

- 可见产物：release POM 中统一的 `gpg.executable` 配置、`.gitignore` 中的 `output/` 忽略规则、任务本地 review 包。
- 修改位置：根 `pom.xml`、各发布相关模块 `pom.xml`、`.gitignore`、本任务目录。
- 验证证据：`rg` 复查无本机 GPG 绝对路径，`mvn -DskipTests package` 通过，`coding-agent-harness status --json .` 无失败无警告。

## 第一眼应该看什么

先读 `task_plan.md` 确认范围，再读 `progress.md` 查看实际执行与验证；`review.md` 记录 agent review submission 和残余风险，`walkthrough.md` 汇总本轮可交付结果。

## 边界

- 范围内：第一波低风险升级切片，包含本机路径清理、生成输出 Git 边界和任务材料收口。
- 范围外：不启用 module-parallel capability，不重构 Regression SSoT，不调整业务代码、测试实现或发布流程语义。
- 停止条件：需要人工 review confirmation、远程 push、发布签名实测或扩大到模块并行治理时必须停下等待明确授权。

## 完成判断

- release POM 不再包含 `D:\Develop\DevelopEnv\GnuPG\bin\gpg.exe`。
- 需要 GPG 可执行文件的 release profile 通过 `gpg.executable` 配置并有默认值。
- `output/` 作为本地生成输出被 `.gitignore` 排除。
- `mvn -DskipTests package` 在当前仓库通过。
- Harness 状态校验通过，任务材料不再保留模板占位内容。

## 执行合同

- Owner：coordinator
- 生命周期状态：审查中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据记录到 `progress.md`，agent review submission 已生成，人工确认由用户或 human reviewer 单独执行。

## 当前下一步

等待人工审查确认；如确认后继续第二波升级，优先处理 module-parallel 能力和回归基线 / live-provider 分层。
