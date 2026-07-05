# 收口记录：plugin ecosystem hardening fixes

## 摘要

已按 review 发现依次修复插件生态 hardening 问题：版本对齐到 2.4.0、CLI runtime inspect 展示 lifecycle hooks、插件资源 strict read 防止宿主 classloader 兜底、ask_user 超大参数截断、docs-site 明确 manifest permissions 是 host-policy 元数据。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-extension-api`、`ai4j-plugin-ask-user`、`ai4j-cli`、`ai4j-coding`、`docs-site`、`docs/05-TEST-QA` |
| 新增文件 | `ai4j-extension-api/src/test/java/io/github/lnyocly/ai4j/extension/resource/ExtensionResourceResolverTest.java` |
| 删除文件 | 无 |
| 不在范围内 | 完整权限引擎、插件市场/远端安装协议、lifecycle hook 可变拦截、真实发布 |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| 远端 main 新鲜度 | `git fetch --all --prune`; `git rev-list --left-right --count main...origin/main` | pass, `0 0` | terminal output |
| Extension API | `mvn -pl ai4j-extension-api -DskipTests=false test` | pass, 26 tests | `progress.md` E-002 |
| Ask User plugin | `mvn -pl ai4j-plugin-ask-user -am -DskipTests=false test` | pass, AskUser 7 + extension API 26 | `progress.md` E-002 |
| CLI targeted | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | pass, 30 tests | `progress.md` E-002 |
| Coding targeted | `mvn -pl ai4j-coding -am -Dtest=CodingSkillSupportTest -DfailIfNoTests=false -DskipTests=false test` | pass, 3 tests | `progress.md` E-002 |
| Package smoke | `mvn -DskipTests package` | pass, 11 reactor projects | Cadence SRB-060 |
| Docs build/type | first `npm run build`; then `npm ci`; `npm run build`; `npm run typecheck` | first failed only due missing ignored `node_modules`; retry build/typecheck pass | Cadence SRB-060 |
| Diff hygiene | `git diff --check` | pass; CRLF working-copy warnings only | terminal output |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self-review | 无 P0/P1/P2 open finding | 可提交 PR/merge 前 CI | `review.md` |
| regression review | 目标本地回归通过 | 更新 Regression SSoT / Cadence | `docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 远端 PR CI 尚未运行 | coordinator | yes | push/PR 后观察 required checks |
| docs-site npm audit advisories | dependency owner | yes | 独立依赖治理，不阻塞本轮 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是，checked-none |
| 经验候选详情文件 | `lesson_candidates.md` |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 回归批次 | `docs/05-TEST-QA/Cadence-Ledger.md` SRB-060 |
