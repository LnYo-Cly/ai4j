# Visual Map

Visual Map Contract: v1.0

## 状态表

| Phase ID | Kind | Depends On | State | Completion | Output | Required Evidence | Actor | Evidence Status | Blocking Risk | Owner / Handoff |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| INIT-01 | init | none | done | 100 | 任务包就位 | task_plan 等 | coordinator | present | none | coordinator |
| EXEC-01 | execution | INIT-01 | done | 100 | MessagesModelClient + StreamBridge | `MessagesModelClientTest` 3 tests | coordinator | present | none | coordinator |
| GATE-01 | gate | EXEC-01 | review | 100 | ai4j-agent 回归 + live 覆盖（经 P1 IMessagesService） | progress | coordinator | present | 人工确认 | human |
