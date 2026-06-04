# Spring Boot Starter 模块

## 模块 Key

`spring-starter`

## 创建日期

2026-06-04

## 一句话结果

维护 `ai4j-spring-boot-starter/` 的 Spring Boot auto-configuration 和 core SDK starter wiring。

## 完成后能得到什么

该模块让 Spring Boot 自动装配、配置属性和 starter 兼容性独立于 core SDK 行为。涉及 starter wiring、auto-configuration、Spring 条件装配或属性绑定的任务应落到 `spring-starter`，并检查 core SDK API 是否变化。

## 交付物

- 可见产物：Spring Boot auto-configuration、properties、starter tests。
- 负责范围：`ai4j-spring-boot-starter/`
- 验证证据：`mvn -pl ai4j-spring-boot-starter -DskipTests=false test`

## 第一眼应该看什么

先读 `module_plan.md`，再读 `docs/11-REFERENCE/engineering-standard.md` 和 `docs/11-REFERENCE/testing-standard.md`。

## 模块职责

负责把 core SDK 以 Spring Boot 方式接入应用，不在 starter 中承载 core SDK 生产逻辑。

## 边界

- 负责：starter 源码、测试和模块 POM。
- 共享面：core SDK API、Spring Boot 版本兼容、BOM。
- 不负责：FlowGram starter、demo backend、core SDK provider 实现。

## 完成判断

- starter 任务有配置绑定或 auto-configuration 验证。
- core SDK API 变化被同步到 `core-sdk` 或 BOM。
- Java 8 / Spring Boot 兼容性风险有记录。

## 当前工作

当前没有独立模块任务；全局任务见 `coding-agent-harness/planning/tasks/`。
