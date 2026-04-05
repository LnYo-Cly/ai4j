---
sidebar_position: 5
---

# 常见问题与排障手册

本页收敛 AI4J 接入阶段最常见问题，并给出“先看哪里、再做什么”的排障顺序。

## 1. 测试被跳过（Skipped）

现象：`Tests run: x, Skipped: x`。

原因：Maven 默认 `skipTests=true`。

处理：

```bash
mvn -pl ai4j -DskipTests=false -Dtest=YourTest test
```

## 2. 流式输出不实时

排查顺序：

1. 确认调用的是 stream API。
2. listener 回调内是否直接输出 delta。
3. IDE 控制台是否延迟刷新。
4. 模型端是否真的在流式返回（抓 HTTP 日志）。

## 3. Tool 未触发

排查顺序：

1. 工具是否注册（`toolRegistry(...)`）。
2. 工具名是否一致（大小写、下划线）。
3. system/instruction 是否明确要求先调用工具。
4. 是否被 handoff policy 或工具白名单拦截。

## 4. CodeAct 报错但结果为空

建议优先看：

- `CODEACT_CODE (pre-exec)` 是否打印
- `TOOL(type=code)` span 状态
- `CODE_ERROR` 是否进入下一轮重试

若要“代码执行失败后交回模型修复并再跑”，请把 `AgentOptions.maxSteps` 设为 >1，且系统提示明确“失败需修复重试”。

## 5. Trace 看起来信息不全

检查：

- 是否配置了 `traceExporter`
- `TraceConfig` 的 record 开关是否被关闭
- 是否使用了 `TraceMasker` 对字段做了脱敏/裁剪

默认配置是全量记录（模型入参、模型输出、工具参数、工具输出）。

## 6. MCP 工具暴露超预期

当前语义：

- `ToolUtil.getAllTools(functions, mcpServices)`：只返回你显式传入的工具/服务
- `ToolUtil.getLocalMcpTools()`：仅用于 MCP Server 暴露本地工具

如果你在 Agent 场景中看到了多余工具，优先检查调用点是否错误使用了 `getLocalMcpTools()`。

## 7. 乱码问题

- 终端建议统一 UTF-8
- JVM 参数显式设置 `-Dfile.encoding=UTF-8`
- 项目文件编码统一 UTF-8

## 8. 定位优先级建议

1. 先看“请求是否发出”（HTTP 日志）
2. 再看“模型是否响应”（状态码、响应体）
3. 再看“工具是否执行”（Tool 日志 / Trace）
4. 最后看“业务拼装是否正确”（Prompt、Workflow 路由）
