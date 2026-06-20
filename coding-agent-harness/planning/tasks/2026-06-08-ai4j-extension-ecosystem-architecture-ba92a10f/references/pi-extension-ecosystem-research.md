# Pi Extension Ecosystem Research

> Last updated: 2026-06-08

## 调研结论

Pi 的生态不是一个窄义的 tool 插件系统，而是 package-first 的 agent customization 体系。

一个 Pi package 可以包含：

| Resource | 作用 | AI4J 映射 |
| --- | --- | --- |
| extension | 运行时代码扩展，可注册 tool、command、event、UI、provider 等能力 | `Ai4jExtension` |
| skill | 给 agent 读取的工作流和领域知识 | `SkillPackage` / `ResourcePackage` |
| prompt template | 可复用 prompt | `PromptPackage` |
| theme | TUI / UI 外观 | 后续 `ThemeResource` |
| package manifest | 声明 package 资源、过滤、安装来源和元信息 | `ai4j-package.yml` / manifest model |

Pi package 支持 npm、git、本地路径等来源；也区分全局和项目级安装。Pi 文档明确提示 package 可以执行任意代码，安装第三方 package 前要审查源码。

## Pi extension 扩展面

Pi extension 文档展示的扩展面包括：

| Extension Surface | 典型能力 | AI4J 规划 |
| --- | --- | --- |
| tool | 给模型新增可调用工具 | Wave 1 |
| command | 给 CLI/TUI 新增用户命令 | Wave 1 |
| shortcut / flag | 快捷键、启动参数、模式开关 | Wave 2/3 |
| events | 监听 session、agent、model、tool、bash、input 生命周期 | Wave 2 |
| UI / renderer / widgets | 自定义渲染、状态栏、弹窗、组件 | Wave 3 |
| provider | 注册模型 provider | AI4J 暂不作为 Wave 1；OpenAI-compatible 用 endpoint/profile 配置 |
| state/session methods | 读取和切换 session、fork、compact 等 | Wave 2，需强权限边界 |

## 对 AI4J 的直接启发

1. 插件生态的分发单位应该是 package，不是单个 Java interface。
2. 一个 package 可以同时贡献代码能力和资源能力。
3. 运行时代码扩展和 agent skill / prompt 不应混成同一接口。
4. 安装、启用、暴露给模型必须分开。
5. 第三方生态必须有 inspect 能力，让用户看到能力、权限和配置。
6. 不应直接照搬 npm install；AI4J 的第一落点是 Maven / classpath / ServiceLoader。

## 不照搬 Pi 的部分

| Pi 机制 | AI4J 第一版不照搬的原因 |
| --- | --- |
| npm/git 动态安装 | Java 项目存在 Maven/Gradle/私服/版本冲突，第一版不应做自动改依赖 |
| provider extension | AI4J provider 是 core SDK 稳定边界，OpenAI-compatible 中转不需要专属 provider |
| UI extension 大量能力 | AI4J CLI/TUI 还在演进，过早公开 UI API 会锁死实现 |
| 热加载 / 临时 package | Java classloader 和安全边界复杂，第一版先做 classpath discovery |

## 参考来源

- Pi Packages: `https://pi.dev/docs/latest/packages`
- Pi Extensions: `https://pi.dev/docs/latest/extensions`
- Pi extension examples: `https://github.com/earendil-works/pi/blob/main/packages/coding-agent/examples/extensions/README.md`
