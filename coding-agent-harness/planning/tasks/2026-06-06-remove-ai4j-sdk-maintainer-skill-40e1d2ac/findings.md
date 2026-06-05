# Findings

## Material Findings

No open material findings.

## Decisions

### Remove Maintainer Skill

`$ai4j-sdk` duplicated repository-maintenance guidance already owned by `AGENTS.md` and `coding-agent-harness/`. Removing it reduces public confusion and future drift.

### Keep Historical Evidence

Historical task artifacts that mention `$ai4j-sdk` are retained as records of prior work. Active public entry points are cleaned instead.

## Residual Risk

- Users who already installed `$ai4j-sdk` from a prior commit may still have a local copy. Future release notes can mention the consolidation if needed.
- Remote repository state must be pushed before `npx skills add LnYo-Cly/ai4j --skill ai4j-app-builder` reflects this local deletion.
