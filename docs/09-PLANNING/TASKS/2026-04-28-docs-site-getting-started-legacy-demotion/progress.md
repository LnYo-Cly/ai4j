# docs-site getting-started Legacy Demotion - Progress

## Status

`completed`

## Log

### 2026-04-28 02:45 - Diagnose

- What changed: scanned the `getting-started/` tree and confirmed that its high-traffic pages still form a self-reinforcing legacy entry loop.
- Verification: `rg -n "(/docs/getting-started/|\\]\\(/docs/getting-started/|/docs/guides/|/docs/ai-basics/)" docs-site/docs/getting-started`
- Next: add legacy notices and repoint the top-level guidance to current canonical pages.

### 2026-04-28 03:20 - Repoint legacy guidance

- What changed: finished the remaining high-level route cleanup in `installation.md` and `quickstart-springboot.md`, replacing the last `/docs/getting-started/` self-loop guidance with canonical `start-here/`, `core-sdk/`, `spring-boot/`, and `coding-agent/` links.
- Verification: `rg -n "/docs/getting-started/" docs-site/docs/getting-started`; `rg -n "/docs/coding-agent/release-and-installation" docs-site/docs/getting-started -g "*.md"`
- Next: run `RG-008` and record the environment residual honestly.

### 2026-04-28 04:05 - Validate and close out

- What changed: ran the required docs-site regression for this task and confirmed the edited legacy pages no longer expose high-level `/docs/getting-started/` entry links.
- Verification: `$env:NODE_OPTIONS='--max-old-space-size=8192'; npm run typecheck`; `rg -n "/docs/getting-started/" docs-site/docs -g "!getting-started/**" -g "!guides/**"`; `npx docusaurus build --out-dir build-getting-started-legacy-verify`
- Next: update Feature SSoT, Regression SSoT, Cadence Ledger, and walkthrough.

## Residual

- Windows may still lock Docusaurus output and webpack cache artifacts, causing `EPERM` failures during final cleanup even when client/server bundle compilation succeeds
- In this workspace, `npm run typecheck` may need `NODE_OPTIONS=--max-old-space-size=8192` to avoid V8 heap exhaustion
- The legacy `getting-started/` bodies are intentionally retained as archival deep-dive content; this task only removed their competing entry behavior
