# ai4j CLI distribution

This archive contains the Java-based ai4j Coding Agent CLI.

## Layout

```text
bin/
  ai4j       Unix launcher
  ai4j.cmd   Windows launcher
lib/
  ai4j-cli-<version>-jar-with-dependencies.jar
conf/
  providers.example.json
  workspace.example.json
```

## Requirements

- Java 8 or newer on `PATH`
- Provider credentials supplied through environment variables or local config

Do not put real provider keys in this distribution directory if it will be shared.

## Quick start

Unix/macOS:

```sh
./bin/ai4j --help
./bin/ai4j code --workspace . --prompt "Summarize this project"
./bin/ai4j tui --workspace .
```

Windows:

```bat
bin\ai4j.cmd --help
bin\ai4j.cmd code --workspace . --prompt "Summarize this project"
bin\ai4j.cmd tui --workspace .
```

## Config examples

- Copy `conf/providers.example.json` to `~/.ai4j/providers.json` and replace only placeholders or environment variable references.
- Copy `conf/workspace.example.json` to `<workspace>/.ai4j/workspace.json` when a workspace needs explicit profile/model/agent/skill settings.

The launchers only locate Java and the bundled fat jar, then forward arguments to `io.github.lnyocly.ai4j.cli.Ai4jCliMain`.
They do not hardcode provider, model, base URL, API keys, workspace, or sandbox settings.

Advanced overrides:

- `AI4J_JAVA`: Java executable path
- `AI4J_JAVA_OPTS`: JVM options
- `AI4J_CLI_JAR`: explicit jar path for launcher smoke tests or custom layouts
