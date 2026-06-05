# remove ai4j sdk maintainer skill - 任务计划

Task Contract: harness-task/v1

## 目标

删除对外公开的 `$ai4j-sdk` maintainer Skill，避免它与 `AGENTS.md` 和 `coding-agent-harness/` 重复维护；docs-site 只推荐 `$ai4j-app-builder`。

## 范围

- 删除 `skills/ai4j-sdk/**`。
- 更新 `docs-site/README.md`，只保留 `ai4j-app-builder` 安装命令。
- 更新 `skills/ai4j-app-builder/SKILL.md`，把仓库维护路由到 `AGENTS.md` 和 harness。
- 验证剩余 Skill 与 docs-site。

## 非目标

- 不删除历史任务、评测报告或已提交的旧证据。
- 不修改 Java SDK 代码。
- 不修改 release/publish 流程。
- 不推送远程。

## 执行步骤

| Step | Status | Evidence |
| --- | --- | --- |
| 创建并启动 harness 任务 | done | `new-task`、`task-start` commits |
| 删除 maintainer Skill | done | commit `f891bdd` |
| 收敛 docs-site README | done | commit `f891bdd` |
| 校验剩余 Skill | done | `quick_validate.py skills\ai4j-app-builder` |
| 构建 docs-site | done | `npm run build` |
| 修复任务材料并提交 review | pending | 本任务材料和 lifecycle 命令 |

## 验证计划

- `python C:\Users\1\.codex\skills\.system\skill-creator\scripts\quick_validate.py skills\ai4j-app-builder`
- `rg -n "ai4j-sdk|\$ai4j-sdk|--skill ai4j-sdk" docs-site\README.md skills\ai4j-app-builder skills`
- `npm run build` in `docs-site/`
- `npx --yes coding-agent-harness status --json .`

## Review Criteria

- 对外只有一个用户侧 Skill 入口。
- 仓库维护说明回到 `AGENTS.md` 和 harness，不引入第二份维护规则。
- 不破坏 docs-site 构建。
- 不留下 active Skill 残留。
