# Troubleshooting

这一页只收接入阶段最高频的问题，目标是先把第一条成功路径打通。

## 1. 测试默认被跳过

当前 Maven 配置里很多测试默认 `skipTests=true`，需要显式打开：

```bash
mvn -pl ai4j -DskipTests=false test
```

## 2. Spring Boot 项目里拿不到 `AiService`

优先检查：

1. 是否引入了 `ai4j-spring-boot-starter`
2. 配置是否写在 `ai.*` 前缀下
3. 是否把网络或 API Key 问题误判成 Bean 注入问题

## 3. 流式没有实时输出

优先检查：

1. 是否真的调用了 stream API
2. listener 是否直接输出 delta
3. Web 层是否做了缓冲

## 4. Tool 不触发

优先检查：

1. 是否显式暴露了函数白名单
2. 工具名是否一致
3. 指令是否明确要求先调用工具

## 5. 继续排障

建议继续看：

- [Spring Boot / Configuration Reference](/docs/spring-boot/configuration-reference)
- [Spring Boot / Common Patterns](/docs/spring-boot/common-patterns)
- [FAQ](/docs/faq)
