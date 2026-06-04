# docs site wave 1 entrance redesign - 进度

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

### [2026-06-04 12:05] - task-start

- 做了什么：开始 docs-site Wave 1 入口修正：仅修改 intro、why-ai4j、新增 feature-map 并挂入 sidebar；不迁移旧目录、不删除旧页面。
- 验证结果：已记录
- 下一步：继续执行
- 证据：n/a

### [2026-06-04 20:12] - docs-site entrance edits

- 做了什么：重写 `docs-site/docs/intro.md` 和 `docs-site/docs/start-here/why-ai4j.md`，新增 `docs-site/docs/start-here/feature-map.md`，并在 `docs-site/sidebars.ts` 挂入 `start-here/feature-map`。
- 验证结果：编辑完成，等待构建验证。
- 下一步：运行 docs-site build 和 diff check。
- 证据：diff:docs-site/docs/intro.md;docs-site/docs/start-here/why-ai4j.md;docs-site/docs/start-here/feature-map.md;docs-site/sidebars.ts:入口页、定位页、功能地图和 sidebar 已更新

### [2026-06-04 20:17] - docs-site build and diff check

- 做了什么：运行 docs-site 生产构建和仓库 diff 检查。
- 验证结果：`npm run build` 在 `docs-site/` 成功；`git diff --check` 成功，仅有 Windows LF/CRLF 转换 warning；`docs-site/build` 未出现在 git status 中。
- 下一步：补齐 review / walkthrough，提交本地 commit 后推进 harness lifecycle。
- 证据：command:docs-site:npm run build success; command:repo-root:git diff --check success with LF/CRLF warnings only; report:coding-agent-harness/planning/tasks/2026-06-04-docs-site-wave-1-entrance-redesign-54198b78/artifacts/INDEX.md:ART-005 and ART-006
