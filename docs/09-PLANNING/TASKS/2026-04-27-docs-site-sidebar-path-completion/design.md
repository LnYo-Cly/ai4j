# docs-site Sidebar Path Completion Design

## Objective

Turn the current `docs-site` into a consistent sidebar-first documentation system that supports both first-time onboarding and high-density technical review.

## Reader Model

The primary reader journey is:

1. understand what AI4J is and why this module exists
2. understand the architecture boundary and package/module path
3. understand the module's strengths, trade-offs, and suitable scenarios
4. dive into the detailed capability pages in a controlled order

This means the site cannot rely on thin bridge pages or old-route residue at the top of each tree.

## Execution Waves

1. `intro + Start Here + FAQ/Glossary`
2. `Core SDK`
3. `Spring Boot`
4. `Agent`
5. `Coding Agent`
6. `Flowgram`
7. `Solutions`
8. `FAQ/Glossary` final refinement after cross-tree stabilization
9. legacy deep-page residue cleanup

## Page Standard

For canonical pages, the minimum content standard is:

- positioning: what this page/module is for
- module path / architecture boundary: where the code and concepts live
- strengths and differentiators: what is distinctive or advantageous here
- use cases: when this path is the right choice
- reading path: what to read next and in what order

For deeper capability pages, the minimum content standard is:

- what problem this capability solves
- how it fits the surrounding module
- the most important concepts or runtime boundaries
- links back to the canonical path

## Wave 1 Plan

Wave 1 should stabilize the global entry experience before any deeper tree rewrite:

- `intro.md`
- `start-here/why-ai4j.md`
- `start-here/architecture-at-a-glance.md`
- `start-here/choose-your-path.md`
- `start-here/quickstart-java.md`
- `start-here/quickstart-spring-boot.md`
- `start-here/first-chat.md`
- `start-here/first-tool-call.md`
- `start-here/troubleshooting.md`
- `faq.md`
- `glossary.md`

The preferred order inside Wave 1 is:

1. diagnose which pages are still acting as thin bridges
2. strengthen the highest-traffic entry pages first
3. normalize their crosslinks
4. run `RG-008`
