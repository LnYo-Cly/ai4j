# Why Flowgram

`Flowgram` 解决的是“可视化工作流平台”这条线，不是普通 Agent runtime 的另一个壳。

这里最容易被误解的一点是：

- `Flowgram.ai` 是字节开源的前端工作流/画布库
- AI4J 这一章讲的是围绕它补起来的后端 runtime、任务 API、节点执行与对接方式

## 1. 适合什么

- 节点图天然比自由推理更稳定的任务
- 前端会画流程
- 后端需要稳定 runtime 和任务 API

## 2. 它和 Agent 的边界

`Agent` 更适合自由推理和 runtime 决策。

`Flowgram` 更适合节点图、明确 schema 和平台后端执行。

`Flowgram.ai` 偏前端编辑器，AI4J `Flowgram` 偏后端执行层。

## 3. 下一步

- [Architecture](/docs/flowgram/architecture)
- [Quickstart](/docs/flowgram/quickstart)
