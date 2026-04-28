# docs-site ai-basics legacy tree cleanup - Progress

## Status

`completed`

## Log

### 2026-04-28 22:35 - Task bootstrap

- What changed: diagnosed the remaining `ai-basics/` and related guide residue, created `F-018`, and fixed the next docs wave on legacy-tree normalization plus thin-page deepening
- Verification: targeted subtree scan only; `RG-008` not rerun yet for this wave
- Next: deepen the remaining active thin pages, clarify legacy bridge pages, then rerun docs-site verification

### 2026-04-28 23:05 - ai-basics cleanup and full docs-site verify

- What changed: clarified the role of the `ai-basics` legacy tree, deepened the remaining active thin pages such as `services/embedding` and `responses/chat-vs-responses`, and normalized the `enhancements/*` bridge pages plus the blog migration index into explicit migration-oriented docs
- Verification: `docs-site` `RG-008` passed with `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `NODE_OPTIONS=--max-old-space-size=8192 npm run build`
- Next: close the feature via walkthrough and move `F-018` into completed status

## Residual

- none
