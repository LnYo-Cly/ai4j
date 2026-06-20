# 收口记录：Extension plugin contract hardening

## 摘要

本任务修复 extension plugin review 中的主要契约问题：公共 ID/name 现在有统一严格格式；`ExtensionValidator` 会真实解析 tool schema 并检查 AI4J 当前 mapper 需要的基础结构；CLI extension 参数校验与 `/command` 兼容路径一致；生成插件 scaffold 的测试从文本断言提升到 JavaCompiler 编译和 ServiceLoader 烟测；docs-site 明确 `apply(...)` 轻量注册、`enable(...)` 整包信任和当前非 tool 资源 allowlist 边界。

## 范围

| 范围 | 详情 |
| --- | --- |
| 变更模块 | `ai4j-extension-api`, `ai4j-cli`, `docs-site`, Regression SSoT/Cadence, task package |
| 新增文件 | `ai4j-extension-api/src/main/java/io/github/lnyocly/ai4j/extension/validation/ExtensionToolSchemaValidator.java` |
| 删除文件 | 无 |
| 不在范围内 | 远程 marketplace、CLI 自动安装依赖、runtime jar hotload、provider 自动注册、非 tool 资源细粒度 allowlist |

## 验证

| 检查 | 命令或过程 | 结果 | 证据 |
| --- | --- | --- | --- |
| Extension API | `mvn -pl ai4j-extension-api -DskipTests=false test` | pass, 16 tests | `progress.md` |
| Agent extension adapter | `mvn -pl ai4j-agent -am -Dtest=ExtensionAgentToolsTest -DfailIfNoTests=false -DskipTests=false test` | pass, 5 tests | `progress.md` |
| Official plugin | `mvn -pl ai4j-plugin-ask-user -am -DfailIfNoTests=false -DskipTests=false test` | pass | `progress.md` |
| CLI targeted scaffold/args | `mvn -pl ai4j-cli -am -Dtest=Ai4jCliTest -DfailIfNoTests=false -DskipTests=false test` | pass, 22 tests | `progress.md` |
| docs-site typecheck | `npm run typecheck` in `docs-site/` | pass | `progress.md` |
| docs-site build | `npm run build` in `docs-site/` | pass | `progress.md` |
| monorepo package smoke | `mvn -DskipTests package` | pass, 11 reactor projects | `progress.md` |
| diff whitespace | `git diff --check` | pass with CRLF warnings only | `progress.md` |

## 审查结论

| 来源 | 重要发现 | 处理 | 证据 |
| --- | --- | --- | --- |
| self adversarial review | 0 open material findings | 可提交人工确认；P3 非 tool allowlist 作为 accepted residual | `review.md` |

## 残余风险

| 风险 | Owner | 是否接受 | 跟进 |
| --- | --- | --- | --- |
| 非工具资源仍无 command / Skill / Prompt / Guardrail 粒度 allowlist | maintainer | yes | 后续插件权限模型设计任务 |
| 人工 review confirmation 未由用户侧执行 | human | yes | 推送后由用户决定是否确认或退回 |

## 经验沉淀反思

| 问题 | 答案 |
| --- | --- |
| 是否完成经验候选检查？ | 是 |
| 经验候选详情文件 | `lesson_candidates.md` |
| 结果 | no-candidate-accepted；本任务没有新增可复用 harness 流程规则 |

## 收口链接

| 产物 | 链接 |
| --- | --- |
| 任务计划 | `task_plan.md` |
| 审查记录 | `review.md` |
| 进度记录 | `progress.md` |
| 发现记录 | `findings.md` |
| Plugin packages docs | `../../../docs-site/docs/core-sdk/extension/plugin-packages.md` |
| Plugin author cookbook | `../../../docs-site/docs/core-sdk/extension/plugin-author-cookbook.md` |

Closeout Status: closed
