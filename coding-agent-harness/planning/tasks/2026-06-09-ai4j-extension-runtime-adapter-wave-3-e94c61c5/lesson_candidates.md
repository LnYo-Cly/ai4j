# AI4J extension runtime adapter wave 3 - 教训候选

本文件是任务本地 lesson candidate queue。人工审查需要决定候选是留在任务内、拒绝、进入 dry-run promotion、创建 promoted lesson 详情文档，还是创建单独的沉淀任务。

## Candidate Status

| Field | Value |
| --- | --- |
| Schema version | lesson-candidate-v1 |
| Task-level status | no-candidate-accepted |
| Review gate | candidate-file-present |
| Review decision | no reusable governance lesson for this task |
| Promotion state | not-promoted |
| Closeout token | checked-none |
| Source task | 2026-06-09-ai4j-extension-runtime-adapter-wave-3-e94c61c5 |
| Owner | coordinator |
| Last updated | 2026-06-09 |

## Candidates

| ID | Row Status | Title | Scope | Module Key | Detail Artifact | Boundary Reason | Why It Might Matter | Review Decision | Promotion Target | Conflict Check | Required Standard Update | Follow-up Task |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## No-Candidate Reason

本任务按既有 harness 流程执行，没有形成需要沉淀到共享治理标准的新流程经验。主要经验是产品/架构事实：插件工具必须保持 discover / enable / expose 三段式门禁；该事实已写入 `findings.md`、docs-site 插件包文档和回归测试，不需要 promotion 到全局 lesson。

## Promotion Notes

- 无候选需要 dry-run promotion。
- 无后续 lesson 沉淀任务。

## Queue Routing

| Queue | When this task enters it | Exit condition |
| --- | --- | --- |
| Lessons | no | 已明确 no-candidate-accepted。 |
| Missing Materials | no | 候选文件存在，且包含 no-candidate reason。 |
| Confirmed / Finalized | yes | 待 review/closeout 完成。 |
