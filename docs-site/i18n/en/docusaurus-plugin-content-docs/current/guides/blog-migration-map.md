---
sidebar_position: 4
title: "Historical Blog Migration Map"
description: "Maps AI4J's historical CSDN blog articles to structured docs, so they can be maintained continuously and kept in sync across versions. Covers historical content such as Spring Boot + OpenAI quickstart wiring and DeepSeek/Qwen/Llama local model integration, along with the corresponding new doc paths."
tags: [reference]
---

# Historical Blog Migration Map

This page maps historical CSDN articles to structured docs, making them easier to maintain over time and keep in sync across versions.

## 1. Migration map

1. **Spring Boot + OpenAI + JDK8 quickstart wiring**
   - Original article: [142177544](https://blog.csdn.net/qq_35650513/article/details/142177544)
   - New doc: `Quick Start / JDK8 + OpenAI minimal example`

2. **DeepSeek / Qwen / Llama local model integration**
   - Original article: [142408092](https://blog.csdn.net/qq_35650513/article/details/142408092)
   - New doc: `Quick Start / Ollama local model integration`

3. **DeepSeek streaming + web search + RAG + multi-turn**
   - Original article: [146084038](https://blog.csdn.net/qq_35650513/article/details/146084038)
   - New doc: `Scenario Guide / DeepSeek: streaming + web search + RAG + multi-turn session`

4. **SearXNG web search enhancement**
   - Original article: [144572824](https://blog.csdn.net/qq_35650513/article/details/144572824)
   - New doc: `Scenario Guide / SearXNG web search enhancement`

5. **Legal assistant RAG (Pinecone)**
   - Original article: [142568177](https://blog.csdn.net/qq_35650513/article/details/142568177)
   - New doc: `Scenario Guide / Pinecone-based legal assistant RAG`

6. **MCP + MySQL dynamic management**
   - Original article: [150532784](https://blog.csdn.net/qq_35650513/article/details/150532784)
   - New doc: `MCP / MySQL dynamic MCP service management`

## 2. Why migrate to a docs library

- Articles are inherently timeline-structured and don't lend themselves to long-term, topic-based retrieval.
- A docs library can be maintained module by module and kept in sync with code changes.
- Community contributors can directly extend and revise content through PRs.

## 3. Recommended migration strategy

1. Keep the original links first, so the history stays traceable.
2. Split "concepts" and "working code" into separate sections.
3. Update the corresponding doc page whenever a version is upgraded.
4. Funnel high-frequency questions into `Quick Start / FAQ & troubleshooting handbook`.

## 4. Follow-up plans (suggested)

- Add "version diff notes" (e.g., 1.3 -> 1.4)
- Add a "sample repo index"
- Add a quick-lookup page that maps "common error logs -> troubleshooting steps"

## 5. How to use this page now

This page works better as an index of historical content than as a primary reading entry point.

The recommended reading order is:

1. Start from the current canonical topic tree to enter the relevant module
2. Come back here only when you need to trace a blog source, migration background, or old path mapping

This keeps the "historical article paths" and the "current main docs line" from collapsing into a single navigation layer.

## 6. Rules the migration map should keep following

For this map to stay useful over time, keep following three rules:

- Every legacy article maps to at least one current canonical page
- If an old path has already been split across multiple capability surfaces, make explicit which topic trees it splits into
- Backfill this index only after the new scenario page or module page has stabilized

This keeps it a maintenance index rather than a one-time migration memo.

## 7. What to prioritize updating during maintenance

If you keep cleaning up legacy content, prioritize:

- The new canonical pages that high-traffic legacy articles point to
- Old path mappings that have already been split into multiple topic trees
- The index entries most relevant to version upgrades, troubleshooting, and the sample repo

This keeps the table serving current readers and maintainers first, rather than just preserving a historical record.
