# docs-site Entry Path Residue Cleanup

## Goal

Remove the remaining high-impact legacy guidance around `Start Here` and adjacent canonical pages so the docs-site homepage path stays on the current canonical tree.

## Scope

- In scope: `docs-site/docs/start-here/choose-your-path.md`, `docs-site/docs/start-here/quickstart-java.md`, `docs-site/docs/core-sdk/model-access/multimodal.md`, `docs-site/docs/glossary.md`, and harness closeout files under `docs/`
- Out of scope: broad cleanup across `ai-basics/`, `guides/`, or the full legacy `getting-started/` tree

## Steps

1. Diagnose the remaining high-impact old guidance near the docs entry path.
2. Normalize the entry-path pages and adjacent canonical links to current routes.
3. Run the required docs regression and record outcomes.
4. Update SSoT, walkthrough, and residual state.

## Acceptance Criteria

- [x] `Start Here` no longer routes new readers through deprecated Agent, Flowgram, or old getting-started entry paths
- [x] Adjacent canonical pages and glossary links point to current routes where relevant
- [x] Required regression was run or honestly recorded as skipped
- [x] Feature SSoT was updated
- [x] Walkthrough or residual note was produced when closing the task

## Worktree

- Path: current workspace
- Branch: `feature/chat-memory-summary-policy`
- If no dedicated worktree was used, why: this is a docs-only cleanup continuation inside the current docs-site workstream

## Links

- Feature SSoT row: `F-010`
- Regression gates: `RG-008`
- Previous task or dependency: `docs/plans/2026-04-28-docs-site-entry-path-residue-cleanup-design.md`
