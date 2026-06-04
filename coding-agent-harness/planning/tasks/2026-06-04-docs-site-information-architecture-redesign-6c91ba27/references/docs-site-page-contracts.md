# docs-site 页面写作合同

## 总原则

每个页面必须先回答“用户为什么要读这页”，再给可运行路径，最后再解释边界和深层原理。不要一上来讲类图、源码或完整体系。

## 首页合同：`intro.md`

目标：说服第一次访问者继续读，并让他选对入口。

结构：

1. AI4J 是什么：一句话。
2. 适合谁：普通 Java / Spring Boot / 国内模型 / 渐进式 Agent。
3. 立即开始：Plain Java、Spring Boot、Feature Map 三个入口。
4. 能力概览：Model、Tool、RAG、MCP、Agentic Extensions，每项一句话。
5. 下一步：Choose Your Path。

禁止：

- 不放长功能清单。
- 不讲内部模块细节。
- 不把 preview 能力包装成主卖点。

## Feature Map 合同：`start-here/feature-map.md`

目标：完整列出 AI4J 特色功能，解决“每个点都要说出来”的诉求。

结构：

1. 状态标签说明。
2. 能力总表。
3. 按模块分组的能力说明。
4. stable 能力推荐学习路径。
5. advanced / preview 能力使用边界。

必备字段：

- Capability
- Status
- Module
- Best for
- Start here
- Deep dive

## Quickstart 合同

目标：让用户复制后能跑，不负责讲完整原理。

结构：

1. 你会得到什么。
2. 前置条件。
3. Maven 依赖。
4. 最小配置。
5. 最小 Java 代码。
6. 运行命令。
7. 成功输出长什么样。
8. 常见失败。
9. 下一步。

约束：

- 示例优先 Java 8 可用写法。
- 避免 Lombok-only 示例。
- API key 必须来自 env 或本地配置，不写死。
- 不引入不必要框架。

## Capability Page 合同

目标：讲清楚一个能力解决什么问题、什么时候用、怎么用、边界是什么。

结构：

1. 这个能力解决什么。
2. 什么时候该用。
3. 什么时候不该用。
4. 最小示例。
5. Plain Java 用法。
6. Spring Boot 用法（如适用）。
7. 配置项。
8. 和其他能力的关系。
9. 常见问题。
10. Reference links。

## Reference Page 合同

目标：给已经决定使用的人查完整参数、类型、行为边界。

结构：

1. 适用范围。
2. API / 配置表。
3. 默认行为。
4. 边界和异常。
5. 版本兼容。
6. 示例索引。

Reference 可以深入，但不要替代 Quickstart。

## Solution Page 合同

目标：展示一个完整可复用方案。

结构：

1. 场景。
2. 架构。
3. 依赖。
4. 核心配置。
5. 核心代码。
6. 验证方式。
7. 可替换点。
8. 生产注意事项。

Solution 里可以引用多个能力页，但不要重新定义这些能力。

## Advanced / Preview Page 合同

适用于 Agent、Coding Agent、FlowGram 等能力。

额外要求：

- 开头写状态标签。
- 说明 API / 行为是否可能变化。
- 明确它不是第一次接入 AI4J 的必经路径。
- 给出最小成功路径，不要只讲架构。

## 旧页面迁移合同

每个旧页迁移前先标注：

| 字段 | 说明 |
| --- | --- |
| Current path | 旧路径 |
| Canonical target | 新 canonical 页 |
| Action | keep / merge / redirect / archive |
| Reason | 为什么这么处理 |
| Link risk | 是否可能断外链 |

未完成映射前，不删除旧页。
