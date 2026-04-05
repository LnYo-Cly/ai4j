---
sidebar_position: 3
---

# 发布、安装与 GitHub Release

本页解决三个问题：

1. `ai4j-cli` 当前已经可以怎样分发和使用
2. 对外发布时，GitHub Release 应该提供哪些资产
3. 一键安装脚本应当承担什么职责，边界在哪里

---

## 1. 当前已经可用的发布形态

当前仓库已经具备的正式产物是 Maven 打包后的 fat jar：

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

产物位置：

```text
ai4j-cli/target/ai4j-cli-2.1.0-jar-with-dependencies.jar
```

直接运行方式：

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-2.1.0-jar-with-dependencies.jar code --help
```

这条路径适合：

- 仓库贡献者；
- 内部开发测试；
- 需要立刻验证 CLI / TUI / ACP 行为的人。

它的优点是维护成本低、和源码完全对齐；缺点是外部用户必须先装 JDK，再自己处理 jar 路径和启动命令。

---

## 2. 当前还没有内建的能力

截至当前仓库状态，下面这些发布能力还没有作为现成产物随仓库一起提供：

- 独立的 `ai4j` / `ai4j.cmd` 启动器；
- GitHub Actions 自动上传 Release 资产；
- 面向最终用户的 `install.sh` / `install.ps1`；
- `winget` / `scoop` / `homebrew` 等包管理分发。

因此官网文档必须把“当前可用方式”和“推荐发布方案”明确区分，避免把规划写成已上线能力。

---

## 3. 推荐的 GitHub Release 资产结构

如果要把 `Coding Agent` 作为真正面向外部用户的交付入口，Release 里至少建议发布四类资产。

### 3.1 最小资产

- `ai4j-cli-<version>-jar-with-dependencies.jar`
- `checksums.txt`

这是最低维护成本方案，适合先把“下载即运行”的能力上线。

### 3.2 推荐资产

- `ai4j-cli-<version>-jar-with-dependencies.jar`
- `ai4j-cli-<version>-windows-x64.zip`
- `ai4j-cli-<version>-linux-x64.tar.gz`
- `ai4j-cli-<version>-macos-x64.tar.gz`
- `checksums.txt`
- `release-notes.md`

其中压缩包建议采用下面的目录结构：

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
  LICENSE
  README.md
```

这样做的好处是：

- 用户不需要自己记 jar 文件名；
- `bin/ai4j` 可以稳定成为长期命令入口；
- 后续增加默认配置、主题样例、命令模板时也有固定目录。

---

## 4. `ai4j` 启动器应该做什么

发布版里的 `ai4j` 或 `ai4j.cmd`，本质上只是一个稳定的启动壳。

它应该负责：

- 定位自身目录；
- 找到 `lib/` 下的 fat jar；
- 检查本机是否存在 `java`；
- 将用户传入参数原样透传给 `Ai4jCliMain`。

它不应该负责：

- 把 provider、workspace、profile 写死在脚本里；
- 修改用户业务目录；
- 在脚本中内嵌复杂安装逻辑；
- 为不同命令分叉出多套不可维护逻辑。

稳定的做法是：

- 启动器只做“找到 Java 并启动 jar”
- 配置留给 `providers.json`、`workspace.json`、CLI 参数和环境变量

---

## 5. 一键安装脚本的职责边界

一键安装不是把整个 CLI 逻辑重写一遍，而是把“下载、落盘、加 PATH、给出下一步提示”标准化。

### 5.1 `install.sh` / `install.ps1` 应做的事

- 识别目标平台；
- 下载 GitHub Release 中对应平台的压缩包；
- 解压到用户目录，例如：
  - Linux/macOS：`~/.ai4j`
  - Windows：`%USERPROFILE%\\.ai4j`
- 确保 `bin/ai4j` 或 `bin\\ai4j.cmd` 可执行；
- 输出 PATH 配置提示；
- 提示用户首次运行示例。

### 5.2 不应做的事

- 自动写入用户的 provider 密钥；
- 强行修改 shell profile 且不提示；
- 吞掉下载或解压错误；
- 在没有 Java 的机器上假装安装成功。

### 5.3 推荐的失败策略

安装脚本遇到下面情况时，应直接失败并给出明确提示：

- 未检测到 `java`
- Release 资产不存在
- 下载校验失败
- 目录无写权限

这类脚本必须是幂等的。重复执行时，应该覆盖同版本文件或安全升级到新版本，而不是留下多份难以判断的残留。

---

## 6. GitHub Release 的推荐发布流程

一个可维护的 Release 流程，建议至少包含下面几步：

1. 打版本 tag，例如 `v2.1.0`
2. CI 构建 `ai4j-cli` fat jar
3. 组装各平台压缩包
4. 生成 `checksums.txt`
5. 创建 GitHub Release 并上传资产
6. 发布 release notes
7. 文档站同步更新安装说明与版本号

Release notes 至少应包含：

- 版本号与发布日期；
- 新增能力；
- 破坏性变更；
- 升级注意事项；
- 已知限制；
- 文档链接。

---

## 7. 对外发布时的三个阶段

如果要把发布成本控制住，建议按三阶段推进，而不是一步到位冲包管理生态。

### 阶段 A：先上线 fat jar + 文档

目标：

- 先让外部用户能下载、能运行、能复现问题。

产物：

- fat jar
- 校验文件
- 本页文档

### 阶段 B：补平台压缩包 + `ai4j` 启动器

目标：

- 用户下载后直接敲 `bin/ai4j`，不再面对 jar 路径。

产物：

- Windows / Linux / macOS 压缩包
- `ai4j` / `ai4j.cmd`

### 阶段 C：补一键安装与包管理器

目标：

- 让 `curl | bash`、PowerShell 安装、包管理器分发成为稳定入口。

产物：

- `install.sh`
- `install.ps1`
- 后续可选：`winget` / `scoop` / `homebrew`

---

## 8. 普通用户该怎么选

如果你是：

- 仓库贡献者：直接源码打包，最快
- 团队内部使用者：优先下载平台压缩包
- IDE / 桌面宿主集成方：优先固定版本 Release，不建议直接绑源码构建
- 面向公开用户分发：必须走 GitHub Release，不要让用户自行找 jar 路径

---

## 9. 当前项目的建议落地顺序

结合当前仓库状态，最现实的落地顺序是：

1. 保持现有 fat jar 打包链路稳定
2. 增加 `ai4j` / `ai4j.cmd` 启动器
3. 增加 Release 资产打包与上传流程
4. 再补 `install.sh` / `install.ps1`
5. 最后再接 `winget` / `scoop` / `homebrew`

这样可以先把“可交付”做实，再逐步把“安装体验”做顺。

---

## 10. 相关阅读

1. [Coding Agent 快速开始](/docs/coding-agent/quickstart)
2. [CLI / TUI 使用指南](/docs/coding-agent/cli-and-tui)
3. [ACP 集成](/docs/coding-agent/acp-integration)

