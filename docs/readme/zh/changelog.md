# 更新日志

[返回中文 README](../../../README.md) · [English README](../../../README-EN.md)

## 更新日志
+ [2026-03-28] 修复 Coding Agent ACP 流式场景下纯空白 chunk 被 runtime 过滤的问题；ACP 保持透传原始 delta，不做 chunk 聚合；补充 CLI/文档中的流式语义说明
+ [2026-03-26] 新增 Coding Agent CLI / TUI 文档与能力说明，覆盖交互式会话、provider profile、workspace model override、命令参考与配置样例
+ [2025-08-19] 修复传递有验证参数的sse-url时，key丢失问题
+ [2025-08-08] OpenAi: max_tokens字段现已废弃，推荐使用max_completion_tokens(GPT-5已经不支持max_tokens字段)
+ [2025-08-08] 支持MCP协议，支持STDIO,SSE,Streamable HTTP; 支持MCP Server与MCP Client; 支持MCP网关; 支持自定义MCP数据源; 支持MCP自动重连
+ [2025-06-23] 修复ollama的流式错误；修复ollama函数调用的错误；修复moonshot请求时错误；修复ollama embedding错误；修复思考无内容；修复日志冲突；新增自定义异常方法。
+ [2025-02-28] 新增对Ollama平台的embedding接口的支持。
+ [2025-02-17] 新增对DeepSeek平台推理模型的适配。
+ [2025-02-12] 为Ollama平台添加Authorization
+ [2025-02-11] 实现自定义的Jackson序列化，解决OpenAi已经无法通过Json String来直接实现多模态接口的问题。
+ [2024-12-12] 使用装饰器模式增强Chat服务，支持SearXNG网络搜索增强，无需模型支持内置搜索以及function_call。
+ [2024-10-17] 支持SPI机制，可自定义Dispatcher和ConnectPool。新增百川Baichuan平台Chat接口支持。
+ [2024-10-16] 增加MiniMax平台Chat接口对接
+ [2024-10-15] 增加realtime服务
+ [2024-10-12] 修复早期遗忘的小bug; 修复错误拦截器导致的音频字节流异常错误问题; 增加OpenAi Audio服务。
+ [2024-10-10] 增强对SSE输出的获取，新加入`currData`属性，记录当前消息的整个对象。而原先的`currStr`为当前消息的content内容，保留不变。
+ [2024-09-26] 修复有关Pinecone向量数据库的一些问题。发布0.6.3版本
+ [2024-09-20] 增加对Ollama平台的支持，并修复一些bug。发布0.6.2版本
+ [2024-09-19] 增加错误处理链，统一处理为openai错误类型; 修复部分情况下URL拼接问题，修复拦截器中response重复调用而导致的关闭问题。发布0.5.3版本
+ [2024-09-12] 修复上个问题OpenAi参数导致错误的遗漏，发布0.5.2版本
+ [2024-09-12] 修复SpringBoot 2.6以下导致OkHttp变为3.14版本的报错问题；修复OpenAi参数`parallel_tool_calls`在tools为null时的异常问题。发布0.5.1版本。
+ [2024-09-09] 新增零一万物大模型支持、发布0.5.0版本。
+ [2024-09-02] 新增腾讯混元Hunyuan平台支持（注意：所需apiKey 属于SecretId与SecretKey的拼接，格式为 {SecretId}.{SecretKey}），发布0.4.0版本。
+ [2024-08-30] 新增对Moonshot(Kimi)平台的支持，增加`OkHttpUtil.java`实现忽略SSL证书的校验。
+ [2024-08-29] 新增对DeepSeek平台的支持、新增stream_options可以直接统计usage、新增错误拦截器`ErrorInterceptor.java`、发布0.3.0版本。
+ [2024-08-29] 修改SseListener以兼容智谱函数调用。
+ [2024-08-28] 添加token统计、添加智谱AI的Chat服务、优化函数调用可以支持多轮多函数。
+ [2024-08-17] 增强SseListener监听器功能。发布0.2.0版本。
