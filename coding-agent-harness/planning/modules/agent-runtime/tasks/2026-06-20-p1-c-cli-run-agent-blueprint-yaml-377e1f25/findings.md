# Findings - P1-C CLI run Agent Blueprint YAML

## 发现记录

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-001 | P2 | `ai4j-cli run` 必须复用 P1-B `AgentFactory` 的 host-supplied dependency 边界，不能让 YAML 或 Factory 读取 token/profile secret。 | P1-B task package; `AgentBlueprintRunCommand` design | CLI host 解析 runtime config 后显式传入 `AgentModelClient`。 | mitigated |
| F-002 | P1 | 显式 `--profile` 或 YAML `model.profile` 不存在时，如果静默回退 default profile，可能误用错误 provider/key/model。 | self-review; added `shouldRejectMissingProfileInsteadOfFallingBackToDefault` | 在 `AgentBlueprintRunCommand` 中校验 requested profile 必须等于 resolved effective profile，否则报错。 | closed |
| F-003 | P1 | `sandbox.enabled=true` 容易让用户误以为 CLI 已创建 VM/容器。 | P1-A/P1-B docs and tests | 默认拒绝 sandbox declaration；`--allow-sandbox-declaration` 只允许声明通过，不创建真实 sandbox。 | closed |
| F-004 | P2 | docs-site 的 Agent Blueprint 页面需要从 P1-A/P1-B 更新到 P1-C 当前能力，避免把 CLI run 写成“后续建议”。 | docs-site scan | 更新 CLI run 小节、参数表和当前边界/下一步。 | in-progress |
| F-005 | P2 | P1-C 改动同时触发 RG-004 和 RG-008，需要同步 Regression SSoT / Cadence Ledger。 | AGENTS.md hard rule; Cadence Ledger | 在本任务中更新固定回归面与 batch log。 | in-progress |

## 残余问题

- P1-C 不做 live provider 测试；默认只使用 fake/local tests，真实 provider 由后续 opt-in gate 覆盖。
- P1-C 不实现真实 sandbox provider；P2 Sandbox SPI / P3 coding routing 继续承接。
- P1-C 不实现最终 `ai4j` 一键安装包和 TUI 体验；P4 CLI/TUI 任务承接。
