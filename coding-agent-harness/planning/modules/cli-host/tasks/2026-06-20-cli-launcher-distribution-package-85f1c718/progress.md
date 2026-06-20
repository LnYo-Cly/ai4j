# CLI launcher distribution package - 进度

## 状态：进行中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

## 进度记录

证据使用 `type:path:summary` 格式。

### [2026-06-20 19:05] - task-start

- 做了什么：启动 CLI launcher distribution package，实现源码构建 dist zip/tar.gz、跨平台 launcher、示例配置、docs-site 更新和 package smoke。
- 验证结果：任务进入进行中。
- 下一步：新增 Maven dist assembly 和 launcher 源文件。
- 证据：command:TARGET:.:`npx --yes coding-agent-harness task-start MODULES/cli-host/2026-06-20-cli-launcher-distribution-package-85f1c718 ...` succeeded

### [2026-06-20 19:15] - targeted layout test

- 做了什么：新增 `CliDistributionLayoutTest`，覆盖 source layout、示例配置 JSON parse、generic secret-pattern、assembly descriptor。
- 验证结果：通过。
- 下一步：运行 package smoke。
- 证据：command:TARGET:.:`mvn -pl ai4j-cli -am "-Dtest=CliDistributionLayoutTest" -DskipTests=false -DfailIfNoTests=false test` -> 3 tests passed

### [2026-06-20 19:19] - package smoke and archive inspection

- 做了什么：新增 filtered dist resources、`src/assembly/dist.xml`、`src/main/dist/bin/ai4j`、`ai4j.cmd`、示例配置和包内 README；修正 Maven `@project.version@` filtering。
- 验证结果：package 成功，生成 `ai4j-cli-2.3.0-dist.zip` / `.tar.gz`；zip 包包含 `bin/ai4j`、`bin/ai4j.cmd`、fat jar、providers/workspace example 和 README；zip 内 launcher 版本过滤成功；`ai4j.cmd --help` smoke 成功。
- 下一步：docs-site build、diff check、Harness status。
- 证据：command:TARGET:.:`mvn -pl ai4j-cli -am -DskipTests package` -> BUILD SUCCESS; fixture:TARGET:ai4j-cli/target/ai4j-cli-2.3.0-dist.zip:required entries present; command:TARGET:.:`cmd /c ai4j-cli\src\main\dist\bin\ai4j.cmd --help` with `AI4J_CLI_JAR` override -> help output

### [2026-06-20 19:24] - docs and regression governance update

- 做了什么：更新 docs-site quickstart / install-and-release，记录在线安装脚本、dist 包、fat jar 三条路径；更新 RG-004 和 SRB-062。
- 验证结果：文件已更新，最终 build/status 待跑。
- 下一步：运行 docs-site build、diff check、Harness status。
- 证据：diff:TARGET:docs-site/docs/coding-agent/install-and-release.md:distribution state updated; diff:TARGET:docs/05-TEST-QA/Cadence-Ledger.md:SRB-062 added

## 残余

- GitHub Release asset 上传、checksum、Linux/macOS 真实 shell smoke 不在本任务范围，后续 release automation 任务处理。
- 本任务没有使用真实 provider token；用户提供的 token 不写入仓库、不回显、不进入日志。

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-pr
- Registry update needed：review / complete 后由 Harness CLI 同步。
- Harness Ledger update needed：review / complete 后由 Harness CLI 同步。
- 负责人：coordinator

### [2026-06-20 19:32] - broad CLI and docs validation

- 做了什么：运行 docs-site build 和 RG-004 broad CLI tests。
- 验证结果：docs-site build 通过；`mvn -pl ai4j-cli -am -DskipTests=false test` 通过，extension API 31、core 103、agent 126、coding 61、CLI 295 tests。
- 下一步：运行 diff check、secret pattern scan、Harness status，然后提交。
- 证据：command:TARGET:docs-site:`npm --prefix docs-site run build` -> success; command:TARGET:.:`mvn -pl ai4j-cli -am -DskipTests=false test` -> BUILD SUCCESS, CLI 295 tests
