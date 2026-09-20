# Coding Agent CLI / TUI

[Back to English README](../../../README-EN.md) · [中文 README](../../../README.md)

## Coding Agent CLI / TUI

AI4J now includes `ai4j-cli`, which can be used directly as a local coding agent. Current capabilities include:

+ one-shot and persistent sessions
+ CLI and TUI interaction modes
+ provider profile persistence
+ workspace-level model override
+ subagent and agent team collaboration
+ session persistence, resume, fork, history, tree, events, replay
+ team board, team messages, and team resume for collaboration visibility
+ process management and buffered logs

### Install

```bash
curl -fsSL https://lnyo-cly.github.io/ai4j/install.sh | sh
```

```powershell
irm https://lnyo-cly.github.io/ai4j/install.ps1 | iex
```

The installer downloads `ai4j-cli` from Maven Central and creates the `ai4j` command. Java 8+ must already be installed on the machine.

### one-shot example

```powershell
ai4j code `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --prompt "Read README and summarize the project structure"
```

### interactive CLI example

```powershell
ai4j code `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### TUI example

```powershell
ai4j tui `
  --provider zhipu `
  --protocol chat `
  --model glm-4.7 `
  --base-url https://open.bigmodel.cn/api/coding/paas/v4 `
  --workspace .
```

### ACP example

```powershell
ai4j acp `
  --provider openai `
  --protocol responses `
  --model gpt-5-mini `
  --workspace .
```

### Build from source (optional)

```powershell
mvn -pl ai4j-cli -am -DskipTests package
```

Artifact:

```text
ai4j-cli/target/ai4j-cli-<version>-jar-with-dependencies.jar
```

If you want to run the locally built artifact directly:

```powershell
java -jar .\ai4j-cli\target\ai4j-cli-<version>-jar-with-dependencies.jar code --help
```

### Current protocol rules

The CLI currently exposes only two protocol families:

+ `chat`
+ `responses`

If `--protocol` is omitted, the CLI resolves a default locally from provider/baseUrl:

+ `openai` + official OpenAI host -> `responses`
+ `openai` + custom compatible `baseUrl` -> `chat`
+ `doubao` / `dashscope` -> `responses`
+ other providers -> `chat`

Notes:

+ `auto` is no longer exposed to users
+ legacy `auto` values in existing config files are normalized to explicit protocols on load

### provider profile locations

+ global config: `~/.ai4j/providers.json`
+ workspace config: `<workspace>/.ai4j/workspace.json`

Recommended workflow:

+ keep reusable long-term runtime profiles in the global config
+ let each workspace reference one `activeProfile`
+ use workspace `modelOverride` for temporary model switching

### Common commands

+ `/providers`
+ `/provider`
+ `/provider use <name>`
+ `/provider save <name>`
+ `/provider add <name> --provider <name> [--protocol <chat|responses>] [--model <name>] [--base-url <url>] [--api-key <key>]`
+ `/provider edit <name> [--provider <name>] [--protocol <chat|responses>] [--model <name>|--clear-model] [--base-url <url>|--clear-base-url] [--api-key <key>|--clear-api-key]`
+ `/provider default <name|clear>`
+ `/provider remove <name>`
+ `/model`
+ `/model <name>`
+ `/model reset`
+ `/stream [on|off]`
+ `/processes`
+ `/process status|follow|logs|write|stop ...`
+ `/resume <id>` / `/load <id>` / `/fork ...`

### Documentation entry points

+ [Coding Agent Overview](../../../docs-site/docs/products/coding-agent/overview.md)
+ [Coding Agent Quickstart](../../../docs-site/docs/products/coding-agent/quickstart.md)
+ [CLI / TUI Usage Guide](../../../docs-site/docs/products/coding-agent/cli-and-tui.md)
+ [Provider Profiles](../../../docs-site/docs/products/coding-agent/provider-profiles.md)
+ [Command Reference](../../../docs-site/docs/products/coding-agent/command-reference.md)
+ [Configuration](../../../docs-site/docs/products/coding-agent/configuration.md)

## Other support
+ [[Low-cost transit platform] Low-cost ApiKey - Limited-time special offer 0.7:1 - Supports the latest o1 model.](https://api.trovebox.online/)
