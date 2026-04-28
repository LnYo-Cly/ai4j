# docs-site cross-tree quality cleanup - Progress

## Status

`completed`

## Log

### 2026-04-28 21:25 - Task bootstrap

- What changed: diagnosed the remaining docs-site residue outside `core-sdk`, created `F-017`, and fixed the next docs wave on cross-tree style cleanup plus thin-page deepening
- Verification: targeted style and line-count scan only; `RG-008` not rerun yet for this wave
- Next: normalize the remaining interview-oriented phrasing, then deepen the thinnest canonical pages starting from `solutions/`

### 2026-04-28 22:10 - Cross-tree cleanup and full docs-site verify

- What changed: normalized the remaining canonical docs outside `core-sdk` away from interview-style wording, deepened `solutions/` and selected `start-here`, `spring-boot`, `agent`, `mcp`, and `getting-started` pages, and left only explicit migration placeholders as short legacy pages
- Verification: `docs-site` `RG-008` passed with `NODE_OPTIONS=--max-old-space-size=8192 npm run typecheck` and `NODE_OPTIONS=--max-old-space-size=8192 npm run build`
- Next: close the feature via walkthrough and move `F-017` into completed status

## Residual

- none
