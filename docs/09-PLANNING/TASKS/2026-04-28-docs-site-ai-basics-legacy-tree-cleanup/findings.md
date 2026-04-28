# docs-site ai-basics legacy tree cleanup - Findings

## Discoveries

### Remaining work is mostly legacy-tree governance, not style residue

- Why it mattered: the previous docs-site waves already cleared the obvious interview-oriented wording from the visible canonical trees
- What was found: `ai-basics/` now contains mostly either historical long-form pages or migration bridge pages, with only a small number of active pages still below the current technical-doc depth standard
- Impact on plan: treat this wave as legacy-tree normalization rather than another site-wide wording pass

### Thin active pages are concentrated in a few service and comparison pages

- Why it mattered: most `ai-basics/` pages are already long-form, so the remaining gaps are narrow and identifiable
- What was found: the notable still-active thin pages are `services/embedding.md`, `responses/chat-vs-responses.md`, and the blog migration index; the very short `enhancements/*` pages are intentionally bridge-like
- Impact on plan: deepen the active pages and clarify the bridge pages instead of rewriting the whole subtree

## Decisions

| Decision | Choice | Reason | Alternatives |
|----------|--------|--------|--------------|
| Wave focus | treat `ai-basics/` as a legacy-tree cleanup wave | the remaining issues are local to the historical subtree and related migration indexes | reopen a larger whole-site cleanup wave |
| Bridge-page handling | keep explicit migration pages short but clearer about their role | some pages are no longer meant to be canonical technical references | artificially deepen every legacy bridge page |
| Active-page deepening | patch only the remaining thin active pages | the rest of the subtree already carries enough technical detail | mechanically rewrite every `ai-basics/` page again |
