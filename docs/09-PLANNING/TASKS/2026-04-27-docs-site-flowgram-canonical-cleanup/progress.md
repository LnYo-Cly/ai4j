# docs-site Flowgram Canonical Cleanup - Progress

## Status

`completed`

## Log

### 2026-04-27 00:00 - Task registration and subtree scan

- What changed: created the design record and task three-piece, scanned the `flowgram/` docs tree for old canonical-path residue, and reviewed the current canonical bridge pages
- Verification: targeted `rg` path scan plus file review of `runtime`, `built-in-nodes`, `custom-nodes`, and related deep pages
- Next: rewrite the canonical pages and normalize subtree links

### 2026-04-27 00:00 - Canonical strengthening and subtree cleanup

- What changed: rewrote `flowgram/runtime`, `flowgram/built-in-nodes`, and `flowgram/custom-nodes` into real canonical topic pages; normalized major old-path references across the `flowgram/` subtree; and carried the `Flowgram.ai` frontend-library vs AI4J backend-runtime boundary into runtime-oriented pages
- Verification: `npm run typecheck` passed; old-path scan for the targeted Flowgram subtree patterns returned no matches; a fresh Docusaurus build compiled client/server bundles successfully and failed only during Windows artifact cleanup with `EPERM`
- Next: feature closed; remaining deeper content refinements can be handled in later focused writing passes

## Residual

- Windows `EPERM` locks still prevent a clean final Docusaurus artifact write even when docs compilation succeeds
- legacy deep pages remain in the tree as supporting detail pages, but the canonical reading path has been normalized
