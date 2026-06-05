# docs-site 文档重构总任务 - 进度

## 状态：进行中

`## 状态` 是受控机器字段，只能使用以下值之一：

- `未开始`
- `计划中`
- `进行中`
- `审查中`
- `已阻塞`
- `已完成`

不要把 `计划审阅中`、`等待 coordinator pass`、`本地审查就绪` 等细粒度协作状态写入本字段。
这些状态应记录到进度记录、残余或协调者交接中。

## 进度记录

证据使用 `type:path:summary` 格式。

允许的 `type`：`command`, `diff`, `fixture`, `screenshot`, `review`, `report`。

证据较长或数量较多时，不要粘贴全文；放入 `artifacts/INDEX.md` 并在这里引用 ID。

### [YYYY-MM-DD HH:MM] - [阶段名称]

- 做了什么：[具体操作]
- 验证结果：[运行了什么检查，结果如何]
- 下一步：[下一步动作]
- 证据：[type:path:summary]

## 残余

- [遗留问题；如无写“无”]

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：pending-coordinator-pass / synced / n/a
- Registry update needed：[module key, step, status, branch, updated / 不适用]
- Harness Ledger update needed：[task plan path, review path, closeout status / 不适用]
- 负责人：coordinator / 不适用

### [2026-06-05 03:28] - task-start

- 做了什么：启动 docs-site 文档重构总任务：分波次把 docs-site 从入口修正推进到更适合大众用户和生产项目接入的 AI SDK 文档；并行只读审计，写入由 coordinator 串行合并。
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-05 11:38] - 并行只读审计收口

- 做了什么：回收 Core/MCP、Agent/Coding/FlowGram、全站 IA 三路只读审计；确认主要问题是 canonical 路径分裂、legacy 目录未降级、主入口页过度像源码解读、缺少版本/安全/生产检查/迁移/排障/选型辅助页。
- 验证结果：审计未修改文件，结论已投影到 `findings.md` 和 `task_plan.md`。
- 下一步：按 coordinator 串行写入 docs-site。
- 证据：review:subagent:019e95cd-25ef-7610-b044-480d7aa21a2b:Core SDK/MCP/ai-basics 只读审计完成
- 证据：review:subagent:019e95cd-61ed-7b11-ab65-ace9b2b91cad:Agent/Coding Agent/FlowGram 只读审计完成
- 证据：review:subagent:019e95cd-9fec-72f0-86f0-be0063f41b12:全站 IA/legacy/生产辅助页只读审计完成

### [2026-06-05 11:52] - docs-site IA 与主入口改造

- 做了什么：新增 documentation map、Reference、Security、Operations、Migration、Troubleshooting、Comparison 页面；更新 Docusaurus include、sidebar、footer、Start Here、FAQ、Glossary 和 MCP 正式入口；重写 Core/Agent/Coding Agent/FlowGram 总览页。
- 验证结果：局部搜索确认新增/改动范围内不再出现生硬营销式措辞。
- 下一步：运行 docs-site build。
- 证据：diff:docs-site:新增生产接入辅助页、canonical map、主入口页重写和 sidebar/include 更新

### [2026-06-05 12:00] - docs-site build

- 做了什么：在 `docs-site/` 执行 `npm run build`；第一次因两个 `Advanced` sidebar 分类 translation key 冲突失败，随后为 Coding Agent 和 FlowGram Advanced 分类增加唯一 key 并重跑。
- 验证结果：第二次 `npm run build` 通过，Docusaurus 生成静态文件到 `docs-site/build`。
- 下一步：强制添加被 `.gitignore` 隐藏的新 docs 文件，执行 harness review 和本地提交。
- 证据：command:docs-site:npm run build passed after sidebar key fix

### [2026-06-05 12:12] - Spring Boot 与 Solutions 入口收口

- 做了什么：重写 `spring-boot/overview.md` 和 `solutions/overview.md`，统一为“定位、适用场景、最小路径、边界、上线前检查、继续阅读”的入口结构。
- 验证结果：再次在 `docs-site/` 执行 `npm run build`，构建通过。
- 下一步：执行 git 边界检查、harness task-review、本地提交。
- 证据：command:docs-site:npm run build passed after spring-boot and solutions overview rewrite
