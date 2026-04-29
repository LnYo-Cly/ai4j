# Install and Release

这一页讲的不是“怎么配置 provider”，而是另一个更现实的问题：

- 当前仓库里到底产出了什么可分发物
- 哪一层属于 Maven artifact 发布
- 哪一层才叫终端用户可安装的 CLI 分发

如果不把这三层分开，很容易把“已经能 `mvn package`”误以为“已经具备完整安装发布链”。

## 1. 先分清三层

当前和 `Coding Agent` 发布相关的事，至少分成三层：

- 构建：把 `ai4j-cli` 以及依赖打成可运行产物
- 发布：把 Maven artifact 或 release asset 放到可分发位置
- 安装：把用户本地的 `java`、命令入口、配置起点接起来

这三层相关，但当前仓库对它们的支持程度并不一样。

## 2. 当前源码里真正已经做好的是什么

`ai4j-cli/pom.xml` 当前已经明确做好的，是：

- 标准 jar 构建
- fat jar 构建
- release profile 下的 source/javadoc/sign/publish

更具体地说：

### 2.1 可直接运行的主类

manifest 主类当前是：

- `io.github.lnyocly.ai4j.cli.Ai4jCliMain`

`Ai4jCliMain` 做的事情很少，但很关键：

1. 先根据 `--verbose` 设置 SLF4J SimpleLogger
2. 再调用 `new Ai4jCli().run(...)`

这说明当前 CLI 的正式启动入口不是某个 shell 脚本，而是标准 Java main class。

### 2.2 fat jar 是当前最稳的终端分发基线

`maven-assembly-plugin` 使用的是：

- `jar-with-dependencies`

所以当前最适合终端用户直接拿来运行的产物是：

- `ai4j-cli-<version>-jar-with-dependencies.jar`

而不是默认 thin jar。

### 2.3 `Ai4jCli` 本身已经完成了命令分发

当前它直接支持：

- `code`
- `tui`
- `acp`

而且：

- 没有子命令、直接传 `--model ...` 时，默认按 `code` 处理
- `tui` 本质是 `code --ui tui`

这说明从 Java 入口角度看，命令分发已经完整，不需要你再写一层 Java launcher。

## 3. 当前仓库还没有提供什么

这一点同样重要。

根据当前 `ai4j-cli` 模块内容，仓库里还没有现成的：

- Windows `.cmd` / `.bat` launcher
- Unix `ai4j` shell launcher
- 多平台压缩包生成脚本
- checksums 资产生成逻辑
- repo 内建 installer

也就是说，当前“可分发”主要还是：

- Maven artifact
- 可直接 `java -jar` 的 fat jar

而不是：

- 开箱即装的多平台 CLI 套件

## 4. 当前最稳的构建命令

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

这个命令的意义不是只编一个模块，而是：

- 把 `ai4j-cli`
- 以及它依赖的 `ai4j`、`ai4j-coding`

一并编译并打包，最终产出可运行 fat jar。

如果你的目标只是：

- 本地验证 CLI 是否可运行
- 生成一个可以交给别人 `java -jar` 的文件

这就是当前最小而稳定的路径。

## 5. “Maven Central 发布”和“终端用户安装”不是同一回事

`ai4j-cli/pom.xml` 的 `release` profile 已经包含：

- `flatten-maven-plugin`
- `maven-source-plugin`
- `maven-javadoc-plugin`
- `maven-gpg-plugin`
- `central-publishing-maven-plugin`

这说明仓库已经考虑了：

- Maven Central / 中央仓库发布
- 源码包和 javadoc 包
- 签名和发布元数据

但这条链路服务的是：

- Java 生态消费者
- Maven / Gradle 依赖分发

它并不自动等于：

- 终端用户拿到一个原生可安装 CLI

换句话说：

- “能发到 Central” 和 “能给终端用户直接安装” 是两件事

## 6. 当前最现实的安装方式

基于现有源码，最稳的安装方式仍然是：

1. 用户机器上先有可用的 Java 运行时
2. 拿到 `ai4j-cli-<version>-jar-with-dependencies.jar`
3. 用 `java -jar ...` 直接运行

例如：

```powershell
java -jar .\ai4j-cli-2.3.0-jar-with-dependencies.jar code --model gpt-5-mini
```

这不是最优雅的终端安装体验，但它是当前源码已经真实支持、且最不依赖额外包装的方式。

## 7. 如果你要做正式 release，最少还缺什么

如果你的目标是“给外部用户稳定安装”，在现有 fat jar 之外，至少还建议补齐：

- 平台 launcher
- release checksum
- 最小示例配置
- 版本化 release notes
- 平台压缩包

一个更像产品的 release 结构通常至少会是：

```text
ai4j-cli-<version>/
  bin/
    ai4j
    ai4j.cmd
  lib/
    ai4j-cli-<version>-jar-with-dependencies.jar
  conf/
    providers.example.json
    workspace.example.json
  README.md
```

当前仓库并没有自动产出这一层，所以它仍然属于“推荐的下一步”，不是“现状”。

## 8. launcher 这一层应该做什么，不该做什么

如果你后续要补安装脚本，最稳的职责边界是：

### 应该做

- 定位 `java`
- 定位 fat jar
- 转发命令行参数
- 维持稳定命令名，例如 `ai4j`

### 不该做

- 把 provider 或 model 写死在脚本里
- 在脚本里偷偷生成仓库级配置
- 把用户密钥硬写进 launcher
- 把复杂业务判断塞进安装层

原因很简单：

- 启动器应该只是启动器
- 配置解析已经在 `Ai4jCli` / `CodeCommandOptionsParser` / config managers 里有正式实现

不要在脚本层再造一套配置系统。

## 9. 为什么不能把发布层只理解成“发一个 profile 示例”

`Coding Agent` 的产品边界比普通 SDK 更接近“本地工具”。

所以发布层至少还要考虑：

- workspace 入口
- session store 行为
- approvals
- ACP stdio server 入口
- TUI 行为依赖的终端环境

换句话说，你发布的不是：

- 某个 provider 的连接模板

而是：

- 一个带 `code` / `tui` / `acp` 三种入口的宿主程序

这也是为什么 release 文档必须同时谈：

- Java main class
- jar 形态
- launcher 形态
- config 起点

## 10. 当前仓库适合哪两类发布目标

### 10.1 面向 Java 生态的 artifact 发布

目标：

- Maven Central 消费
- 作为模块依赖

现状：

- 已有 `release` profile
- 基本元数据和签名发布链已在 pom 中体现

### 10.2 面向终端用户的 CLI 分发

目标：

- 用户直接下载并运行
- 最少理解 Maven

现状：

- 目前主要依赖 fat jar
- 还缺平台 launcher 和 release asset 组织层

一定要把这两类目标拆开，不要拿 Maven 发布链替代 CLI 安装体验。

## 11. 如果要继续完善这一层，最值得先改哪里

从当前源码出发，最合理的下一步通常是：

1. 保持 fat jar 作为基础产物不变
2. 补 `bin/ai4j` 和 `bin/ai4j.cmd`
3. 补 release 打包脚本或 CI job
4. 补 checksums 和最小配置样例
5. 再考虑 GitHub Release 自动化

这个顺序的好处是：

- 不需要改 CLI 核心逻辑
- 只是在已有稳定 Java 入口外，增加分发包装层

## 12. 这页最该记住的结论

- 当前仓库已经提供可运行的 Java 入口和 fat jar 构建
- `ai4j-cli` 的正式启动入口是 `Ai4jCliMain`
- 当前最稳的终端分发基线是 `jar-with-dependencies`
- `release` profile 更偏 Maven Central artifact 发布，不等于终端用户安装链
- 仓库里目前还没有现成的多平台 launcher / installer / release bundle 产物

## 13. 推荐下一步

1. [Coding Agent 快速开始](/docs/coding-agent/quickstart)
2. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
3. [配置体系](/docs/coding-agent/configuration)
4. [ACP 集成](/docs/coding-agent/acp-integration)
5. [命令参考](/docs/coding-agent/command-reference)
