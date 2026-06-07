# docs site wave 1 real onboarding recipes - 进度

## 状态：审查中

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

## 残余

- 无

## 协调者交接（Coordinator，启用模块并行时填写）

- Global sync status：n/a
- Registry update needed：不适用
- Harness Ledger update needed：task lifecycle CLI 已同步 generated ledger
- 负责人：coordinator

### [2026-06-07 15:47] - task-start

- 做了什么：开始 docs-site Wave 1 真实接入路径重写；范围限定 canonical docs-site/docs。
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-07 20:08] - docs-update

- 做了什么：新增 OpenAI-compatible/TroveBox recipe，挂入 sidebar；更新 five-minute first chat、Java quickstart、Spring Boot quickstart、Spring Boot configuration reference、Core SDK service registry 页面。
- 验证结果：文本扫描确认 TroveBox/profile 链接存在；`ChatClient.openAi` / `Ai4j.chat()` 只在“不推荐/不作为主入口”语境出现。
- 下一步：运行 docs-site build。
- 证据：diff:TARGET:docs-site/docs:start-here and config pages updated; diff:TARGET:docs-site/sidebars.ts:new recipe reachable; command:TARGET:.:`rg -n "ChatClient\\.openAi|Ai4j\\.chat\\(" docs-site/docs README.md`

### [2026-06-07 20:10] - verification

- 做了什么：运行 docs-site production build。
- 验证结果：`npm run build` 通过，Docusaurus 生成静态文件成功。
- 下一步：提交变更并提交 agent review。
- 证据：command:TARGET:docs-site:`npm run build`

### [2026-06-07 15:56] - task-review

- 做了什么：docs-site Wave 1 onboarding recipes updated; OpenAI-compatible/TroveBox page added and docs-site build passed.
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a
