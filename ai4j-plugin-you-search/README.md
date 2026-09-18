# ai4j-plugin-you-search

Optional AI4J extension plugin that adds a You.com web search tool for agents.

## What It Provides

- Extension id: `you-search`
- Tool: `you_web_search`
- Command: `you-search`
- Skill: `you-web-search`
- Prompt: `you-search-answer`

The tool queries the You.com Search API (`https://api.you.com/api/search`) and returns a
`you.web_search.response` envelope with `title` / `url` / `snippet` result triples so an
agent can ground answers in current web sources and cite URLs.

It does not change default behavior: nothing is registered until the host explicitly
enables the extension, and the plugin performs no network activity during `apply(...)`.

## Setup

Get an API key at [you.com/platform/api-keys](https://you.com/platform/api-keys) and export it:

```bash
export YDC_API_KEY="***"
```

Optional overrides (usually unnecessary):

- `-Dai4j.extensions.you-search.baseUrl=...` — search endpoint override
- `-Dai4j.extensions.you-search.apiKey=...` — key via system property instead of env var

## Enable

```java
ExtensionRegistry registry = ExtensionRegistry.discover()
        .enable("you-search")
        .exposeTool("you_web_search");
```

The tool then accepts arguments like:

```json
{"query": "latest Java LTS release notes", "numResults": "5"}
```

and returns:

```json
{
  "type": "you.web_search.response",
  "tool": "you_web_search",
  "query": "latest Java LTS release notes",
  "numResults": 5,
  "results": [
    {"title": "...", "url": "https://...", "snippet": "..."}
  ]
}
```

## Fallback behavior

Errors are returned as a `you.web_search.error` envelope instead of exceptions:

- `missing_api_key` — `YDC_API_KEY` is not set
- `missing_query` — tool arguments had no usable `query` field
- `http_401` / `http_403` — key rejected by the API
- `network_error` — endpoint unreachable
- `unparsable_response` — response shape not recognized

## Alternative: MCP

If you prefer not to manage an API key in-process, the AI4J Coding Agent CLI can use the
keyless You.com MCP server profile instead:

```bash
ai4j /mcp add you-free --type streamable_http --url "https://api.you.com/mcp?profile=free"
```

## Verify

```bash
mvn -pl ai4j-plugin-you-search -am -DskipTests=false test
```

Live search requires `YDC_API_KEY`; without it the offline tests still pass and the tool
returns the documented `missing_api_key` envelope.
