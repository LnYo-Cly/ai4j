# first wave project upgrades - 发现记录

本文件记录任务执行中形成的判断、事实和技术决策。阻塞性问题写入 `review.md`；本轮没有阻塞性发现。

## 研究发现

### release GPG 可执行文件路径

- 背景：多个 release profile 中存在只适用于本机的 Windows GPG 绝对路径。
- 发现：POM 中的 `executable` 可以改为 Maven 属性 `${gpg.executable}`，默认值使用 `gpg`，本地或 CI 可按需覆盖。
- 影响：移除单人机器路径依赖，同时不改变 maven-gpg-plugin 的签名目标。
- 后续：发布前在具备 GPG 和签名凭据的环境执行真实 signing 验证。

### 本地生成输出边界

- 背景：`output/imagegen/smoke-test.png` 作为本地生成输出出现在 Git dirty 视图中。
- 发现：`output/` 属于临时或生成产物目录，加入 `.gitignore` 可避免误提交。
- 影响：仓库提交边界更干净，不会影响业务源码。
- 后续：如未来需要保留示例图片，应放入明确的 docs 或 fixtures 路径并单独登记。

## 技术决策

| 决策 | 选择 | 原因 | 替代方案 | 状态 |
| --- | --- | --- | --- | --- |
| GPG executable 配置 | 使用 `${gpg.executable}`，默认 `gpg` | 保持 release profile 可移植，并允许 CI 或开发者覆盖。 | 删除 `executable` 配置；保留本机路径。 | accepted |
| 生成输出 Git 边界 | `.gitignore` 忽略 `output/` | 该目录是本地生成输出，不应参与业务提交。 | 删除本地文件；将图片纳入仓库。 | accepted |
| 后续升级拆分 | 本轮只做低风险配置切片 | module-parallel 和回归分层会触及治理面，适合独立任务。 | 在同一任务里继续扩大范围。 | accepted |

## 待确认问题

| 问题 | 当前判断 | Owner | 截止点 |
| --- | --- | --- | --- |
| 是否执行真实 release signing 验证 | 本轮不执行；需要具备 GPG、凭据和发布目标环境。 | release owner | 发布前 |
| 是否继续 module-parallel 与回归分层升级 | 建议继续，但应作为后续 harness 任务。 | coordinator / user | 下一轮升级前 |
