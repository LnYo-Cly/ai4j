# P5 Remote Agent Runner SPI contract - Execution Strategy

## Strategy

本任务采用 contract-first 小切片：先在 `ai4j-agent` 中定义 provider-neutral runner SPI，再用 fake runner 单测证明合同可运行，最后用 docs-site 说明边界和后续产品化路径。

## Implementation Boundaries

- `ai4j-agent`：新增 `io.github.lnyocly.ai4j.agent.runner` 包。
- `docs-site`：新增 Agent Runner SPI 技术页，并从 sidebar / roadmap 可达。
- `docs/05-TEST-QA`：更新 RG-002 / SRB-059 证据。
- 不修改 `ai4j-coding`、`ai4j-cli` runtime 行为。
- 不新增 Maven 模块，不接真实云 provider，不使用任何 provider token。

## Validation Plan

1. Targeted：`mvn -pl ai4j-agent -am "-Dtest=AgentRunnerSpiContractTest" -DskipTests=false -DfailIfNoTests=false test`。
2. Broad agent：`mvn -pl ai4j-agent -am -DskipTests=false test`。
3. Docs：`npm --prefix docs-site run build`。
4. Hygiene：`git diff --check`。
5. Harness：`npx --yes coding-agent-harness status --json .`。

## Risk Controls

| Risk | Control |
| --- | --- |
| Runner 与 Sandbox SPI 混淆 | docs-site 明确 Host-driven sandbox tools vs Remote Agent Runner。 |
| 过早产品化 | 只交付 SPI contract + fake tests，不承诺真实云平台。 |
| Secret 泄漏 | 示例只用 provider/profile/workspace/id，不写 token/cookie/key。 |
| Java 8 兼容 | 不使用 records/sealed/var/stream-only API，沿用现有 builder/defensive-copy 风格。 |

## Handoff

后续任务可以继续：

- Runner provider plugin contribution。
- CLI/TUI runner status / attach / logs UX。
- Remote Agent productization guide。
- 是否新增独立 `ai4j-agent-runner` Maven module 的决策任务。
