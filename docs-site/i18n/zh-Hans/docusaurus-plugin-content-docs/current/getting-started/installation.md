---
sidebar_position: 1
---

# 安装与环境准备

本页目标：让你在 **10 分钟内完成依赖接入并跑通首个请求**。

## 1. 环境要求

| 项目 | 要求 | 说明 |
| --- | --- | --- |
| JDK | `1.8+` | 核心能力兼容 JDK8 |
| Maven | `3.8+` | 推荐使用 3.8 或更高版本 |
| Node.js | `18+` | 仅文档站构建需要，业务运行不依赖 |

## 2. Coding Agent CLI 一键安装

如果你当前的目标是直接把本地 coding agent 跑起来，不必先从源码打包。文档站提供了 GitHub Pages 托管的安装脚本，脚本会从 Maven Central 下载 `ai4j-cli` 并生成 `ai4j` 命令。

### 2.1 macOS / Linux

```bash
curl -fsSL https://lnyo-cly.github.io/ai4j/install.sh | sh
```

### 2.2 Windows PowerShell

```powershell
irm https://lnyo-cly.github.io/ai4j/install.ps1 | iex
```

### 2.3 安装结果

- 默认安装目录：`~/.ai4j`（Windows 为 `%USERPROFILE%\.ai4j`）
- 默认启动命令：`ai4j`
- 安装脚本不会替你安装 JDK，仍需预先准备 Java 8+
- 如需固定版本，可设置 `AI4J_VERSION` 后再执行安装脚本

如果你只使用 SDK，不需要安装 `ai4j-cli`。

## 3. 依赖坐标

### 3.1 非 Spring 项目

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j</artifactId>
  <version>${latestVersion}</version>
</dependency>
```

### 3.2 Spring Boot 项目

```xml
<dependency>
  <groupId>io.github.lnyo-cly</groupId>
  <artifactId>ai4j-spring-boot-starter</artifactId>
  <version>${latestVersion}</version>
</dependency>
```

## 4. 非 Spring 初始化（推荐基线模板）

```java
OpenAiConfig openAiConfig = new OpenAiConfig();
openAiConfig.setApiKey(System.getenv("OPENAI_API_KEY"));

Configuration configuration = new Configuration();
configuration.setOpenAiConfig(openAiConfig);

OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .addInterceptor(new ErrorInterceptor())
        .connectTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build();

configuration.setOkHttpClient(okHttpClient);
AiService aiService = new AiService(configuration);
```

## 5. 一次性健康检查

建议按顺序执行以下检查：

1. **依赖检查**：`mvn -q -pl ai4j -DskipTests package`
2. **配置检查**：确认 API Key 能从环境变量读取
3. **调用检查**：执行一个最小同步对话
4. **流式检查**：确认 SSE/stream 能看到增量输出

## 6. 常用构建命令

```bash
# 根目录构建（默认跳过测试）
mvn -DskipTests package

# 只构建 ai4j 模块
mvn -pl ai4j -am -DskipTests package

# 运行 ai4j 模块测试（显式开启）
mvn -pl ai4j -DskipTests=false test

# 只跑单个测试类
mvn -pl ai4j -Dtest=OpenAiTest -DskipTests=false test
```

## 7. 测试为什么会被跳过

当前 POM 默认 `skipTests=true`，所以你必须显式开启：

```bash
mvn -pl ai4j -DskipTests=false -Dtest=YourTest test
```

## 8. 生产建议（安装阶段就应确定）

- API Key 只放环境变量/JVM 参数，不写死在代码。
- `OkHttpClient` 统一在配置层创建，避免业务层重复 new。
- 先收敛一个“标准模型配置模板”（model / timeout / retry）。

## 9. 常见问题排查

### 9.1 控制台只看到最终结果，看不到流式中间文本

排查顺序：

1. 是否调用了 stream 接口而不是普通接口
2. listener 是否在 `onEvent/send` 内实时输出 delta
3. IDE 控制台是否有缓冲（尤其是测试模式）

### 9.2 `There are test failures` 但日志不明显

- 查看 `ai4j/target/surefire-reports`
- 使用 `-e` 或 `-X` 获取完整栈信息

### 9.3 中文日志乱码

建议统一终端编码为 UTF-8（PowerShell/CMD），并确保 JVM 参数包含：

- `-Dfile.encoding=UTF-8`

## 10. 下一步阅读

- 首次接入：`快速开始 / JDK8 + OpenAI 最小示例`
- 业务集成：`快速开始 / Spring Boot 快速接入模式`
- 本地模型：`快速开始 / Ollama 本地模型接入`
