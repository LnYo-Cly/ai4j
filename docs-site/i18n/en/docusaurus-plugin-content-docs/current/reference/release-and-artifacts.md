---
title: "Release and Artifacts"
sidebar_position: 2
description: "AI4J release artifacts, Maven coordinates, and BOM version-alignment strategy, covering each module's role, how to declare dependencies, and the upgrade order."
tags: [reference]
---

# Release and Artifacts

This page covers AI4J release artifacts, version alignment, and the order in which to pull modules into a project. It is aimed at consumers and maintainers and does not replace the per-module API documentation.

## Maven Coordinates

AI4J currently publishes under the coordinates:

```xml
<groupId>io.github.lnyo-cly</groupId>
```

The current repository version is:

```xml
<version>2.4.2</version>
```

## Recommended Dependency Declaration

When you only pull in a single module, you can declare that module's version directly:

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
    <version>2.4.2</version>
</dependency>
```

When pulling in multiple AI4J modules, aligning them through the BOM is recommended:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.lnyo-cly</groupId>
            <artifactId>ai4j-bom</artifactId>
            <version>2.4.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then business dependencies no longer repeat the version:

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
</dependency>
```

## Artifact Roles

| Artifact | Role | When to adopt |
| --- | --- | --- |
| `ai4j` | Core SDK | General Java projects, model, Tool, Skill, MCP, RAG |
| `ai4j-spring-boot-starter` | Spring integration | Spring Boot applications that need configuration and bean lifecycle |
| `ai4j-agent` | Agent runtime | When you need multi-step reasoning, workflow, trace, team |
| `ai4j-coding` | Coding runtime | When you need workspace-aware tools, session, compaction |
| `ai4j-cli` | CLI / TUI / ACP host | When you need a terminal or host entry point |
| `ai4j-flowgram-spring-boot-starter` | FlowGram backend starter | When you need the Java task API for the FlowGram.ai canvas |
| `ai4j-bom` | Version alignment | When a project pulls in two or more AI4J artifacts |

`ai4j-flowgram-demo` is a demo backend and should not be treated as the source of truth for production business dependencies.

## Release Boundary

The parent POM is the multi-module release entry point, but the root artifact should not, by default, be consumed as an SDK by business projects. When wiring a project, pull in only the modules you need.

The release profile handles source, javadoc, GPG signing, and the Sonatype Central publishing configuration. For the full release procedure, see the [Release Checklist](/docs/reference/maintainers/release-checklist). Before releasing, maintainers should confirm:

- The version number has been updated consistently across the root POM and module POMs.
- `ai4j-bom` includes all release modules that need to be aligned.
- The demo module has not been mistakenly treated as a production artifact.
- The boundary between live provider tests and local tests is clear.
- The credentials used by the release profile are not committed to the repository.

## Dependency Selection Examples

### Minimal Plain Java Integration

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
    <version>2.4.2</version>
</dependency>
```

### Spring Boot Integration

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-starter</artifactId>
    <version>2.4.2</version>
</dependency>
```

### Agent or Coding Agent

```xml
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-agent</artifactId>
    <version>2.4.2</version>
</dependency>
```

Coding Agent consumers usually also need `ai4j-coding` or `ai4j-cli`, depending on whether you embed the runtime or use the CLI/TUI/ACP host directly.

## Upgrade Strategy

1. First, bump the BOM (or a single module's version) on a test branch.
2. Run the minimal quickstart to confirm the provider, baseUrl, and apiKey sources are still correct.
3. If the project uses Tool, MCP, RAG, or Agent, run the corresponding smoke test for each.
4. If the project uses the Spring Boot starter, check that the configuration properties still bind.
5. If the project uses Coding Agent or FlowGram, check the host entry point and the task API.

Once the upgrade is done, link the project's internal integration notes back to [Version Compatibility](/docs/reference/version-compatibility) and [Production Checklist](/docs/production/production-checklist).
