# 跨仓调试 / Cross-Repo Debugging

Context Doc Type: cross-repo-debugging
Owner: project coordinator
Last Verified: 2026-06-04
Confidence: medium

## Debug Flow

1. 先定位失败的 interface 或 flow。
2. 读 `coding-agent-harness/context/architecture/service-catalog.md`，确认归属和上下游服务。
3. 读对应的 `coding-agent-harness/context/integrations/` 契约。
4. 如果有外部资料，读 `coding-agent-harness/context/development/external-context/<service-key>.md`；当前用户确认没有外部资料包。
5. 优先用本仓代码、README、CI workflow 和现有 `docs/05-TEST-QA/` 事实验证，不把外部系统行为写成已验证事实。

## Known Failure Modes

| Symptom | Likely Service | First Check | Source Evidence | Last Verified | Confidence |
| --- | --- | --- | --- | --- | --- |
| Maven module tests fail only in provider-facing suites | provider APIs / credentials | Confirm whether the test needs live credentials; prefer local/mock tests unless task explicitly requires live validation. | `AGENTS.md`; `docs/05-TEST-QA/Regression-SSoT.md` | 2026-06-04 | high |
| FlowGram web demo cannot call backend | `flowgram-web-demo` / `flowgram-demo` | Verify backend on `127.0.0.1:18080` and web proxy `/flowgram` config. | `ai4j-flowgram-webapp-demo/rsbuild.config.ts`; `ai4j-flowgram-demo/README.md` | 2026-06-04 | high |
| Docs-site build/typecheck OOM or cleanup failure on Windows | `docs-site` local environment | Retry with `NODE_OPTIONS=--max-old-space-size=8192`; distinguish build failure from Windows file-lock cleanup residual. | `docs/05-TEST-QA/Regression-SSoT.md`; `docs/05-TEST-QA/Cadence-Ledger.md` | 2026-06-04 | high |
| CLI/TUI/ACP behavior regresses after coding runtime change | `ai4j-cli` / `ai4j-coding` | Run both changed module tests and package smoke per Cadence Ledger. | `.github/workflows/java-regression.yml`; `docs/05-TEST-QA/Cadence-Ledger.md` | 2026-06-04 | high |
