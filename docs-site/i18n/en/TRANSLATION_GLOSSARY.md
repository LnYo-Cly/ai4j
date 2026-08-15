# Translation Glossary (zh → en)

Authoritative term map for translating AI4J docs to English. Every translator
agent MUST apply these mappings consistently. When a term is not listed, use the
most standard technical English equivalent and stay consistent with related
entries below.

## Hard rules

- **Never translate**: class names, interface names, package names, method names,
  CLI flags, env vars, config keys, file names, code in fenced blocks. These stay
  exactly as-is.
- **Never translate (brands/protocols/products)**: AI4J, MCP, A2A, FlowGram,
  Spring Boot, Docusaurus, RAG, JDK, Maven, OpenAI, Anthropic, DeepSeek,
  Pinecone, Milvus, Redis, Milvus, E2B, Daytona, CubeSandbox, OTel, Langfuse,
  GraalVM, SearXNG, ACP, TUI, CLI.
- **Translate prose** to natural technical English — not word-for-word.
- **Keep all markdown structure**: headings, tables, code fences, links,
  admonitions (`:::note` / `:::warning` — translate the content, keep the fences).
- **Frontmatter**: translate `title` and `description`; keep `sidebar_position`,
  `tags`, and any other keys. If a translated `description` contains an ASCII
  colon `:`, wrap the whole value in double quotes (YAML safety).
- **Punctuation**: use standard English punctuation. Chinese full-width `，。：；""（）`
  → English `, . : ; " " ( )`. Em dash `—` is fine.

## Term map

### Architecture & SDK structure
| zh | en |
|---|---|
| 能力 | capability |
| 扩展 | extension |
| 插件 | plugin |
| 模块 | module |
| 入口 | entry point |
| 总览 | overview |
| 架构 | architecture |
| 接入 | integration / wiring |
| 取用 / 按需取用 | adopt / take on demand |
| 默认 | default |
| 配置 | configuration |
| 自动装配 | auto-configuration |
| 基座 | foundation / base |
| 边界 | boundary |
| 心智模型 | mental model |
| 落地 | ship / land (as implemented) |
| 对齐 | align (with) |
| 兜底 / 回退 | fallback |
| 暴露 | expose |
| 声明 | declare |
| 注册 | register |
| 发现 | discovery |
| 升级（向上） | upgrade (to the next layer) |

### Model & Chat
| zh | en |
|---|---|
| 模型 | model |
| 模型调用 | model call |
| 流式 / 流式响应 | streaming / streamed response |
| 多模态 | multimodal |
| 请求 | request |
| 响应 | response |
| 提供方 | provider |
| 平台 | platform |
| 提示词 | prompt |
| 指令 | instruction |
| 系统提示 | system prompt |
| 推理 | reasoning |
| 思维链 | chain-of-thought |
| 令牌 / token | token |
| 用量 | usage |

### Tools / Skills / MCP
| zh | en |
|---|---|
| 工具 | tool |
| 工具调用 | tool call |
| 函数调用 | function call |
| 技能 | skill |
| 协议 | protocol |
| 网关 | gateway |
| 传输 | transport |
| 白名单 | allowlist / whitelist |
| 执行器 | executor |

### Agent runtime
| zh | en |
|---|---|
| 代理 / 智能体 | agent |
| 运行时 | runtime |
| 会话 | session |
| 记忆 | memory |
| 上下文 | context |
| 轮次 | turn |
| 步骤 | step |
| 循环 | loop |
| 编排 | orchestration |
| 子代理 | subagent |
| 团队 | team |
| 移交 | handoff |
| 拦截器 | interceptor |
| 钩子 | hook |
| 生命周期 | lifecycle |
| 蓝图 | blueprint |
| 工作流 | workflow |

### RAG & search
| zh | en |
|---|---|
| 检索 | retrieval |
| 召回 | recall |
| 重排 | rerank / reranking |
| 嵌入 | embedding |
| 向量 | vector |
| 向量库 | vector store |
| 分块 | chunking |
| 摄取 | ingestion |
| 混合检索 | hybrid retrieval |
| 引用 | citation |
| 命中 | hit |
| 标注 | ground-truth label / annotation |
| 评测 / 评估 | evaluation |
| 评判 | judge |
| 忠实度 | faithfulness |

### Compaction / context / memory
| zh | en |
|---|---|
| 压缩 | compaction |
| 投影 | projection |
| 摘要 | summary |
| 脱敏 | masking |
| 截断 | truncation |
| 快照 | snapshot |
| 恢复 | recovery / resume |
| 重放 | replay |
| 审计 | audit |
| 防篡改 | tamper-evident |
| 占位符 | placeholder |
| 逐字节 / 逐项 | byte-for-byte / item-for-item |

### Sandbox & security
| zh | en |
|---|---|
| 沙箱 | sandbox |
| 隔离 | isolation |
| 鉴权 / 认证 | authentication |
| 授权 | authorization |
| 审批 | approval |
| 权限 | permission |
| 越界 | out-of-bounds |

### Production / ops
| zh | en |
|---|---|
| 生产 | production |
| 部署 | deployment |
| 可观测性 | observability |
| 追踪 | tracing |
| 指标 | metrics |
| 故障排查 | troubleshooting |
| 迁移 | migration |
| 版本兼容 | version compatibility |
| 发布 | release |
| 制品 | artifact |
| 检查清单 | checklist |
| 治理 | governance |

### Common phrasing
| zh | en |
|---|---|
| 一句话 | in one sentence / TL;DR |
| 关键 / 要点 | key point |
| 适合 | suitable for / fits |
| 刻意 | deliberately / by design |
| 不是…而是… | not … but rather … |
| 注意 / 提醒 | note / caveat |
| 不要混 | keep distinct / don't conflate |
| 即 | i.e. |
| 例如 | e.g. |
| 见 | see |
| 推荐继续阅读 | further reading |
