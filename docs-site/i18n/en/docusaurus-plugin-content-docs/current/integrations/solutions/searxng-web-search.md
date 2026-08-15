---
title: "SearXNG Web Search"
description: "An enhancement that adds real-time public web search to the answer chain, covering SearXNG configuration, its boundary with RAG, and fallback strategies."
tags: [integration]
---

# SearXNG Web Search

This solution addresses "how to add real-time public web search to the answer chain" rather than replacing a private knowledge base.

## 1. When it fits

- You need the latest web information
- You need time-sensitive online search
- You want public web search as an optional enhancement chain

It is positioned as an "online search enhancement", not a replacement for RAG.

## 2. Core module combination

The main chain typically consists of:

- `SearXNGConfig`
- `ChatWithWebSearchEnhance`
- The model answer chain
- Optional streaming output

The key point is "injecting search results into the answer chain" rather than manually stitching together lots of web text yourself.

## 3. Boundary with RAG

- `SearXNG`: public web retrieval, strong timeliness
- `RAG`: private knowledge base retrieval, a more controllable boundary

:::warning Don't replace RAG with SearXNG
If you need to query an internal knowledge base, do not use SearXNG as a replacement for RAG.
:::

## 4. Value of this solution

- Fills in time-sensitive information
- Fits as an optional enhancement chain, not the default heavy path
- Easier to combine with existing chat or RAG systems

## 5. Which main pages to read first

1. [Core SDK / Search & RAG](/docs/capabilities/rag/overview)
2. [Core SDK / Extension](/docs/extending/overview)
3. [DeepSeek Stream Search RAG](/docs/integrations/solutions/deepseek-stream-search-rag)

## 6. Implementation details

If you want to see:

- `SearXNGConfig`
- Timeout and fallback strategies
- Local deployment recommendations

Continue to the detail page:

- [Legacy path case page](/docs/integrations/solutions/searxng-web-search)

## 7. Key objects

If you want to dig into the implementation, focus first on:

- `websearch/searxng/SearXNGConfig.java`
- `websearch/ChatWithWebSearchEnhance.java`
- The underlying `IChatService`

These objects are enough to explain why online search is an enhancement chain in AI4J rather than a separate independent runtime.

## 8. When it is the right enhancement

This solution is best suited when:

- External public information is highly time-sensitive
- User questions do not require a private knowledge base
- You want online capability as an optional enhancement rather than the default heavy path

If the questions mainly come from internal knowledge assets, you should go back to the RAG main line first.

## 9. What to constrain up front

- How much context the search results inject
- How to fall back when search fails
- Whether to explicitly surface search sources to the upstream UI or audit chain

Pin down these constraints first, or the online enhancement will turn the answer chain into an unstable black box.
