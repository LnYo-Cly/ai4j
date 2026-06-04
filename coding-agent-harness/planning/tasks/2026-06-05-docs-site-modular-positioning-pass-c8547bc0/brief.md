# docs site modular positioning pass

## Task ID

`2026-06-05-docs-site-modular-positioning-pass-c8547bc0`

## 创建日期

2026-06-05

## 一句话结果

把 AI4J 的多模块结构明确写成“按需取用、逐步升级”的用户卖点，而不是只停留在工程目录事实。

## 完成后能得到什么

docs-site 的入口页、Why AI4J 和 Feature Map 会直接回答“模块是否独立、用户该怎么取用”的问题。用户可以按当前目标选择最小 artifact：只调模型用 `ai4j`，Spring 应用用 starter，Agent runtime 用 `ai4j-agent`，Coding Agent 用 `ai4j-coding` + `ai4j-cli`，FlowGram 用对应 starter，多模块组合用 BOM。后续 docs-site 深页也可以沿这张 modular positioning 继续补最小依赖、升级路径和边界说明。

## 交付物

- 可见产物：`用多少，取多少` 文案、`不是全家桶，而是可渐进升级的 Java AI SDK` 章节、Feature Map 的 `按模块取用` 表。
- 修改位置：`docs-site/docs/intro.md`、`docs-site/docs/start-here/why-ai4j.md`、`docs-site/docs/start-here/feature-map.md`。
- 验证证据：docs-site production build、`git diff --check`、harness status。

## 第一眼应该看什么

1. `docs-site/docs/intro.md`
2. `docs-site/docs/start-here/why-ai4j.md`
3. `docs-site/docs/start-here/feature-map.md`
4. `review.md`

## 边界

- 范围内：docs-site 三个入口页的模块化定位文案和任务材料。
- 范围外：Java 模块拆分、Maven 依赖调整、README、全站深页重写、视觉样式。
- 停止条件：发现文案需要声明尚未验证的独立发布能力、构建断链、或变更范围超出三页入口。

## 完成判断

- 首页出现面向用户的 `用多少，取多少` 模块取用表。
- Why AI4J 说明 AI4J 是可渐进升级的 Java AI SDK，而不是一体化全家桶平台。
- Feature Map 增加基于当前 Maven 模块依赖事实的 `按模块取用` 表。
- `docs-site` 构建通过，`git diff --check` 通过。
- 任务进入 harness review 队列，等待人工确认。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行 docs-site build 和 diff check，补齐 review packet 后提交本地 commit。
