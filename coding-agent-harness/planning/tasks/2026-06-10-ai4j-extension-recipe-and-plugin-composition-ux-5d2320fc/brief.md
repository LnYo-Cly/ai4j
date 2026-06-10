# AI4J Extension Recipe and Plugin Composition UX

## Task ID

`2026-06-10-ai4j-extension-recipe-and-plugin-composition-ux-5d2320fc`

## 创建日期

2026-06-10

## 一句话结果

为 AI4J 插件生态补一层面向使用者的 recipe 文档，让普通 Java、Spring Boot、CLI 和多插件组合的接入路径可复制、可检查、可授权。

## 完成后能得到什么

完成后，docs-site 的 Extension 章节会新增 `Plugin Recipes` 页面。读者可以从插件依赖、classpath 检查、activation plan、显式授权、tool 暴露一路走到 Agent / Coding Agent / Spring Boot 接入。第三方插件作者也能看到 README 应该提供的最小 recipe 模板。本任务不改变 Java 运行时，只把已有插件能力组织成更低心智成本的使用路径。

## 交付物

- 可见产物：`docs-site/docs/core-sdk/extension/plugin-recipes.md`
- 修改位置：Extension docs sidebar、extension overview、plugin packages、ask-user plugin 页面、任务材料、Feature SSoT
- 验证证据：docs-site typecheck/build、harness status、diff check

## 第一眼应该看什么

先读 `docs-site/docs/core-sdk/extension/plugin-recipes.md`，再看 `progress.md` 的验证记录和 `walkthrough.md` 的收口结论。

## 边界

- 范围内：docs-site 插件 recipe 文档、侧边栏入口、相关页面交叉链接、任务材料和 Feature SSoT。
- 范围外：Java API 行为变更、远程 marketplace、CLI 自动安装插件依赖、运行时 jar 热加载、provider 自动注册。
- 停止条件：如果发现现有 API 无法支持文档承诺，必须回退文档范围或另开实现任务。

## 完成判断

- Extension 章节存在 `Plugin Recipes` 入口，并位于 `Plugin Packages` 之后。
- Recipe 页面覆盖普通 Java、Spring Boot、CLI 接入前检查、多插件组合和第三方 README 模板。
- 文档不暗示已存在 marketplace、自动依赖安装、jar 热加载或 provider 自动注册。
- docs-site typecheck/build 通过。
- harness status 通过，任务进度、审查和 walkthrough 有记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、
  `progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行 docs-site typecheck/build、harness status 和 diff check，然后补 review / walkthrough 收口。
