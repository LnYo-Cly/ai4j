# 外部服务开发上下文：<service-key> / External Development Context: <service-key>

Context Doc Type: external-development-context
Owner: project coordinator
Last Verified: unknown
Confidence: low

## Index Links

| Service Profile | Source Pack | Integration Contracts | Last Verified | Confidence |
| --- | --- | --- | --- | --- |
| `coding-agent-harness/context/architecture/services/<service-key>.md` | `coding-agent-harness/context/development/external-source-packs/<source-key>/README.md` 或 N/A | `coding-agent-harness/context/integrations/<contract>.md` | unknown | low |

## Development Use

[说明 Agent 修改本仓时需要知道的外部服务事实。只写可验证事实、mock 方式和调试入口。]

## Do Not Assume

- [未查看外部仓库或未问负责人前不能成立的假设。]

## Mocks / Stubs

| Scenario | Stub / Mock | Command or Path | Source Evidence |
| --- | --- | --- | --- |

## Cross-Repo Debug Notes

[当问题涉及这个外部服务时，按什么顺序定位、用哪些命令或日志确认。]

## Placement Rule

本文件只写本仓开发、测试、调试这个外部服务时需要的上下文。服务职责放 `context/architecture`，接口字段和 schema 放 `context/integrations`。
