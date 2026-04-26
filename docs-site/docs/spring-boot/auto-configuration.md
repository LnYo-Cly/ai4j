# Spring Boot Auto Configuration

这一页专门解释 starter 到底自动装配了什么，以及为什么你能只配 `application.yml` 就直接拿到可用 Bean。

## 1. 它解决什么问题

`Spring Boot starter` 的价值不在于“少写几行代码”，而在于把基座能力收敛成稳定的容器入口。

这一层主要负责：

- 绑定配置属性
- 组装统一 `Configuration`
- 暴露 `AiService`、`AiServiceRegistry` 等基础 Bean
- 挂接向量库、RAG、HTTP 栈等默认能力

## 2. 你最该关注哪些自动装配结果

常见重点包括：

- `AiService`
- `AiServiceRegistry`
- `FreeAiService`
- 各类向量库与 RAG 默认组件

如果你已经能发请求，但还不明白“为什么这个 Bean 会出现”，这页就是你该回看的位置。

## 3. 和相邻页面怎么分工

- `quickstart` 负责最快跑通
- `auto-configuration` 负责解释 starter 到底做了什么
- `configuration-reference` 负责解释配置入口
- `bean-extension` 负责解释如何覆盖默认装配

## 4. 推荐下一步

1. [Configuration Reference](/docs/spring-boot/configuration-reference)
2. [Bean Extension](/docs/spring-boot/bean-extension)
3. [Common Patterns](/docs/spring-boot/common-patterns)
