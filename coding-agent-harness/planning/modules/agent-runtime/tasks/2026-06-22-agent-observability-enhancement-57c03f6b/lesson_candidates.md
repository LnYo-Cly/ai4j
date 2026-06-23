# Agent observability enhancement - Lesson candidates

## 检查结论

- Lesson check status：checked-none
- 结论：本任务没有需要立即沉淀到共享 Harness lessons 的通用规则。

## 候选评估

| Candidate | 是否沉淀 | 原因 | 后续 |
| --- | --- | --- | --- |
| Runtime 新增方法签名时检查子类 override | 否 | 属于本任务代码审查经验，暂不构成 repo-wide 流程规则 | 后续若再次出现类似回归，再沉淀为 engineering lesson |
| Auto-continue hidden instructions 不应写入 user memory | 否 | 是 coding loop 的产品语义，已通过测试固化 | 保留在测试中 |
| clear/release 类方法应与 sibling 方法同样幂等（null-guard 早返回） | 否 | 是 AgentSession 内部实现一致性，已通过 `clearSandboxShouldBeIdempotentAndNoopWithoutBinding` 固化 | 若跨模块反复出现同类非幂等清理方法，再沉淀为 engineering lesson |

## 备注

真实防线已经落到单测：`AgentBlueprintFactoryTest#shouldMapCodeActWorkflowToCodeActRuntime`、`CodingAgentLoopControllerTest.shouldContinueWithHiddenInstructionsWithoutAddingExtraUserMessage`、`AgentSessionSandboxBindingTest#clearSandboxShouldBeIdempotentAndNoopWithoutBinding`，以及 3 个 `*SessionEventSupportTest#toSessionEventShouldPreserveCorrelationFields`。
