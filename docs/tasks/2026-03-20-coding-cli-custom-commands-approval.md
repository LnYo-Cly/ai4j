# 2026-03-20 Coding CLI Custom Commands And Approval

## Goal
为 coding-agent CLI/TUI 补齐 custom commands 与 permission/approval 基础层。

## Scope
- [completed] 1. 梳理现有 slash command / tool 执行入口
- [completed] 2. 实现 custom command registry 与加载规则
- [completed] 3. 实现 CLI 内 `/commands` 与 `/cmd <name>`
- [completed] 4. 实现 permission/approval 策略与终端确认
- [completed] 5. 补充并通过测试

## Deliverables
- custom command registry
- workspace/home 命令加载规则
- permission / approval strategy
- CLI 入口与测试

## Verification
- `mvn -pl ai4j-cli -am -DskipTests=false "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=CodeCommandTest,Ai4jCliTest,FileCodingSessionStoreTest,FileSessionEventStoreTest,DefaultCodingSessionManagerTest,CodeCommandOptionsParserTest,DefaultCodingCliAgentFactoryTest,TuiConfigManagerTest" test`

## Notes
- 本任务文档仅用于实施过程，不纳入 commit。
- 当前为基础层：workspace/home 自定义命令模板 + `auto|safe|manual` 审批模式。
