---
sidebar_position: 5
---

# 常见问题与排障手册

本页收敛 AI4J 接入阶段最常见问题，并给出“先看哪里、再做什么”的排障顺序。

> Legacy note: 本页保留为历史排障长文。当前正式排障入口优先从 [Start Here / Troubleshooting](/docs/start-here/troubleshooting) 和 [FAQ](/docs/faq) 进入。

## 1. 测试被跳过（Skipped）

现象：`Tests run: x, Skipped: x`。

原因：Maven 默认 `skipTests=true`。

处理：

```bash
mvn -pl ai4j -DskipTests=false -Dtest=YourTest test
```

## 2. 依赖能下但项目跑不起来

优先检查：

1. 是否把 `ai4j`、starter、flowgram starter 的版本写乱了
2. 如果项目里用了多个 AI4J 模块，是否已经切到 `ai4j-bom`
3. Spring Boot 项目是否误把普通 `ai4j` 当成 starter 使用

推荐做法：

- 单模块试用：直接写单个依赖
- 多模块项目：统一切 BOM

## 3. Spring Boot 项目里拿不到 `AiService`

排查顺序：

1. 是否引入了 `ai4j-spring-boot-starter`
2. `application.yml` 是否按 `ai.*` 前缀配置
3. 当前 Spring Boot 版本是否偏离文档基线过多
4. 是否因为代理或 API Key 问题导致你误判为“没有注入成功”

## 4. 流式输出不实时

排查顺序：

1. 确认调用的是 stream API。
2. listener 回调内是否直接输出 delta。
3. IDE 控制台是否延迟刷新。
4. 模型端是否真的在流式返回（抓 HTTP 日志）。

### 2.1 ACP / IDE 宿主中的流式显示建议

如果你是通过 ACP 把 coding-agent 接到 IDE 或自定义宿主，优先检查下面几项：

1. 宿主是否按收到顺序直接渲染 `agent_message_chunk` / `agent_thought_chunk`。
2. 宿主是否错误地对 chunk 做了 `trim()`，导致换行或空白丢失。
3. 宿主是否把 `stdout` 和 `stderr` 混在一起处理。
4. 是否把“流式 event”误当成“单 token”，从而做了不必要的二次切分。
5. 如果需要更平滑的展示效果，是否在 UI 层做缓冲，而不是修改协议层数据。

## 5. Tool 未触发

排查顺序：

1. 工具是否注册（`toolRegistry(...)`）。
2. 工具名是否一致（大小写、下划线）。
3. system/instruction 是否明确要求先调用工具。
4. 是否被 handoff policy 或工具白名单拦截。

## 6. CodeAct 报错但结果为空

建议优先看：

- `CODEACT_CODE (pre-exec)` 是否打印
- `TOOL(type=code)` span 状态
- `CODE_ERROR` 是否进入下一轮重试

若要“代码执行失败后交回模型修复并再跑”，请把 `AgentOptions.maxSteps` 设为 >1，且系统提示明确“失败需修复重试”。

## 7. Trace 看起来信息不全

检查：

- 是否配置了 `traceExporter`
- `TraceConfig` 的 record 开关是否被关闭
- 是否使用了 `TraceMasker` 对字段做了脱敏/裁剪

默认配置是全量记录（模型入参、模型输出、工具参数、工具输出）。

## 8. MCP 工具暴露超预期

当前语义：

- `ToolUtil.getAllTools(functions, mcpServices)`：只返回你显式传入的工具/服务
- `ToolUtil.getLocalMcpTools()`：仅用于 MCP Server 暴露本地工具

如果你在 Agent 场景中看到了多余工具，优先检查调用点是否错误使用了 `getLocalMcpTools()`。

## 9. 乱码问题

- 终端建议统一 UTF-8
- JVM 参数显式设置 `-Dfile.encoding=UTF-8`
- 项目文件编码统一 UTF-8

## 10. 定位优先级建议

1. 先看“请求是否发出”（HTTP 日志）
2. 再看“模型是否响应”（状态码、响应体）
3. 再看“工具是否执行”（Tool 日志 / Trace）
4. 最后看“业务拼装是否正确”（Prompt、Workflow 路由）
