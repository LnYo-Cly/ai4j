# 收口记录：P2-D E2B sandbox provider

## 摘要

为 agent sandbox SPI 新增 E2B provider：通过 E2B control API（`X-API-Key`）创建/销毁沙箱，
经 Connect server-streaming `process.Process/Start`（`Authorization: Bearer`）执行命令。
行为对齐 Daytona，零既有文件改动。实现 + live 烟测完成，待 PR 评审。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | ai4j-agent |
| 新增文件 | ai4j-agent/src/main/java/.../sandbox/e2b/（7 源文件）；ai4j-agent/src/test/.../（5 测试文件） |
| 删除文件 | 无 |
| 不在范围内 | cancel(SendSignal)、listArtifacts(filesystem)、create labels/metadata；core/CLI/starter |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| 离线单测 | `mvn -pl ai4j-agent -am -Dtest=E2B* test` | 15 测试全绿 | progress.md |
| live 烟测 | `-Plive-provider-tests -Dtest=E2BSandboxLiveSmokeTest`（E2B_API_KEY） | exit 0 + exit 7 全绿 | progress.md |
| 全模块回归 | `mvn -pl ai4j-agent -am test` | 148 测试 0 失败 | progress.md |
| diff 卫生 | `git diff --check` | 无 whitespace error | progress.md |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| coordinator self-review | 无阻塞发现 | v1 范围外能力记 deferred | `review.md` |
| PR reviewer | 待 PR | pending | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| cancel/listArtifacts/create-labels 未实现 | coordinator | yes | 后续任务 |
| E2B key 泄露在会话历史 | user | no | 合入后轮换 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，LC-20260623-001（外部协议实现须 live 实测） |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 分支 | `feat/e2b-sandbox-provider` |
