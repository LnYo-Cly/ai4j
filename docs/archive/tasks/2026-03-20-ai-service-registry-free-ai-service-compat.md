# 2026-03-20 ai-service-registry-free-ai-service-compat

- 状态：COMPLETED
- 所属阶段：Phase 3 / 重建 `ai4j-model` 与 facade 收口
- 对应范围：`AiService` 主入口保持稳定，`FreeAiService` 改为兼容层，多实例能力正式收口到 registry
- 关联文档：
  - `docs/plans/2026-03-19-ai4j-2.0-constitution.md`
  - `docs/plans/2026-03-19-ai4j-2.0-implementation-plan.md`

## 1. 目标

在不破坏现有 `AiService` 主使用路径的前提下，给多实例配置路由建立正式抽象，并把 `FreeAiService` 从“直接 new provider service 的旧实现”收口成兼容委托层。

## 2. 预期交付

1. 新增正式多实例注册抽象：
   - `AiServiceRegistry`
   - `AiServiceRegistration`
   - `AiServiceFactory`
2. 新增默认实现：
   - `DefaultAiServiceRegistry`
   - `DefaultAiServiceFactory`
3. `FreeAiService` 改为兼容层：
   - 保留原有构造方式
   - 保留静态 `getChatService(id)` 用法
   - 内部委托给 registry
4. Spring Boot 自动装配新增 `AiServiceRegistry` Bean，并让 `FreeAiService` 通过 registry 装配
5. 增加一组无外部 API 依赖的本地单元测试

## 3. 设计约束

- `AiService` 仍然是主入口，不改变对外定位
- 不在这一步继续扩大 provider 迁移范围
- `FreeAiService` 只做兼容，不再承担正式扩展职责
- 多实例 registry 不直接暴露静态全局可变 Map 作为正式 API

## 4. 详细任务拆解

| 编号 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| T1 | 创建任务文档并锁定目标 | COMPLETED | 避免实现漂移 |
| T2 | 设计 registry / factory / compat 的最小接口 | COMPLETED | 以 `AiService` 为主入口，不重新发明主 facade |
| T3 | 实现 `AiServiceRegistry` 默认实现与配置复制逻辑 | COMPLETED | 支持按 `id` 管理多平台实例，并对非法 platform fail-fast |
| T4 | 重构 `FreeAiService` 为兼容委托层 | COMPLETED | 保留旧 API，内部委托 registry |
| T5 | 更新 Spring Boot 自动装配 | COMPLETED | 暴露 registry Bean，保留 `FreeAiService` Bean |
| T6 | 增加本地测试并完成验证 | COMPLETED | 不依赖外部模型 API |
| T7 | 更新实施计划与归档任务文档 | COMPLETED | 同步状态沉淀 |

## 5. 参考资料

- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factor/AiService.java`
- `ai4j/src/main/java/io/github/lnyocly/ai4j/service/factor/FreeAiService.java`
- `ai4j-spring-boot-starter/src/main/java/io/github/lnyocly/ai4j/AiConfigAutoConfiguration.java`

## 6. 变更记录

- 2026-03-20：创建任务文档，锁定本次只处理 `AiService` / `FreeAiService` / registry 这条线。
- 2026-03-20：完成 `AiServiceRegistry` / `FreeAiService` 兼容收口，Spring Boot 自动装配与本地单测已同步更新。

## 7. 已完成

- 已确认 `AiService` 才是原有主入口
- 已确认 `FreeAiService` 为后续补充的多实例配置路由能力
- 已新增 `AiServiceRegistry` / `AiServiceFactory` / `AiServiceRegistration`
- 已完成 `DefaultAiServiceRegistry` / `DefaultAiServiceFactory`
- 已将 `FreeAiService` 收口为兼容壳，并接入 Spring Boot 自动装配
- 已补充 registry 本地单元测试，覆盖配置复制与非法 platform fail-fast

## 8. 未完成

- 无；当前任务范围已完成。

## 9. 归档规则

- 本文档在当前功能完成前保持在 `docs/tasks/`
- 当 `T1-T7` 全部完成后，移动到 `docs/archive/tasks/`
