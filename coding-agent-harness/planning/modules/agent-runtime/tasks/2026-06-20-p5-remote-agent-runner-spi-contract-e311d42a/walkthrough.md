# P5 Remote Agent Runner SPI contract - Walkthrough

## Summary

本任务新增 `ai4j-agent` 的 Remote Agent Runner SPI contract，用于后续把完整 Agent loop 跑到远端 sandbox / hosted workspace。当前只做合同、fake tests 和 docs-site，不接真实云 provider。

## Changed surfaces

- `ai4j-agent/src/main/java/io/github/lnyocly/ai4j/agent/runner/`
- `ai4j-agent/src/test/java/io/github/lnyocly/agent/AgentRunnerSpiContractTest.java`
- `docs-site/docs/agent/remote-agent-runner-spi.md`
- `docs-site/docs/agent/sdk-roadmap.md`
- `docs-site/sidebars.ts`

## Verification

- `mvn -pl ai4j-agent -am "-Dtest=AgentRunnerSpiContractTest" -DskipTests=false -DfailIfNoTests=false test` passed with 5 tests.\n- `mvn -pl ai4j-agent -am -DskipTests=false test` passed with extension API 25, core 103, agent 124 tests.\n- `npm --prefix docs-site run build` passed after local ignored dependency install.\n- `git diff --check` passed with CRLF warnings only.\n- `npx --yes coding-agent-harness status --json .` reported failures=0 before commit, with dirty-state warning only.

## Residual

- 真实 provider / plugin contribution 后置。
- CLI runner UX 后置。
- 独立 runner Maven module 后置。
