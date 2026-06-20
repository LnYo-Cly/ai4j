# CLI launcher distribution package

## Task ID

`2026-06-20-cli-launcher-distribution-package-85f1c718`

## 创建日期

2026-06-20

## 一句话结果

为 `ai4j-cli` 补齐源码构建可产出的 CLI distribution 包，让用户解压后可以通过 `bin/ai4j` 或 `bin/ai4j.cmd` 启动同一个 Java CLI。

## 完成后能得到什么

本任务完成后，`mvn -pl ai4j-cli -am -DskipTests package` 不只生成 fat jar，还会生成 `ai4j-cli-<version>-dist.zip` 和 `ai4j-cli-<version>-dist.tar.gz`。发行包内包含 Unix/Windows launcher、fat jar、provider/workspace 示例配置和 README。docs-site 会同步说明 fat jar、dist 包、在线 install 脚本三条路径的边界，后续发布任务可在此基础上继续做 checksum、GitHub Release asset 上传和跨平台 smoke。

## 交付物

- 可见产物：`ai4j-cli/target/ai4j-cli-2.3.0-dist.zip`、`ai4j-cli/target/ai4j-cli-2.3.0-dist.tar.gz`。
- 修改位置：`ai4j-cli/pom.xml`、`ai4j-cli/src/assembly/dist.xml`、`ai4j-cli/src/main/dist/**`、`CliDistributionLayoutTest`、docs-site install/quickstart 页面、Regression SSoT / Cadence Ledger。
- 验证证据：targeted JUnit、package smoke、archive inspection、launcher help smoke、docs-site build、diff check、Harness status。

## 第一眼应该看什么

1. `ai4j-cli/src/assembly/dist.xml`：发行包结构。
2. `ai4j-cli/src/main/dist/README.md`：用户可见的包内说明。
3. `ai4j-cli/src/test/java/io/github/lnyocly/ai4j/cli/CliDistributionLayoutTest.java`：布局、示例配置和 secret-pattern 回归。
4. `docs-site/docs/coding-agent/install-and-release.md`：技术文档的真实现状说明。

## 边界

- 范围内：源码构建 distribution 包、launcher、示例配置、文档和本地确定性 package smoke。
- 范围外：不做 GitHub Release workflow、checksum、Maven Central 发布、真实 provider 调用、系统 PATH 安装器重构。
- 停止条件：如要改变 CLI 配置解析、真实安装脚本语义或发布凭证链路，必须另开任务。

## 完成判断

- [x] Maven package 生成 dist zip / tar.gz。
- [x] 发行包包含 `bin/ai4j`、`bin/ai4j.cmd`、fat jar、示例配置和 README。
- [x] launcher 不硬编码 provider/model/key/local path，只定位 Java 和 fat jar。
- [x] docs-site 不再声称仓库缺少 launcher/distribution 包。
- [ ] docs-site build、diff check、Harness status 完成并记录。

## 执行合同

- Owner：coordinator
- 生命周期状态：进行中
- 必需文件：`INDEX.md`、`task_plan.md`、`execution_strategy.md`、`visual_map.md`、`progress.md`、`findings.md`、`review.md`
- 完成条件：验证证据必须记录到 `progress.md`

## 当前下一步

运行 docs-site build、diff check、Harness status；若通过则提交、推送并创建 PR。
