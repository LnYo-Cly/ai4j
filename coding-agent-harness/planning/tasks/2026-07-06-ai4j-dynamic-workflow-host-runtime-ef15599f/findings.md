# ai4j dynamic workflow host runtime - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。它不是审查报告；阻塞性问题请写入 `review.md`。

## 研究发现

### 插件和 host runtime 的边界

- 背景：用户明确要求像第三方插件一样保持独立，不为了 dynamic workflow 直接修改或内嵌插件到 SDK reactor。
- 发现：独立插件的稳定职责是通过 `ai4j-extension-api` 暴露 `workflow` tool/command/Skill/Prompt，并返回 `ai4j.dynamic_workflow.request` envelope；实际执行需要宿主持有 model client、agent bridge、tool policy、workspace/sandbox 才能安全落地。
- 影响：本轮没有修改 `ai4j-extension-api`，也没有把插件并入 `ai4j-sdk` BOM；新增能力落在 `ai4j-agent` host/runtime 层，并通过 `AgentBuilder.dynamicWorkflow(...)` 显式 opt-in。
- 后续：如后续要做后台 run manager、resume 或 worktree fan-out，应在 `ai4j-agent` / `ai4j-coding` 开新任务。

### Nashorn runtime 安全面

- 背景：workflow script 可能由模型生成，不能让脚本默认获得宿主 Java 对象、文件、进程或 classpath 访问能力。
- 发现：直接把 Java bridge 放入 Nashorn binding 会扩大可见面；默认 Nashorn Java interop 也不是安全默认。
- 影响：执行器改为默认通过 `--no-java` 创建 engine，清掉 `load`/`quit` 等危险全局，把 Java bridge 仅保存在 prelude 闭包内，并用 `phase/log/agent/parallel/pipeline` 作为唯一稳定 primitive。
- 后续：替换 runtime 时必须保留同类安全回归。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| Runtime 所属模块 | `ai4j-agent` host/runtime 层 | 宿主持有 Agent、tool executor、permission、trace、sandbox/workspace 上下文；插件只负责请求 envelope。 | 放入独立插件或 `ai4j-extension-api` | accepted |
| Public plugin contract | 不修改 `ai4j-extension-api` | envelope 已足够表达 host action、script、args 和预算；扩展 API 当前能力足够。 | 新增 plugin executor SPI | accepted |
| 内置 JS runtime | Java 8 compatible Nashorn MVP | 与当前 Java 8 基线兼容；适合作为 deterministic host runtime smoke。 | Node.js、GraalJS、JSR223 外部进程 | accepted |
| Script safety default | `allowJavaInterop=false` + `--no-java` + closure bridge | 避免模型脚本默认访问宿主 Java/classpath；只暴露 workflow primitives。 | 直接暴露 Java bridge / 完全信任脚本 | accepted |
| `parallel()` 首版行为 | 保持 fan-out group 语义和结果顺序，但不承诺物理并发 | Nashorn 单 engine 共享状态下并发执行 JS function 风险更高；真实隔离并发应交给 coding-agent worker/worktree bridge。 | 在 Nashorn 内直接多线程跑 task function | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否需要 live-provider E2E | 本轮不需要；deterministic local regression 足够证明 host executor contract。 | coordinator | 若用户要求真实 provider demo，则单独开启 opt-in 任务。 |
| 是否将插件纳入 BOM | 当前不纳入；保持独立 plugin repo 展示生态。 | maintainer | 插件稳定发版并决定进入 SDK release train 时再评估。 |
