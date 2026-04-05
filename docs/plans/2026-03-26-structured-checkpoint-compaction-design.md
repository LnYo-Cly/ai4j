# Structured Checkpoint Compaction Design

Date: 2026-03-26

## Goal

Move coding-session compaction to a structured checkpoint flow:

- internal truth source: `CodingSessionCheckpoint`
- human-facing summary: rendered Markdown
- compatibility parser: best-effort only

## Problem

The existing compaction flow asks the model for Markdown, then parses that Markdown back into a checkpoint object.

This is fragile because:

- the model may prepend natural-language text
- headings may drift slightly
- parsing failures can crash auto compact

## Design

1. Compaction should carry forward the previous `CodingSessionCheckpoint` object when available.
2. The compaction prompt should prefer structured JSON output.
3. The formatter should:
   - parse structured JSON when present
   - tolerate leading prose and unknown lines in Markdown fallback mode
4. The compactor should render the final checkpoint back to Markdown only for memory/session display.

## Non-Goals

- changing session memory storage from `summary:String` to a new storage type
- changing CLI checkpoint rendering format
- removing Markdown compatibility for older sessions

## Follow-up

If this direction holds up, the next step is to separate:

- checkpoint compaction
- tool-result pruning
- long-turn partial summarization
