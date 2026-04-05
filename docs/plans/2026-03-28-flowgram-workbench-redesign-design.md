# 2026-03-28 FlowGram Workbench Redesign Design

## Goal

Rework `ai4j-flowgram-webapp-demo` into a more product-shaped workflow studio that is easier to start with, visually closer to Coze, and still keeps the runtime visibility needed for ai4j + Spring Boot integration demos.

This redesign focuses on:

- a clearer workbench shell
- a visible `Blank Canvas` entry
- a richer default demo workflow
- a stable local HTTP mock for the default HTTP node

## Product Direction

Use a `Coze-like outer shell + Dify-like runtime observability` approach:

- Coze-like:
  - template-first entry
  - lighter and friendlier studio shell
  - obvious "new blank" and "load demo" actions
- Dify-like:
  - clear runtime state
  - explicit validate / run actions
  - visible task status and backend connectivity

## UX Decisions

### Default Entry

The default template is no longer `Start -> LLM -> End`.

The new default template is `Travel Copilot`, which demonstrates:

- `Start`
- `Variable`
- `HTTP`
- `Tool`
- `Code`
- `LLM`
- `End`

### Blank Canvas

`Blank Canvas` is not a truly empty JSON document.

It contains:

- `Start`
- `End`

and shows a lightweight center overlay telling the user to drag nodes from the left panel or load the default demo.

### Workbench Layout

The workbench is split into:

1. top command / status bar
2. left rail with:
   - templates
   - node materials
3. center canvas
4. right FlowGram inspector panel

## Template Set

The template list becomes:

- `blank-canvas`
- `travel-copilot`
- `condition-review`
- `loop-digest`
- `knowledge-qa`

`knowledge-qa` remains optional and may require extra backend bean/configuration.

## Travel Copilot Workflow

### Inputs

- `departure`
- `destination`
- `date`
- `trainType`
- `userGoal`

### Main Chain

1. `Start`
2. `Variable`
   - normalize service/model/runtime labels
3. `HTTP`
   - call local mock weather endpoint
4. `Tool`
   - call `queryTrainInfo`
5. `Code`
   - merge weather/tool/input data into structured summaries and final prompt
6. `LLM`
   - generate the final travel suggestion
7. `End`
   - expose `result`, `weatherSummary`, `trainSummary`, `travelBrief`

## Backend Support

Add a minimal endpoint under `ai4j-flowgram-demo`:

- `GET /flowgram/demo/mock/weather`

This endpoint returns deterministic JSON for:

- `city`
- `date`
- `weather`
- `temperature`
- `advice`

The endpoint exists only to keep the default `HTTP` node stable for local demo runs.

## Files Expected To Change

Frontend:

- `ai4j-flowgram-webapp-demo/src/data/workflow-templates.ts`
- `ai4j-flowgram-webapp-demo/src/editor.tsx`
- `ai4j-flowgram-webapp-demo/src/workbench/workbench-shell.tsx`
- `ai4j-flowgram-webapp-demo/src/workbench/workbench-toolbar.tsx`
- `ai4j-flowgram-webapp-demo/src/workbench/workbench-sidebar.tsx`
- `ai4j-flowgram-webapp-demo/src/styles/index.css`

Backend:

- `ai4j-flowgram-demo/src/main/java/io/github/lnyocly/ai4j/flowgram/demo/FlowGramDemoMockController.java`

## Validation

After implementation, validate with:

- frontend type check
- backend package/build
- local run of frontend + backend
- browser smoke check for:
  - `New Blank`
  - `Load Demo`
  - template switching
  - opening and running the default demo
