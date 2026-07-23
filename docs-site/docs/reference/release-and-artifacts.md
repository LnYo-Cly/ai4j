---
sidebar_position: 2
---

# Release and Artifacts

这页说明 AI4J 的发布 artifact、版本对齐和项目引入顺序。它面向使用者和维护者，不替代每个模块的 API 文档。

## Maven 坐标

AI4J 当前发布坐标使用：

```xml
<groupId>io.github.lnyo-cly</groupId>
```

当前仓库版本为：

```xml
<version>2.4.2</version>
```

## 推荐依赖方式

只引入一个模块时，可以直接声明该模块版本：

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
    <version>2.4.2</version>
</dependency>
```

引入多个 AI4J 模块时，推荐使用 BOM 对齐：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.lnyo-cly</groupId>
            <artifactId>ai4j-bom</artifactId>
            <version>2.4.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

然后业务依赖里不再重复写版本：

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
</dependency>
```

## Artifact 角色

| Artifact | 角色 | 何时引入 |
| --- | --- | --- |
| `ai4j` | Core SDK | 普通 Java 项目、模型、Tool、Skill、MCP、RAG |
| `ai4j-spring-boot-starter` | Spring 接入 | Spring Boot 应用需要配置和 Bean 生命周期 |
| `ai4j-agent` | Agent runtime | 需要多步推理、workflow、trace、team |
| `ai4j-coding` | Coding runtime | 需要 workspace-aware tools、session、compaction |
| `ai4j-cli` | CLI / TUI / ACP host | 需要终端或宿主入口 |
| `ai4j-flowgram-spring-boot-starter` | FlowGram 后端 starter | 需要 FlowGram.ai 画布的 Java task API |
| `ai4j-bom` | 版本对齐 | 项目引入两个以上 AI4J artifact |

`ai4j-flowgram-demo` 是 demo 后端，不应作为生产业务依赖的 source of truth。

## 发布边界

父 POM 是多模块发布入口，但根 artifact 默认不应被业务项目当成 SDK 使用。项目接入时只引入需要的模块。

发布 profile 会处理 source、javadoc、GPG 签名和 Sonatype Central 发布配置。完整发布步骤见 [Release Checklist](/docs/reference/release-checklist)。维护者发布前应确认：

- 版本号已在根 POM 和模块 POM 中一致更新。
- `ai4j-bom` 已包含需要对齐的发布模块。
- demo 模块没有被误当成生产 artifact。
- live provider 测试和本地测试边界清楚。
- release profile 使用的凭证不写入仓库。

## 依赖选择示例

### 普通 Java 最小接入

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
    <version>2.4.2</version>
</dependency>
```

### Spring Boot 接入

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.4.2</version>
</dependency>
```

### Agent 或 Coding Agent

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-agent</artifactId>
    <version>2.4.2</version>
</dependency>
```

Coding Agent 使用者通常还会需要 `ai4j-coding` 或 `ai4j-cli`，具体取决于你是嵌入 runtime 还是直接使用 CLI/TUI/ACP host。

## 版本升级策略

1. 先在测试分支升级 BOM 或单模块版本。
2. 跑最小 quickstart，确认 provider、baseUrl、apiKey 来源仍然正确。
3. 如果项目使用 Tool、MCP、RAG 或 Agent，分别跑对应 smoke。
4. 如果项目使用 Spring Boot starter，检查配置项是否仍能绑定。
5. 如果项目使用 Coding Agent 或 FlowGram，检查宿主入口和任务 API。

升级完成后，把项目内部的接入说明链接回 [Version Compatibility](/docs/reference/version-compatibility) 和 [Production Checklist](/docs/operations/production-checklist)。
