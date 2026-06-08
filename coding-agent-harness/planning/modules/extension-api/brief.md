# Extension API 模块简介

## 模块身份

- 模块 Key：`extension-api`
- 路径：`ai4j-extension-api/`
- 负责人：coordinator
- 分支：`main`

## 职责

`ai4j-extension-api` 负责第三方扩展生态的轻量公共合同：manifest、capability、ServiceLoader discovery、显式 enable/expose 门禁，以及 tool、command、skill、prompt、guardrail 的中立资源 spec。

## 边界

- 它不依赖 `ai4j`、`ai4j-agent`、`ai4j-coding`、`ai4j-cli` 或 Spring Boot starter。
- 它不实现 provider plugin、Marketplace、运行时 jar 下载、CLI install、Spring Boot 绑定或 Agent runtime 适配。
- 第三方插件可以依赖这个模块，宿主模块再把中立 spec 适配到自己的 runtime。

## 默认验证

- `mvn -pl ai4j-extension-api -DskipTests=false test`
- 共享 build 或 BOM 变更时追加 `mvn -DskipTests package`
