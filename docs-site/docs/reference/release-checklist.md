---
sidebar_position: 3
---

# Release Checklist

这页是维护者发布 AI4J 到 Maven Central 和 GitHub Release 前后的最小检查清单。

## 版本策略

AI4J 当前采用 **全模块同号发布**：

- 发布版：所有发布模块使用同一个稳定版本，例如 `2.4.2`
- 开发分支：所有 Maven POM 使用下一个 `SNAPSHOT`，例如 `2.4.3-SNAPSHOT`
- README / docs 示例：写最新已发布稳定版，不写 `SNAPSHOT`

只改 README、docs-site 或 demo 时，不需要发布新的 Maven 版本。

## 发布前

1. 确认当前分支干净并从 `main` 切出 release 修复分支。
2. 把所有 Maven POM 从 `*-SNAPSHOT` 改成同一个 release 版本。
3. 同步 README、README-EN 和 docs-site 里的用户安装版本。
4. 确认 `ai4j-bom` 覆盖需要对齐的发布模块。
5. 确认 release profile 不发布聚合根项目、demo artifact 或 CLI fat jar。
6. 确认 Maven `settings.xml` 里存在 Central server id，且密钥不进仓库。
7. 确认 GPG agent / Kleopatra 可以完成签名。

## 本地验证

```powershell
mvn -DskipTests package
mvn -P release -DskipTests clean verify
```

如果修改 docs-site：

```powershell
npm --prefix docs-site ci
npm --prefix docs-site run build
```

## 发布

```powershell
mvn -P release -DskipTests clean deploy
```

记录 Central deployment id。若 Central 返回 `validated` 但要求手动发布，在 Sonatype Central Portal 或 Publisher API 中发布该 deployment。

## 发布后验证

1. Maven Central deployment 状态为 `PUBLISHED`。
2. `maven-metadata.xml` 的 `latest` 和 `release` 等于本次版本。
3. 主模块的 `pom`、`jar`、`sources`、`javadoc` 和 `.asc` 可下载。
4. `ai4j-cli-<version>-jar-with-dependencies.jar` 不存在。
5. 创建 GitHub tag / Release，说明版本变化和 Maven 坐标。
6. 新分支把所有 Maven POM bump 到下一个 `SNAPSHOT` 并合回 `main`。

## 收口

- 删除已合并 release / bump 分支。
- 确认本地 `main` 与 `origin/main` 对齐。
- HA task 记录 deployment id、GitHub release URL、验证命令和残余风险。

