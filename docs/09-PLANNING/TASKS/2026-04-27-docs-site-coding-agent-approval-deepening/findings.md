# docs-site Coding Agent Approval Deepening - Findings

## Discoveries

### Approval docs are conceptually right but implementation-light

- Why it mattered: the user question was not whether approval exists, but how the runtime actually intercepts a tool call.
- What was found: the current docs already mention `ToolExecutorDecorator` and ACP `session/request_permission`, but they stop short of explaining the assembly-time wrapping chain, the concrete `SAFE` trigger rules, and the CLI/TUI versus ACP execution split.
- Impact on plan: keep the change narrow and concentrate the added depth in the pages that already own tools, session runtime, and runtime architecture.

### Workspace safety is split between approval and path-resolution concerns

- Why it mattered: readers can easily confuse “approval” with “path sandboxing”, even though they are different controls.
- What was found: most file and shell surfaces resolve through `WorkspaceContext`, but `WriteFileToolExecutor` resolves absolute paths directly and has a test that explicitly allows writing outside the workspace.
- Impact on plan: document the distinction honestly, including the current `write_file` caveat, instead of implying a stronger sandbox than the implementation provides.

### docs-site regression is stable with raised Node heap

- Why it mattered: the task had to close with `RG-008`, and recent docs-site work had recurring Windows build residuals.
- What was found: the first `npm run typecheck` attempt hit a default Node heap OOM, but rerunning with `NODE_OPTIONS=--max-old-space-size=8192` passed, and `npx docusaurus build --out-dir build-approval-deepening-verify` completed successfully in this run.
- Impact on plan: record the successful verification honestly, but keep the docs-site gate mapped as partial until the standard `npm run build` path is revalidated cleanly in the same Windows environment.

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Task scope | create a new narrow docs task | the earlier Agent/Coding Agent canonical cleanup is already closed and this is a focused follow-up | reopen `F-007` and blur two separate closeout records |
| Primary owning page | deepen `tools-and-approvals.md` first | it is the natural canonical page for approval semantics and extension boundaries | spread the explanation thinly across only runtime pages |
| Security wording | document the current `write_file` caveat explicitly | the docs should not overstate workspace safety | hide the caveat until code hardening happens |
