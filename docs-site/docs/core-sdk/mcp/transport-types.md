# Transport Types

这一页只讲 transport 选型。

## 1. STDIO

适合本地子进程 MCP 工具。

## 2. SSE

适合事件流式远端服务。

## 3. Streamable HTTP

适合服务化发布和标准 HTTP 场景。

## 4. 一个实用判断

- 本地工具进程：优先 `STDIO`
- 服务化发布：优先 `Streamable HTTP`
