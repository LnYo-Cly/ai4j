# Architecture at a Glance

这一页的目标不是讲细节，而是先帮你建立一个不会乱的总图。

如果你能先把这张图记住，后面读 `Core SDK`、`Agent`、`Coding Agent`、`Flowgram` 时，就不会觉得这些模块只是“堆功能”。

## 1. 先记住这张四层图

```text
Start Here
  负责定位、路径选择、第一条成功路径

Core SDK
  统一模型调用 / Tools / Skills / MCP / Memory / Search & RAG / Extension
  对应主模块：ai4j

Upper Modules
  Spring Boot         -> ai4j-spring-boot-starter
  Agent               -> ai4j-agent
  Coding Agent        -> ai4j-coding + ai4j-cli
  Flowgram            -> ai4j-flowgram-spring-boot-starter

Solutions
  场景方案、案例、端到端组合方式
```

最重要的一点是：`Core SDK` 才是整套体系的主干，其余模块都是在这层基础上往上长出来的。

## 2. 每一层各自负责什么

### 2.1 Start Here

只负责三件事：

- 解释 AI4J 是什么
- 帮你选阅读路径
- 带你走通第一条成功路径

它不负责替代完整参考文档。

### 2.2 Core SDK

这是 AI4J 的唯一基座层，也是整个文档站最重要的一层。

它回答的是：

- 模型怎么统一调用
- 本地工具怎么声明与执行
- `Skill` 怎么组织和加载
- `MCP` 怎么接入外部能力
- 会话 memory、RAG、联网增强怎么归位
- provider、服务、网络栈怎么扩展

如果这一层没理解清楚，后面的 `Agent`、`Coding Agent`、`Flowgram` 都很容易看成“零散能力集合”。

### 2.3 Upper Modules

上层模块不是另起炉灶，而是在 `Core SDK` 之上解决更具体的问题：

- `Spring Boot`：把底座能力接入容器、配置、自动装配
- `Agent`：增加 runtime、memory、tool loop、orchestration、trace
- `Coding Agent`：增加 workspace-aware tools、session runtime、CLI / TUI / ACP 宿主
- `Flowgram`：增加面向可视化节点工作流的后端运行与平台接入

### 2.4 Solutions

`Solutions` 不负责定义基础概念，而是告诉你这些能力如何按场景组合落地。

所以阅读顺序应该是：

1. 先建结构感
2. 再读基座
3. 再看上层模块
4. 最后看案例

## 3. 真实仓库里的模块路径怎么对应

从仓库结构看，主路径可以这样理解：

```text
ai4j-sdk                    父 POM / 多模块发布入口
ai4j                        Core SDK
ai4j-spring-boot-starter    Spring Boot 接入层
ai4j-agent                  通用 Agent runtime
ai4j-coding                 Coding Agent runtime
ai4j-cli                    CLI / TUI / ACP 宿主
ai4j-flowgram-spring-boot-starter
                            Flowgram 后端接入层
ai4j-flowgram-demo          Flowgram 示例工程
ai4j-bom                    版本对齐
docs-site                   文档站源码
```

也就是说，文档里的分层并不是“抽象概念”，而是可以在当前仓库里直接找到模块对应物的。

## 4. 三个最容易混的概念

这是整站最关键的边界题。

### 4.1 Function Call

`Function Call` 解决的是“本地工具如何暴露给模型调用”。

它属于：

- [Core SDK / Tools](/docs/core-sdk/tools/overview)

### 4.2 Skill

`Skill` 解决的是“给模型按需读取什么说明、模板、方法论资源”。

它首先属于：

- [Core SDK / Skills](/docs/core-sdk/skills/overview)

在 `Coding Agent` 里，它会进一步变成产品化的技能发现与加载能力。

### 4.3 MCP

`MCP` 解决的是“模型如何通过标准协议接入外部能力系统”。

它属于：

- [Core SDK / MCP](/docs/core-sdk/mcp/overview)

注意：`MCP` 和 `Tools` 密切相关，但不是 `Tools` 的子目录。`MCP` 还包含 transport、gateway、server publish、tool exposure semantics 等协议层问题。

## 5. 按目标看，你应该从哪层进入

如果你的目标是：

- 先发第一个模型请求：从 `Start Here -> Quickstart -> Core SDK / Model Access`
- 接 Spring Boot：从 `Spring Boot`
- 做智能体 runtime：从 `Agent`
- 直接把本地仓库跑成 coding assistant：从 `Coding Agent`
- 做可视化节点平台：从 `Flowgram`

但无论哪条线，最后都绕不开 `Core SDK`。

## 6. 下一步

如果你已经知道自己想做哪类事情，继续看 [Choose Your Path](/docs/start-here/choose-your-path)。

如果你想先把基座主线吃透，下一页建议直接看 [Core SDK / Overview](/docs/core-sdk/overview)。
