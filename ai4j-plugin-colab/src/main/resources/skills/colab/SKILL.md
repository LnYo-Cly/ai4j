---
name: colab
description: Execute Python cells, inspect kernel state, and capture rich display outputs inside a Google Colab or Jupyter runtime.
---

# Google Colab Kernel Skill

Use the `colab_execute` tool or `colab` SandboxProvider when running Python analytics, GPU workloads, data visualization, or notebook reproduction against a Colab/Jupyter runtime endpoint.

Guidelines:
- Configure `COLAB_API_URL` (and optional `COLAB_API_TOKEN`) or pass `baseUrl`/`apiToken` in sandbox configuration.
- Keep cells focused and deterministic; prefer returning structured JSON summaries or standard output alongside plots (`image/png`, `image/svg+xml`).
- Re-use a kernel session ID across dependent notebook steps so variables and imports remain in memory, and close or dispose the session when finished.
