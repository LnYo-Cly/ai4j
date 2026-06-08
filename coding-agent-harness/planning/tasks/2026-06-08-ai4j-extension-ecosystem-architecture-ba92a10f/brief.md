# AI4J extension ecosystem architecture

## Task ID

`2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f`

## 创建日期

2026-06-08

## 一句话结果

形成一份可执行的 AI4J Extension System 架构设计，明确如何对标 Pi 的 package / extension 生态，并落到 AI4J 的 Java SDK、Agent、Coding Agent、CLI、Spring Boot 和 docs-site 边界。

## 完成后能得到什么

本任务完成后，项目可以直接进入插件生态实现切片，而不是继续在对话里反复讨论“插件到底是什么”。设计会给出 Pi 生态事实、AI4J 对标边界、Package / Manifest / Extension / Resource 分层、首批扩展点、显式启用和安全治理规则、模块落点、官方样板插件、分波实施路线和验收标准。下一轮实现任务可以从 Wave 1 的 manifest / loader / inspect 开始，不需要重新定义方向。

## 交付物

- 可见产物：Pi 调研摘要、AI4J Extension System 架构设计、Feature SSoT 活跃项、任务审查材料。
- 修改位置：`coding-agent-harness/planning/tasks/2026-06-08-ai4j-extension-ecosystem-architecture-ba92a10f/`、`docs/09-PLANNING/Feature-SSoT.md`。
- 验证证据：`npx.cmd --yes coding-agent-harness status --json .`、`git diff --check`、设计自审记录。

## 第一眼应该看什么

先读 `references/ai4j-extension-system-design.md`。如果要核对为什么这样设计，再读 `references/pi-extension-ecosystem-research.md` 和 `findings.md`。如果要进入实现，按 `task_plan.md` 的 Wave 1 切片开新任务。

## 边界

- 范围内：插件生态架构规划、Pi 对标调研、AI4J 模块落点、分波实施路线、Feature SSoT 记录和任务本地审查材料。
- 范围外：运行时代码实现、Maven 模块新增、docs-site 插件专区正文落地、CLI 命令实现、第三方插件样板实现。
- 停止条件：如果设计需要改变 Java 8 基线、引入运行时动态下载 jar、或把 OpenAI-compatible 中转平台设计成专属 provider，必须暂停并重新确认。

## 完成判断

- Pi 的 package / extension / skill / prompt / theme 事实被记录，并明确不能只按 Tool Plugin 理解。
- AI4J 的 Package / Manifest / Extension / Resource 分层和首批扩展点被定义。
- 安全模型明确 classpath 发现、插件启用和工具暴露是三道不同门禁。
- 分波路线可执行，且第一波不包含动态安装、热加载、marketplace 或 provider 乱命名。
- harness 状态检查通过，任务进入审查材料就绪状态。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

完成 task-local 设计文档和 Feature SSoT 更新，然后运行 harness status 和 diff check。
