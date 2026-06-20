# P5 Remote Agent Runner SPI contract - Findings

## Findings

| ID | Severity | Finding | Evidence | Required Action | Status |
| --- | --- | --- | --- | --- | --- |
| F-001 | P1 | Remote Runner 必须 contract-first，不能在本任务新增真实云 runner 或大 Maven 模块。 | task_plan.md；新增代码仅 `ai4j-agent` runner 包 | 保持 fake-testable；真实 provider 后续独立任务。 | mitigated |
| F-002 | P1 | Runner SPI 和 Sandbox SPI 容易混淆。 | docs-site `remote-agent-runner-spi.md` 第 7 节 | 文档明确 Host-driven sandbox tools vs Remote Agent Runner。 | mitigated |
| F-003 | P1 | Spec/config 可能被误用来保存 secret。 | docs-site 安全边界；代码不读取 token | 文档声明 token 只能来自宿主 secret store/env；测试不含真实 token。 | mitigated |
| F-004 | P2 | `origin/dev` 基线缺少 root `AGENT.md` 和部分 legacy reference 文件。 | local file check | 以 AGENTS.md 和当前模块代码为准；不要伪造读取缺失文件。 | accepted-residual |

## Residual

- 没有真实 provider discovery / plugin contribution；后续在 `ai4j-extension-api` 任务中处理。
- 没有 CLI `/runner` 或 `/sandbox create/list/logs` UX；后续 `ai4j-cli` 任务处理。
- 没有新增 `ai4j-agent-runner` Maven 模块；待产品化需求和 provider contract 稳定后再决策。
