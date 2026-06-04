# Webhook 契约：<webhook-name> / Webhook Contract: <webhook-name>

Context Doc Type: webhook-contract
Owner: project coordinator
Last Verified: unknown
Confidence: low

## Contract Type

Webhook

## Index Links

| Service Profile | Development Context | Contract Index | Last Verified | Confidence |
| --- | --- | --- | --- | --- |
| `coding-agent-harness/context/architecture/services/<service-key>.md` | `coding-agent-harness/context/development/external-context/<service-key>.md` | `coding-agent-harness/context/integrations/README.md` | unknown | low |

## Auth

[签名、token、IP allowlist 或其他认证方式。不要写入真实密钥。]

## Payload

[Webhook body schema 或 schema 路径。]

## Source Evidence

[代码、供应商文档、负责人说明或发现命令。]

## Errors

[重试、超时、幂等行为。]

## Contract Tests

| Test | Command / Path | Expected Result |
| --- | --- | --- |

## Placement Rule

本文件只写一个 webhook 契约。服务职责放 `context/architecture`，本地 mock/stub/debug 放 `context/development`。
