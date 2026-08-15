---
title: "Install and Release"
description: "Distinguishes the three layers of the Coding Agent — build, Maven release, and end-user CLI installation — notes that the repo already ships a fat jar and platform launchers, and names the fat jar as the most stable distribution baseline along with the current release gaps."
tags: [how-to]
---

# Install and Release

This page is not about "how to configure a provider", but about a more practical question:

- What distributable artifacts the current repo actually produces
- Which layer belongs to Maven artifact release
- Which layer counts as an installable CLI distribution for end users

Without separating these three layers, it's easy to mistake "already able to `mvn package`" for "already has a complete installation and release chain".

## 1. Distinguish the three layers first

Everything related to the `Coding Agent` release today falls into at least three layers:

- Build: package `ai4j-cli` and its dependencies into a runnable artifact
- Release: place Maven artifacts or release assets in a distributable location
- Install: wire together the user's local `java`, command entry point, and configuration starting point

These three layers are related, but the current repo supports them to different degrees.

## 2. What the current source code has actually done right

What `ai4j-cli/pom.xml` already does explicitly:

- Standard jar build
- fat jar build
- source/javadoc/sign/publish under the release profile

More specifically:

### 2.1 The directly runnable main class

The manifest main class is currently:

- `io.github.lnyocly.ai4j.cli.Ai4jCliMain`

`Ai4jCliMain` does very little, but it's critical:

1. First configure the SLF4J SimpleLogger based on `--verbose`
2. Then call `new Ai4jCli().run(...)`

This shows that the CLI's real entry point is not a shell script, but a standard Java main class.

### 2.2 The fat jar is the most stable end-user distribution baseline today

`maven-assembly-plugin` uses:

- `jar-with-dependencies`

So the artifact best suited for end users to run directly is:

- `ai4j-cli-<version>-jar-with-dependencies.jar`

rather than the default thin jar.

### 2.3 `Ai4jCli` already handles command dispatch

The top-level subcommands it directly supports today:

- `code` — coding session CLI host (most common)
- `tui` — equivalent to `code --ui tui`
- `acp` — coding session as an ACP stdio server
- `run` — run an Agent Blueprint YAML once (one-shot, no session)
- `extension` — inspect / assemble / run extension packages
- `trust` — manage the workspace hook trust directory

And:

- When no subcommand is given and `--model ...` is passed directly, it defaults to `code`
- `tui` is essentially `code --ui tui`

From the Java entry point's perspective, command dispatch is complete — you don't need to write another Java launcher layer.

## 3. What the current repo does not yet provide

This matters just as much.

Based on the current `ai4j-cli` module, what the repo **already ships**:

- A Unix `ai4j` shell launcher (`ai4j-cli/src/main/distribution/bin/ai4j`)
- A Windows `ai4j.bat` launcher (`ai4j-cli/src/main/distribution/bin/ai4j.bat`)

These two launchers follow the responsibility boundary in section 8: they only locate `java`, locate the fat jar, forward arguments, and keep the stable command name `ai4j`. They do not hardcode provider/model/secrets, nor do they parse configuration in the script layer.

What is **still not** provided:

- An assembly that auto-produces a `bin/` + `lib/` cross-platform archive (the launchers are currently source-form; you must place them manually in the bin/lib layout below, or point directly via `AI4J_JAR`)
- Checksum asset generation logic
- A repo-bundled installer (`curl|sh` one-liner install, etc.)

So "distributable" today means:

- Maven artifact
- A directly `java -jar`-able fat jar
- Two platform launchers (as source)

And **not yet**:

- A ready-to-install, multi-platform CLI suite with its own update mechanism

## 4. The most stable build command today

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

The point of this command is not just to build one module, but to:

- Take `ai4j-cli`
- Along with its dependencies `ai4j` and `ai4j-coding`

compile and package them all at once, ultimately producing the runnable fat jar.

If your goal is only to:

- Locally verify that the CLI runs
- Produce a file you can hand to someone for `java -jar`

this is the smallest stable path today.

## 5. "Maven Central release" and "end-user install" are not the same thing

The `release` profile in `ai4j-cli/pom.xml` already includes:

- `flatten-maven-plugin`
- `maven-source-plugin`
- `maven-javadoc-plugin`
- `maven-gpg-plugin`
- `central-publishing-maven-plugin`

This shows the repo has already accounted for:

- Maven Central / central repository release
- Source and javadoc packages
- Signing and release metadata

But this pipeline serves:

- Java ecosystem consumers
- Maven / Gradle dependency distribution

It does not automatically equal:

- An end user getting a natively installable CLI

In other words:

- "Releasable to Central" and "directly installable by end users" are two different things

## 6. The most realistic installation method today

Based on the current source, the most stable installation method is still:

1. Have a working Java runtime on the user's machine (JDK 8+)
2. Obtain `ai4j-cli-<version>-jar-with-dependencies.jar`

Both approaches below work in practice.

### 6.1 Use a launcher (recommended)

After building the fat jar, lay out the launcher and jar in a `bin/` + `lib/` structure:

```text
ai4j-cli-<version>/
  bin/
    ai4j          # from ai4j-cli/src/main/distribution/bin/ai4j
    ai4j.bat      # from ai4j-cli/src/main/distribution/bin/ai4j.bat
  lib/
    ai4j-cli-<version>-jar-with-dependencies.jar
```

The launcher automatically finds the fat jar under `../lib/`, so you can directly run:

```bash
./ai4j code --model gpt-5-mini
```

On Windows use `ai4j.bat`. You can also skip the layout and point at the jar directly via an environment variable:

```bash
export AI4J_JAR=/path/to/ai4j-cli-2.4.2-jar-with-dependencies.jar
ai4j --help
```

Optional overrides the launcher supports: `AI4J_JAR` (point at the jar explicitly), `JAVA_HOME` (pin a JRE), `AI4J_JAVA_OPTS` / `JAVA_OPTS` (JVM args). It does not hardcode provider/model/secrets or parse configuration — that is all delegated to `Ai4jCli`.

### 6.2 Direct `java -jar` (simplest, no launcher)

If you don't want a launcher, running the fat jar directly works just as well:

```powershell
java -jar .\ai4j-cli-2.4.2-jar-with-dependencies.jar code --model gpt-5-mini
```

Both approaches are genuinely supported by the current source; the launcher just saves you from typing the long jar name every time and keeps the command unchanged across version upgrades.

## 7. For a proper release, what's still missing at minimum

If your goal is "stable installation for external users", on top of the existing fat jar + launcher, you should at least add:

- ~~Platform launchers~~ (already provided: `ai4j-cli/src/main/distribution/bin/ai4j(.bat)`)
- Release checksums
- Minimal example configuration
- Versioned release notes
- An assembly that auto-produces the `bin/` + `lib/` platform archive

A more product-like release structure usually looks at least like:

```text
ai4j-cli-<version>/
  bin/
    ai4j
    ai4j.cmd
  lib/
    ai4j-cli-<version>-jar-with-dependencies.jar
  conf/
    providers.example.json
    workspace.example.json
  README.md
```

The current repo does not auto-produce this layer, so it remains a "recommended next step", not the "current state".

## 8. What the launcher layer should and shouldn't do

If you plan to add install scripts later, the safest responsibility boundary is:

### Should do

- Locate `java`
- Locate the fat jar
- Forward command-line arguments
- Keep a stable command name, e.g. `ai4j`

### Shouldn't do

:::danger What a launcher must not do
- Hardcode a provider or model in the script
- Silently generate repo-level configuration in the script
- Burn user secrets into the launcher
- Push complex business logic into the install layer
:::

The reason is simple:

- A launcher should just be a launcher
- Configuration parsing already has a proper implementation in `Ai4jCli` / `CodeCommandOptionsParser` / the config managers

Don't reinvent a configuration system in the script layer.

## 9. Why the release layer can't be reduced to "ship a profile example"

The product boundary of the `Coding Agent` sits closer to a "local tool" than an ordinary SDK.

So the release layer must also consider at least:

- The workspace entry point
- Session store behavior
- Approvals
- The ACP stdio server entry point
- The terminal environment the TUI behavior depends on

In other words, what you release is not:

- A connection template for some provider

but rather:

- A host program with multiple top-level subcommands like `code` / `tui` / `acp` / `run` / `extension` / `trust`

This is also why release documentation must cover simultaneously:

- The Java main class
- The jar form
- The launcher form
- The config starting point

## 10. Which two release targets the current repo fits

### 10.1 Artifact release for the Java ecosystem

Goal:

- Maven Central consumption
- Use as a module dependency

Status:

- `release` profile already exists
- Basic metadata and signing release pipeline is already reflected in the pom

### 10.2 CLI distribution for end users

Goal:

- Users download and run directly
- Minimal Maven knowledge required

Status:

- Currently relies mainly on the fat jar
- Still missing platform launchers and a release asset organization layer

Keep these two targets separate — don't substitute the Maven release pipeline for the CLI install experience.

## 11. If you want to keep improving this layer, where to start first

Starting from the current source, the most sensible next step is usually:

1. Keep the fat jar as the base artifact, unchanged
2. Add `bin/ai4j` and `bin/ai4j.cmd`
3. Add a release packaging script or CI job
4. Add checksums and minimal configuration samples
5. Only then consider GitHub Release automation

The benefit of this order:

- No need to touch the CLI core logic
- It only adds a distribution wrapper layer around the existing stable Java entry point

## 12. The conclusions to remember from this page

- The current repo already ships a runnable Java entry point and a fat jar build
- The formal entry point of `ai4j-cli` is `Ai4jCliMain`
- The most stable end-user distribution baseline today is `jar-with-dependencies`
- The `release` profile leans toward Maven Central artifact release, not the end-user install pipeline
- The repo does not yet ship ready-made multi-platform launcher / installer / release bundle artifacts

## 13. Further reading

1. [Coding Agent Quickstart](/docs/products/coding-agent/quickstart)
2. [CLI / TUI Usage Guide](/docs/products/coding-agent/cli-and-tui)
3. [Configuration System](/docs/products/coding-agent/configuration)
4. [ACP Integration](/docs/products/coding-agent/acp-integration)
5. [Command Reference](/docs/products/coding-agent/command-reference)
