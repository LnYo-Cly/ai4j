---
sidebar_position: 8
---

# CodeAct：自定义代码沙箱执行器

你要补全“CodeAct 使用自定义沙箱”的内容，这页按可落地实现来写。

## 1. 先明确：CodeAct 的沙箱扩展点在哪里

扩展点只有一个：`CodeExecutor`。

```java
public interface CodeExecutor {
    CodeExecutionResult execute(CodeExecutionRequest request) throws Exception;
}
```

`CodeActRuntime` 不关心你是本地解释器、容器、还是远程沙箱服务，只要你返回标准 `CodeExecutionResult` 即可。

## 2. 默认执行器与边界

默认是 `GraalVmCodeExecutor`，支持 Python(GraalPy) 与 JS。

优点：

- 开箱即用
- 能直接调用 AI4J 工具

边界：

- 默认不是强隔离容器沙箱
- 生产高风险场景建议替换为你自己的执行器

## 3. `CodeExecutionRequest` 里你能拿到什么

- `language`：代码语言（python/js）
- `code`：模型生成代码
- `toolNames`：当前允许的工具名
- `toolExecutor`：工具执行器（可用于 `callTool`）
- `user`：当前用户上下文
- `timeoutMs`：可用超时时间

## 4. `CodeExecutionResult` 合同（很关键）

- `result`：最终结果（建议优先放最终可消费值）
- `stdout`：标准输出
- `error`：错误信息

`error` 为空表示成功；非空表示失败，Runtime 会把失败信息回写 memory（`CODE_ERROR`）。

## 5. 接入自定义执行器

```java
Agent agent = Agents.codeAct()
        .modelClient(modelClient)
        .model("doubao-seed-1-8-251228")
        .codeExecutor(new MySandboxCodeExecutor())
        .codeActOptions(CodeActOptions.builder().reAct(true).build())
        .build();
```

## 6. 实现模式 A：本地进程沙箱（推荐起步）

适合先在单机环境做可控执行。

### 6.1 核心思路

1. 校验语言与工具白名单
2. 把代码写入临时文件
3. 用受限命令启动子进程（如 `python -I`）
4. 超时杀进程
5. 收集 stdout/stderr 构造 `CodeExecutionResult`

### 6.2 示例骨架

```java
public class ProcessSandboxCodeExecutor implements CodeExecutor {

    @Override
    public CodeExecutionResult execute(CodeExecutionRequest request) throws Exception {
        if (request == null || request.getCode() == null) {
            return CodeExecutionResult.builder().error("code is required").build();
        }

        String language = normalize(request.getLanguage());
        if (!"python".equals(language)) {
            return CodeExecutionResult.builder().error("only python is allowed").build();
        }

        Path tempDir = Files.createTempDirectory("ai4j-codeact-");
        Path script = tempDir.resolve("main.py");
        Files.write(script, request.getCode().getBytes(StandardCharsets.UTF_8));

        ProcessBuilder pb = new ProcessBuilder("python", "-I", script.toString());
        pb.directory(tempDir.toFile());
        Process process = pb.start();

        long timeout = request.getTimeoutMs() == null ? 8000L : request.getTimeoutMs();
        boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return CodeExecutionResult.builder().error("code execution timeout").build();
        }

        String stdout = read(process.getInputStream());
        String stderr = read(process.getErrorStream());

        return CodeExecutionResult.builder()
                .stdout(stdout)
                .result(stdout == null ? null : stdout.trim())
                .error(stderr == null || stderr.trim().isEmpty() ? null : stderr)
                .build();
    }

    private String normalize(String lang) {
        if (lang == null) {
            return "python";
        }
        return lang.trim().toLowerCase();
    }

    private String read(InputStream input) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(line);
            }
            return sb.toString();
        }
    }
}
```

> 这里是“最小版骨架”，生产上还要补资源限制与隔离策略。

## 7. 实现模式 B：远程沙箱服务

适合开源组件场景：SDK 不直接执行不可信代码，而是调用外部执行服务。

### 7.1 思路

- `CodeExecutor` 里只做 HTTP RPC
- 请求体包含 `language/code/timeout/tool-policy`
- 返回统一 `result/stdout/error`

### 7.2 示例骨架

```java
public class RemoteSandboxCodeExecutor implements CodeExecutor {

    private final OkHttpClient client;
    private final String endpoint;

    public RemoteSandboxCodeExecutor(OkHttpClient client, String endpoint) {
        this.client = client;
        this.endpoint = endpoint;
    }

    @Override
    public CodeExecutionResult execute(CodeExecutionRequest request) throws Exception {
        String body = JSON.toJSONString(request);
        Request httpRequest = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(MediaType.parse("application/json"), body))
                .build();

        try (Response response = client.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                return CodeExecutionResult.builder().error("sandbox http error: " + response.code()).build();
            }
            String json = response.body() == null ? "" : response.body().string();
            return JSON.parseObject(json, CodeExecutionResult.class);
        }
    }
}
```

## 8. 工具调用在自定义沙箱里怎么做

你有两种策略：

1. **Host 回调模式**：沙箱内代码通过桥接函数调用 `request.getToolExecutor()`
2. **先禁用工具**：沙箱只做纯计算，不允许工具调用

建议开源默认策略：

- 默认只开白名单工具
- 参数做 JSON Schema 校验
- 高风险工具默认禁用

## 9. 与 `CodeActOptions.reAct` 的配合

- `reAct=false`：你的执行器返回结果后可直接结束
- `reAct=true`：执行结果会再回给模型整理为自然语言

这两种模式对“执行器实现”没有破坏性差异，执行器只需保证 `CodeExecutionResult` 正确。

## 10. 生产安全清单（强烈建议）

1. 时间限制：每次执行必须有 timeout。
2. 资源限制：CPU/内存/进程数。
3. 文件系统限制：只允许临时目录。
4. 网络限制：默认无外网（除非明确需要）。
5. 工具限制：白名单 + 参数校验。
6. 审计日志：记录代码摘要、工具调用、耗时、退出状态。

## 11. 观测建议

结合 trace + 事件流看三段耗时：

- `MODEL`：代码生成时间
- `TOOL(type=code)`：沙箱执行时间
- 下一轮 `MODEL`：结果整理时间（`reAct=true` 时）

## 12. 常见坑

1. `error` 字段不填导致失败被误判为成功。
2. 忽略 `timeoutMs` 导致执行悬挂。
3. 自定义执行器没处理编码，中文输出乱码。
4. 工具白名单缺失，出现越权调用。

## 13. 关联源码与测试

- `CodeExecutor`
- `CodeExecutionRequest`
- `CodeExecutionResult`
- `GraalVmCodeExecutor`
- `CodeActRuntime`
- `CodeActRuntimeTest`
- `CodeActRuntimeWithTraceTest`

你可以先实现一个最小 `ProcessSandboxCodeExecutor` 跑通，再演进到远程沙箱。
