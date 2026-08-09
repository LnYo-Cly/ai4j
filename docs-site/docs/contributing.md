---
sidebar_position: 14
title: Contribute to AI4J
description: How to contribute to AI4J — reporting issues, proposing changes, running docs-site local checks and Java module tests, and following the security disclosure policy.
---

# Contribute to AI4J

This page is the documentation entry point for contributors. The repository's [full contribution guide](https://github.com/LnYo-Cly/ai4j/blob/main/CONTRIBUTING.md) is authoritative for source changes and pull requests.

## Choose a path

- Report a reproducible defect through [Issues](https://github.com/LnYo-Cly/ai4j/issues).
- Discuss a larger API or product change in [Discussions](https://github.com/LnYo-Cly/ai4j/discussions) before implementing it.
- Start with a scoped [good first issue](https://github.com/LnYo-Cly/ai4j/labels/good%20first%20issue) when you are new to the project.
- Send documentation fixes from `docs-site/` with the checks below.

Open pull requests against `main`. Keep one logical change per pull request and state the exact verification command in its description.

## Documentation changes

From `docs-site/`, install dependencies and run the same local checks used by the documentation workflow:

```bash
npm ci
npm run typecheck
npm run check:docs
npm run build
```

Keep conceptual pages linked from the sidebar, preserve redirects when moving a public route, and verify examples against the current source before calling a feature supported.

## Java changes

AI4J is a Java 8 Maven monorepo. Build or test the smallest affected module first, then expand verification when the change crosses module boundaries.

```bash
mvn -pl <module> -am -DskipTests=false test
```

The [contribution guide](https://github.com/LnYo-Cly/ai4j/blob/main/CONTRIBUTING.md) defines the current branch, compatibility, commit, and review expectations.

## Security and conduct

:::warning Report security issues privately
Do not disclose a security vulnerability in a public issue. Follow the repository's [security policy](https://github.com/LnYo-Cly/ai4j/blob/main/SECURITY.md) instead. Participation is governed by the [Code of Conduct](https://github.com/LnYo-Cly/ai4j/blob/main/CODE_OF_CONDUCT.md).
:::
