---
title: "Release Checklist"
sidebar_position: 3
description: "Maintainer checklist before and after publishing AI4J to Maven Central and GitHub Release, covering version strategy, local verification, and post-release checks."
tags: [reference]
---

# Release Checklist

This page is the minimal checklist maintainers follow before and after publishing AI4J to Maven Central and GitHub Release.

## Version strategy

AI4J currently uses **uniform versioning across all modules**:

- Release version: all release modules use the same stable version, e.g. `2.4.2`
- Development branch: all Maven POMs use the next `SNAPSHOT`, e.g. `2.4.3-SNAPSHOT`
- README / docs examples: write the latest released stable version, never a `SNAPSHOT`

When you only change the README, docs-site, or a demo, no new Maven version is required.

## Pre-release

1. Confirm the current branch is clean and cut a release fix branch from `main`.
2. Bump all Maven POMs from `*-SNAPSHOT` to the same release version.
3. Sync the user-facing install version in README, README-EN, and docs-site.
4. Confirm `ai4j-bom` covers the release modules that need to align.
5. Confirm the release profile does not publish the aggregation root project, demo artifacts, or the CLI fat jar.
6. Confirm Maven `settings.xml` contains the Central server id, and the secret stays out of the repo.
7. Confirm GPG agent / Kleopatra can complete signing.

## Local verification

```powershell
mvn -DskipTests package
mvn -P release -DskipTests clean verify
```

If you modified docs-site:

```powershell
npm --prefix docs-site ci
npm --prefix docs-site run build
```

## Release

```powershell
mvn -P release -DskipTests clean deploy
```

Record the Central deployment id. If Central returns `validated` but requires manual publishing, publish that deployment from the Sonatype Central Portal or the Publisher API.

## Post-release verification

1. Maven Central deployment status is `PUBLISHED`.
2. `maven-metadata.xml` `latest` and `release` equal this version.
3. The main module's `pom`, `jar`, `sources`, `javadoc`, and `.asc` are downloadable.
4. `ai4j-cli-<version>-jar-with-dependencies.jar` does not exist.
5. Create a GitHub tag / Release describing the version changes and Maven coordinates.
6. On a new branch, bump all Maven POMs to the next `SNAPSHOT` and merge it back to `main`.

## Wrap-up

- Delete the merged release / bump branches.
- Confirm local `main` aligns with `origin/main`.
- Record the deployment id, GitHub release URL, verification commands, and residual risks in the HA task.
