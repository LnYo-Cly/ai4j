# AI4J docs-site 补全路线图设计

日期：2026-03-28

## 1. 背景

当前 `docs-site` 已经具备较多内容基础，但还没有形成一套真正适合开源 SDK / Agent / Coding Agent / Flowgram 组件官网的完整信息架构。

主要问题不是“完全没文档”，而是：

- 有内容，但结构不稳定；
- 有能力，但主题归类不统一；
- 有老文档和新文档并存；
- 有代码和 demo，但官网入口缺失；
- 有总览页，但缺少真正能指导用户完成接入、扩展、排障和二次开发的深度页面。

本设计的目标，是把 `docs-site` 收敛成一套可以长期维护、适合新用户上手、也适合高级开发者查阅的开源组件官方网站。

---

## 2. 现状审计结论

### 2.1 已有优势

当前站点已经覆盖了这些基础主题：

- 安装与快速开始；
- Chat / Responses / 多模态 / Function Tool；
- Embedding / Audio / Image / Realtime；
- SearXNG / Pinecone / SPI；
- MCP 基础文档；
- Agent 架构、Runtime、Workflow、Trace、Teams；
- Coding Agent 的 CLI / TUI / ACP / Session / Skills / MCP / TUI；
- 部分案例和迁移文章。

这说明内容素材不是主要瓶颈，主要瓶颈是“结构与深度”。

### 2.2 关键问题

#### 问题 A：基础能力文档存在双份结构

仓库中同时存在：

- `docs-site/docs/ai-basics`
- `docs-site/docs/core-sdk`

但当前构建只纳入 `ai-basics`，`core-sdk` 目录并未进入正式站点路由。

结果：

- 同一主题有两套页面；
- 一套能访问，一套是死内容；
- 后续补文档时很容易补错目录。

#### 问题 B：Coding Agent 仍存在新旧两套路由

现在已经新增顶级专题：

- `docs-site/docs/coding-agent`

但历史内容仍保留在：

- `docs-site/docs/agent/coding-agent-cli.md`
- `docs-site/docs/agent/coding-agent-acp-integration.md`
- `docs-site/docs/agent/multi-provider-profiles.md`
- `docs-site/docs/agent/provider-config-examples.md`
- `docs-site/docs/agent/coding-agent-command-reference.md`
- `docs-site/docs/getting-started/coding-agent-cli-quickstart.md`

结果：

- 用户会遇到相似但不一致的两套说明；
- 新页更适合官网结构，但旧页里反而有更多详细内容；
- 需要迁移而不是简单保留。

#### 问题 C：参考型内容不够完整

尤其在以下领域仍然偏“说明页”，缺少真正的 reference / how-to 深度：

- POM / BOM / Gradle 引用；
- 各服务请求参数与返回字段读取方式；
- Function Tool 的完整开发流程；
- MCP Server 发布与对外接入；
- Coding Agent 命令逐条详解；
- ACP 对接 IDE 的宿主实现模式；
- 模型提供商扩展；
- Prompt 组装和 Runtime 机制；
- Flowgram 产品化文档。

#### 问题 D：Flowgram 还停留在 demo / starter 可用，但官网几乎缺席

从代码可确认，Flowgram 已有：

- `/flowgram` API；
- starter 自动装配；
- `Start / End / LLM / Variable / Code / Tool / HTTP / KnowledgeRetrieve` 节点；
- 自定义节点执行器扩展点；
- demo 工程与测试。

但 `docs-site` 里几乎没有对应正式专题。

#### 问题 E：站点还残留模板与历史性内容

例如：

- `tutorial-basics`
- `tutorial-extras`

这类模板目录不属于正式官网内容，应该清理或归档。

---

## 3. 总目标

将 `docs-site` 建成一套面向三类用户的官方文档网站：

1. **普通接入者**
   只关心如何引依赖、怎么调用、怎么排障。

2. **高级开发者**
   关心 MCP、Agent、Coding Agent、Flowgram 的扩展机制与架构边界。

3. **生态集成者**
   关心如何把 AI4J 集成进 IDE、桌面端、低代码平台、企业内部工具总线或工作流平台。

文档站需要做到：

- 首页即能看懂产品边界；
- 导航即能表达能力地图；
- 每个核心模块都有总览、快速开始、how-to、reference、排障；
- 所有示例都能对齐现有代码与测试；
- 没有双份内容和死目录；
- 支持长期扩展，而不是堆博客式文章。

---

## 4. 信息架构设计

建议将正式站点收敛为以下一级栏目：

1. `快速开始`
2. `核心 SDK`
3. `MCP`
4. `Agent`
5. `Coding Agent`
6. `Flowgram`
7. `场景实践`
8. `参考与排障`

### 4.1 各栏目职责

#### 快速开始

面向第一次接触 AI4J 的用户，解决：

- 如何引用依赖；
- 如何选择 Spring / 非 Spring 接入路径；
- 如何跑通第一个请求；
- 如何完成基本排障。

#### 核心 SDK

面向直接接服务能力的开发者，解决：

- 统一平台与服务入口；
- Chat / Responses / 多模态 / Tool；
- Embedding / Audio / Image / Realtime；
- RAG / SearXNG / Pinecone / SPI。

这部分建议将当前 `ai-basics` 与 `core-sdk` 合并，避免重复。

#### MCP

面向协议接入者与平台扩展者，解决：

- 如何作为 MCP Client；
- 如何使用 MCP Gateway；
- 如何把本地能力发布成 MCP Server；
- 如何与 Agent / Coding Agent 组合。

#### Agent

面向框架开发者，解决：

- Agent 抽象层与模型层；
- Runtime；
- Workflow / StateGraph；
- Memory；
- SubAgent / Teams；
- Trace。

#### Coding Agent

面向直接使用者与宿主集成者，解决：

- CLI / TUI / ACP；
- Provider / Model / Session / Commands；
- Prompt / Runtime / Memory / Tool / Skill / MCP；
- TUI 定制；
- ACP 宿主接入；
- 模型与工具扩展。

#### Flowgram

面向低代码与工作流使用者，解决：

- demo 如何直接运行；
- starter 怎么接；
- 内置节点如何用；
- API 如何调用；
- 自定义节点如何扩展；
- 前后端如何联动。

#### 场景实践

保留端到端案例，但应当服务主线文档，而不是取代主线文档。

#### 参考与排障

统一沉淀：

- 版本与兼容性；
- FAQ；
- 参数表；
- 命令参考；
- 术语表；
- 迁移指南；
- Release / 安装说明。

---

## 5. 内容建设原则

### 5.1 页面类型固定化

每个核心模块至少应包含以下五类页面：

- `overview`：它是什么，边界是什么；
- `quickstart`：最短路径；
- `how-to`：按任务完成具体动作；
- `reference`：参数、命令、对象、配置表；
- `troubleshooting`：排障与边界。

### 5.2 示例必须来自真实实现

优先使用这些来源：

- 正式源码；
- 集成测试；
- demo 工程；
- starter 自动装配代码。

避免写“看起来合理但并未被实现验证”的伪示例。

### 5.3 页面按用户任务组织，不按作者写作习惯组织

文档不是日记，不应该把修 bug 过程写成说明文档。

所有页面应围绕这些问题展开：

- 我怎么接？
- 我怎么调用？
- 我怎么扩展？
- 我怎么排障？
- 我怎么上线？

### 5.4 避免重复

同一个主题只能有一套正式主文档。

例如：

- Chat / Responses 不应同时维护 `ai-basics` 与 `core-sdk` 两份；
- Coding Agent 不应同时在 `agent` 和 `coding-agent` 中并行维护主入口。

---

## 6. 分阶段实施计划

### 阶段 1：结构收敛

目标：

- 确定唯一正式目录；
- 清理重复入口；
- 建立新的官网栏目边界。

任务：

- 合并 `ai-basics` 与 `core-sdk`；
- 迁移旧 `agent/coding-agent-*` 的有效内容到 `coding-agent/*`；
- 将 `tutorial-basics` / `tutorial-extras` 归档或移除；
- 新增 `Flowgram` 顶级栏目；
- 更新侧边栏、首页、footer、intro、README。

完成标准：

- 站内不再存在双份主题；
- 用户从导航即可看懂站点结构。

### 阶段 2：快速开始重构

目标：

- 新用户可以快速完成依赖引入与首个成功调用。

任务：

- 补全 Maven / BOM / Gradle 引用；
- 增加版本与兼容性说明；
- 区分 Spring / 非 Spring；
- 补最小同步请求、最小流式请求、最小 Tool 请求；
- 重写安装与排障路径。

完成标准：

- 用户可在 10 分钟内完成首个成功调用。

### 阶段 3：核心 SDK 完整化

目标：

- 将统一平台与服务接入写成完整主线。

任务：

- 平台与服务矩阵；
- Chat 非流式 / 流式；
- Responses 非流式 / 流式；
- 多模态；
- Tool / Function；
- Embedding / Audio / Image / Realtime；
- SearXNG；
- Pinecone；
- SPI 扩展。

完成标准：

- 每个服务均有最小示例、参数说明、返回读取方式、平台差异、排障建议。

### 阶段 4：MCP 产品化文档

目标：

- 让 MCP 从“协议说明”升级为“工程接入手册”。

任务：

- MCP Client；
- 第三方 MCP；
- Gateway；
- MCP Server 发布；
- 传输类型；
- Tool 暴露语义；
- 与 Agent / Coding Agent 组合方式；
- 多租户与动态服务管理。

完成标准：

- 用户能清楚完成三种事：
  - 接别人家的 MCP；
  - 管理多个 MCP；
  - 发布自己的 MCP 给别人用。

### 阶段 5：Agent 文档完善

目标：

- 让 Agent 文档真正承担框架文档角色。

任务：

- Agent 总览与边界；
- 最小 ReAct Agent；
- 自定义 Agent；
- Runtime 体系；
- Workflow / StateGraph；
- Memory；
- SubAgent / Teams；
- Trace；
- Prompt 组装与模型适配；
- 核心类参考。

完成标准：

- 开发者能基于文档实现自己的 Agent 和编排流程。

### 阶段 6：Coding Agent 深水区完善

目标：

- 将 Coding Agent 做成真正的产品文档，而非只是一组使用说明。

任务：

- CLI / TUI / ACP；
- Provider / Protocol / Model / Profile；
- Session / Runtime / Memory；
- Prompt 组装；
- Tools / Skills / MCP；
- TUI 定制；
- ACP 对接 IDE；
- 模型与工具扩展；
- 命令逐条详解。

完成标准：

- 普通用户能直接使用；
- IDE 宿主开发者能完成集成；
- 二次开发者能完成扩展。

### 阶段 7：Flowgram 官方专题建设

目标：

- 将 Flowgram 从 demo/readme 级别升级为正式官网能力章节。

任务：

- Flowgram 总览；
- demo 快速运行；
- REST API 使用；
- starter 自动装配；
- 内置节点说明：
  - `Start`
  - `End`
  - `LLM`
  - `Variable`
  - `Code`
  - `Tool`
  - `HTTP`
  - `KnowledgeRetrieve`
- 自定义后端节点执行器；
- 前端节点 schema 与表单约定；
- 前后端联调；
- 部署与排障。

完成标准：

- 普通用户能跑 demo；
- 开发者能新增节点；
- 平台方能理解前后端扩展边界。

### 阶段 8：参考、版本与发布体系

目标：

- 让文档站支持长期运营和发布。

任务：

- 命令参考；
- 配置参考；
- FAQ；
- 迁移指南；
- 版本兼容矩阵；
- 发布说明；
- 下载与安装；
- Release / GitHub Release / 一键安装文档；
- 术语表。

完成标准：

- 文档站可支撑后续 Release 节奏和外部用户维护成本。

---

## 7. 重点补全文档清单

以下页面建议优先新增或重写：

### 快速开始

- `getting-started/dependency-management.md`
- `getting-started/first-chat-request.md`
- `getting-started/first-stream-request.md`
- `getting-started/first-tool-request.md`

### 核心 SDK

- `sdk/overview.md`
- `sdk/platforms-and-service-matrix.md`
- `sdk/chat/non-stream.md`
- `sdk/chat/stream.md`
- `sdk/chat/multimodal.md`
- `sdk/chat/tool-calling.md`
- `sdk/responses/non-stream.md`
- `sdk/responses/stream-events.md`
- `sdk/embedding.md`
- `sdk/audio.md`
- `sdk/image.md`
- `sdk/realtime.md`
- `sdk/searxng.md`
- `sdk/pinecone.md`
- `sdk/spi.md`

### MCP

- `mcp/client-quickstart.md`
- `mcp/server-quickstart.md`
- `mcp/gateway-quickstart.md`
- `mcp/server-publication-checklist.md`

### Agent

- `agent/minimal-react-agent.md`
- `agent/prompt-assembly.md`
- `agent/model-provider-extension.md`
- `agent/workflow-recipes.md`

### Coding Agent

- `coding-agent/prompt-assembly.md`
- `coding-agent/model-provider-management.md`
- `coding-agent/ide-hosting.md`
- `coding-agent/commands/provider-and-model.md`
- `coding-agent/commands/skills-and-mcp.md`
- `coding-agent/commands/session-and-history.md`
- `coding-agent/commands/process-and-shell.md`
- `coding-agent/commands/tui-and-palette.md`

### Flowgram

- `flowgram/overview.md`
- `flowgram/quickstart.md`
- `flowgram/demo-run.md`
- `flowgram/api.md`
- `flowgram/builtin-nodes.md`
- `flowgram/llm-node.md`
- `flowgram/tool-node.md`
- `flowgram/http-node.md`
- `flowgram/code-node.md`
- `flowgram/knowledge-retrieve-node.md`
- `flowgram/custom-backend-node.md`
- `flowgram/frontend-node-schema.md`
- `flowgram/frontend-backend-integration.md`
- `flowgram/troubleshooting.md`

---

## 8. 执行顺序建议

推荐按以下顺序推进：

1. 收敛结构与导航；
2. 统一核心 SDK 主线；
3. 重写快速开始；
4. MCP 补齐；
5. Agent 补齐；
6. Coding Agent 深化；
7. Flowgram 专题建设；
8. 参考、版本、安装与发布补齐。

原因：

- 先解决结构问题，后续补内容才不会返工；
- 先补 SDK 主线，才能让新用户顺畅进入；
- Agent / Coding Agent / Flowgram 属于进阶能力，应建立在基础能力清晰的前提上。

---

## 9. 验收标准

当以下条件满足时，可认为 `docs-site` 基本达到“开源 SDK 官网”标准：

1. 没有双份主题、死目录和模板目录；
2. 顶级导航能完整表达 AI4J 的能力边界；
3. 每个核心模块都有：
   - 总览
   - 快速开始
   - how-to
   - reference
   - 排障
4. 所有关键示例均能在源码或测试中找到对应实现；
5. Coding Agent 的命令有逐条详解；
6. Flowgram 具备正式专题，不再只依赖 demo README；
7. 文档站构建通过，主导航无死链；
8. README 与 docs-site 保持一致入口。

---

## 10. 风险与注意事项

### 风险 1：文档写得太快，脱离实现

应对：

- 每页都要以源码、测试、demo 为基准；
- 不确定的能力不要写成既成事实。

### 风险 2：旧文档删太快，丢失有效内容

应对：

- 先迁移内容，再下线旧入口；
- 必要时保留归档目录。

### 风险 3：站点继续混合“博客文章”和“官方手册”

应对：

- 场景实践与产品主线严格分离；
- 主线文档优先级高于案例文章。

### 风险 4：Flowgram 文档只写后端，不写前端协同

应对：

- Flowgram 必须同时覆盖：
  - 普通用户如何跑；
  - 后端如何扩展；
  - 前端如何对接；
  - 前后端如何联调。

---

## 11. 下一步

下一步应从“结构收敛实施清单”开始，把本设计拆成按文件执行的任务列表，包括：

- 新目录规划；
- 旧文件迁移映射；
- 侧边栏与首页调整；
- 第一批优先新增 / 重写页面清单；
- 每一页对应的源码 / 测试依据。

本设计文档用于作为 `docs-site` 后续补全工作的统一基线。
