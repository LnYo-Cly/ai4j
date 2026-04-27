# Troubleshooting

这一页只收接入阶段最高频的问题，目标是先把第一条成功路径打通。

如果你现在遇到的是：

- 某个深层 API 细节
- 某个专题模块内部的设计边界
- 某个复杂 runtime 行为

优先去对应专题树。

本页只负责第一段主线：

- 依赖
- 配置
- 第一次模型调用
- 第一次 Tool Call
- Spring Boot starter 的最小接入排障

## 1. 先判断你卡在哪一段

最常见的阻塞点通常只有五类：

1. 依赖没接对
2. provider / API Key / 网络没通
3. Spring Boot 配置或 Bean 没进来
4. 流式或工具调用预期和实际不一致
5. 测试和验证命令理解错了

## 2. 测试默认被跳过

当前 Maven 配置里很多测试默认 `skipTests=true`，需要显式打开：

```bash
mvn -pl ai4j -DskipTests=false test
```

如果你本来只是想先跑示例，不要把“测试默认跳过”误当成“主功能不可用”。

## 3. Spring Boot 项目里拿不到 `AiService`

优先检查：

1. 是否引入了 `ai4j-spring-boot-starter`
2. 配置是否写在 `ai.*` 前缀下
3. 是否把网络或 API Key 问题误判成 Bean 注入问题

如果 starter 已经引入，但服务还是拿不到，下一步建议直接对照：

- [Spring Boot / Overview](/docs/spring-boot/overview)
- [Spring Boot / Auto Configuration](/docs/spring-boot/auto-configuration)
- [Spring Boot / Configuration Reference](/docs/spring-boot/configuration-reference)

## 4. 模型请求发不出去或返回异常

优先检查：

1. API Key 是否真的进入了运行环境
2. provider 是否选对
3. model 名是否可用
4. base URL、代理、网络连通性是否正确
5. 是否把 `Chat` 和 `Responses` 的接口语义混用了

如果你只是想先确认第一条主线是否成立，优先回到：

- [Quickstart for Java](/docs/start-here/quickstart-java)
- [Quickstart for Spring Boot](/docs/start-here/quickstart-spring-boot)

## 5. 流式没有实时输出

优先检查：

1. 是否真的调用了 stream API
2. listener 是否直接输出 delta
3. Web 层是否做了缓冲

## 6. Tool 不触发

优先检查：

1. 是否显式暴露了函数白名单
2. 工具名是否一致
3. 指令是否明确要求先调用工具

如果你现在还在 `Start Here` 阶段，这里最容易混淆的是：

- `Function Call`
- `Skill`
- `MCP`

先别把三件事混起来，优先回看：

- [First Tool Call](/docs/start-here/first-tool-call)
- [Core SDK / Tools](/docs/core-sdk/tools/overview)
- [Core SDK / Skills](/docs/core-sdk/skills/overview)
- [Core SDK / MCP](/docs/core-sdk/mcp/overview)

## 7. 什么时候该离开 Start Here

建议继续看：

- [Spring Boot / Configuration Reference](/docs/spring-boot/configuration-reference)
- [Spring Boot / Common Patterns](/docs/spring-boot/common-patterns)
- [FAQ](/docs/faq)

如果你已经能回答下面三个问题，就应该离开 `Start Here` 进入专题树了：

1. AI4J 在仓库里哪些模块各自负责什么
2. 你的项目应该先走 Java、Spring Boot、Agent、Coding Agent 还是 Flowgram
3. `Tool`、`Skill`、`MCP` 这三个概念为什么不是一回事
