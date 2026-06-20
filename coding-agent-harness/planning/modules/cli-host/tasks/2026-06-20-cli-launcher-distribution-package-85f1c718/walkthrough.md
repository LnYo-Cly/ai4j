# CLI launcher distribution package - Walkthrough

## 任务结果

本任务为 `ai4j-cli` 增加源码构建 distribution 包。`mvn -pl ai4j-cli -am -DskipTests package` 现在会生成：

- `ai4j-cli/target/ai4j-cli-2.3.0-jar-with-dependencies.jar`
- `ai4j-cli/target/ai4j-cli-2.3.0-dist.zip`
- `ai4j-cli/target/ai4j-cli-2.3.0-dist.tar.gz`

发行包内包含 `bin/ai4j`、`bin/ai4j.cmd`、fat jar、`providers.example.json`、`workspace.example.json` 和 README。

## 关键改动

- `ai4j-cli/pom.xml`：新增 filtered dist resources 和 dist assembly execution。
- `ai4j-cli/src/assembly/dist.xml`：定义 zip / tar.gz 包结构。
- `ai4j-cli/src/main/dist/**`：新增 launcher、示例配置和 README。
- `CliDistributionLayoutTest`：锁定发行包源布局、示例配置和 secret-pattern。
- docs-site：更新 quickstart / install-and-release，说明 install 脚本、dist 包、fat jar 三条路径。
- 回归治理：更新 RG-004，新增 SRB-062。

## 验证

- `mvn -pl ai4j-cli -am "-Dtest=CliDistributionLayoutTest" -DskipTests=false -DfailIfNoTests=false test`：通过，3 tests。
- `mvn -pl ai4j-cli -am -DskipTests package`：通过，生成 dist zip / tar.gz。
- Archive inspection：必需 entry 全部存在。
- `ai4j.cmd --help` smoke：通过。
- `npm --prefix docs-site run build`：待最终记录。
- `git diff --check`：待最终记录。
- `npx --yes coding-agent-harness status --json .`：待最终记录。

## 残余

- GitHub Release asset 上传、checksum、release notes、Linux/macOS shell smoke 后续另开任务。
- 在线 install 脚本仍以下载 fat jar + 本地生成 launcher 为主，和 dist 包互补，不在本任务合并。

## Lessons Reflection

- 本任务没有产生需要沉淀到共享 governance lessons 的新规则；`dist/` 被 `.gitignore` 误伤的问题已经用 task-local finding 和精确反忽略处理。
- Lesson decision：checked-none: cli-distribution-bounded-slice
