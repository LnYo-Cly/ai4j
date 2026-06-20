---
sidebar_position: 3
---

# 发布、安装与 GitHub Release

这一页专门从 GitHub Release 和安装器角度看 `ai4j-cli`。

如果说 [Install and Release](/docs/coding-agent/install-and-release) 更偏“当前仓库实际已提供什么分发基线”，那么这页更聚焦三件事：

1. GitHub Release 里到底该放哪些资产
2. 安装器应承担什么职责
3. 当前源码里哪些能力还没有落地成正式 release 流水线

---

## 1. 当前仓库里真实存在的发布基线

从源码现状看，`ai4j-cli` 目前已经明确具备的分发基线是：

- Java main class：`Ai4jCliMain`
- 命令分发入口：`Ai4jCli`
- fat jar 构建：`maven-assembly-plugin` 生成 `jar-with-dependencies`
- release profile：source / javadoc / sign / central publish

也就是说，当前已经真实可交付的是：

- Maven artifact
- 可直接 `java -jar` 的 fat jar

而不是：

- 已内建的平台安装器
- 已自动化的 GitHub Release bundle

---

## 2. 先把三条链彻底拆开

### 2.1 Maven 构建链

回答的是：

- 产物能不能编出来
- 有没有一个能直接跑的 fat jar

### 2.2 GitHub Release 资产链

回答的是：

- 给外部用户上传哪些可下载文件
- 文件如何组织
- 如何附带校验和说明

### 2.3 安装器链

回答的是：

- 用户下载后怎么落盘
- 怎么得到稳定命令入口
- PATH、Java 检测、失败提示谁来处理

当前仓库在第一条链上已经有实装，在后两条链上更多还是“应有设计”，不是已完成产品。

---

## 3. 当前最小 Release 资产应该是什么

基于现有源码，最小而真实的 GitHub Release 资产至少应该包含：

- `ai4j-cli-<version>-jar-with-dependencies.jar`
- `checksums.txt`
- `release-notes.md`

为什么这三项是最小集合：

- fat jar 解决“用户能直接运行”
- checksum 解决“用户能验证下载完整性”
- release notes 解决“用户知道这一版改了什么”

这套最小资产不完美，但它和当前仓库现实能力最对齐。

---

## 4. 面向终端用户的推荐 Release 资产结构

如果要把使用体验从“能运行”提升到“可安装”，更推荐发布：

- `ai4j-cli-<version>-jar-with-dependencies.jar`
- `ai4j-cli-<version>-windows-x64.zip`
- `ai4j-cli-<version>-linux-x64.tar.gz`
- `ai4j-cli-<version>-macos-x64.tar.gz`
- `checksums.txt`
- `release-notes.md`

压缩包内部更推荐采用：

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
  LICENSE
```

这层结构的价值是：

- 命令入口稳定
- 后续补默认配置、主题样例、command templates 时不需要再改目录约定
- 安装脚本只要负责把这个目录放到正确位置

---

## 5. 当前仓库里哪些东西还没有正式提供

按当前源码现状，下面这些能力仍然没有作为现成 release 产物落地：

- `ai4j` / `ai4j.cmd` launcher
- GitHub Release 资产自动上传逻辑
- `install.sh`
- `install.ps1`
- 包管理器分发入口，例如 `winget` / `scoop` / `homebrew`

这点必须在文档里说清楚，因为很多发布文档最大的风险就是：

- 把“建议方案”写成“已经存在的功能”

---

## 6. launcher 的职责边界要尽量薄

如果你准备补 `ai4j` / `ai4j.cmd`，它最稳的职责边界应该是：

### 应该做

- 定位自身目录
- 找到 `lib/` 下的 fat jar
- 检查系统是否有 `java`
- 原样透传用户命令行参数

### 不应该做

- 写死 provider / model / workspace
- 修改用户仓库内容
- 内嵌复杂 profile 管理逻辑
- 替代 `Ai4jCli` 自己的参数解析和命令分发

原因很简单：

- 当前 CLI 的正式行为已经在 `Ai4jCli`、`CodeCommand`、`AcpCommand`、config managers 里定义好了
- launcher 只需要把用户带到那个入口，不该再造一层业务逻辑

---

## 7. 一键安装器该负责什么

真正的一键安装器应当只做：

1. 识别平台
2. 下载匹配的 Release 资产
3. 校验 checksum
4. 解压到目标目录
5. 给出 PATH 配置提示
6. 给出首次运行示例

例如可落盘到：

- Linux/macOS：`~/.ai4j`
- Windows：`%USERPROFILE%\\.ai4j`

它不应该做：

- 自动写入用户 provider key
- 强行覆盖 shell profile 且不提示
- 吞掉 Java 缺失、权限不足、下载失败这类错误

最稳的安装器不是“最会猜用户意图”，而是“失败边界最清晰”。

---

## 8. Release notes 不该只写功能列表

针对 `Coding Agent` 这类本地工具，release notes 至少应包括：

- 版本号与发布日期
- 新增入口或行为变化
- provider / protocol 兼容性变更
- session / approval / MCP 的行为变化
- 破坏性变更
- 升级注意事项

原因是：

- 用户下载的是一个本地宿主程序，不只是一个 Java 依赖
- 行为层变化往往比 API 变化更重要

尤其像：

- protocol 默认值变化
- ACP 事件模型变化
- CLI slash command 语义变化

都应该被 release notes 明确记录。

---

## 9. GitHub Release 流水线真正需要产出什么信息

一个可维护的 GitHub Release 流水线，建议至少产出：

- 构建日志
- fat jar
- 各平台压缩包
- checksum
- release notes

并且最好能把这些产物和源码 tag 明确绑定，例如：

- `v2.3.0`

因为对宿主集成方来说，最重要的不是“最新版本在哪里”，而是：

- 某个稳定版本的行为能否被复现和长期依赖

---

## 10. 为什么“发 Maven Central”不能替代 GitHub Release

当前 `release` profile 确实已经面向 Maven Central 做了准备。

但 Maven Central 主要服务的是：

- Java 构建系统里的依赖消费

而 GitHub Release 主要服务的是：

- 终端用户下载使用
- 宿主集成方固定二进制版本
- 运维或 QA 团队验证具体 CLI 资产

所以这两者是互补，不是替代。

如果只做 Central 发布，用户依然还需要自己：

- 解析 artifact
- 找到 fat jar
- 自己组织运行入口

这并不是真正的 CLI 分发体验。

---

## 11. 当前最合理的演进顺序

结合现在的源码状态，更现实的路线通常是：

1. 先保持 fat jar 构建链稳定
2. 再补平台 launcher
3. 再补 GitHub Release 资产打包
4. 再补一键安装器
5. 最后再接包管理器生态

这样做的好处是：

- 每一步都建立在已存在、已验证的 Java 入口之上
- 不需要一开始就把分发问题做成一个巨大的平台工程

---

## 12. 这页最该记住的结论

- 当前源码已经支持 fat jar 构建，但还没有完整 GitHub Release 安装链
- GitHub Release 资产、安装器、Maven Central 发布是三条不同层面的链
- launcher 应尽量薄，只负责找到 Java 和 jar 并转发参数
- 一键安装器应做下载、校验、落盘、提示，不应重写 CLI 行为
- 要把“当前仓库已有能力”和“推荐发布方案”明确区分

---

## 13. 继续阅读

1. [Install and Release](/docs/coding-agent/install-and-release)
2. [Coding Agent 快速开始](/docs/coding-agent/quickstart)
3. [配置体系](/docs/coding-agent/configuration)
4. [ACP 集成](/docs/coding-agent/acp-integration)
