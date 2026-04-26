# Harness Bootstrap - Findings

## Discoveries

### Repository shape

- Why it mattered: the existing root guidance still described the repo as a two-module layout
- What was found: the actual repository is an 8-module Maven monorepo plus `docs-site/` and `ai4j-flowgram-webapp-demo/`
- Impact on plan: the harness had to be sized as `Full`, not `Lite` or `Standard`

### Existing documentation drift

- Why it mattered: the harness depends on accurate repo entry guidance
- What was found: `AGENTS.md` existed but was stale; `docs/plans`, `docs/tasks`, and `docs/archive` contained useful history but not a standardized SSoT-based workflow
- Impact on plan: preserve legacy docs, add numbered harness directories, and route all new work into the new structure

### Regression reality

- Why it mattered: a useful harness needs fixed verification entrypoints
- What was found: multiple modules already have JUnit suites, but the repo lacked a unified regression map and cadence rules; docs CI existed, core CI did not
- Impact on plan: seed Regression SSoT and Cadence Ledger with module and surface-oriented gates

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Harness scale | Full | Large monorepo with many active surfaces and cross-module change risk | Standard |
| Legacy docs handling | Preserve in place | Low risk and avoids losing historical context | Immediate migration |
| Root guidance | Replace `AGENTS.md` | Existing file was materially outdated | Patch partially |
