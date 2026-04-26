---
sidebar_position: 5
---

# MySQL 动态 MCP 服务管理

历史主题来源：通过 MySQL 管理 MCP 服务配置，实现“热更新而非重启发布”。

## 1. 为什么要动态管理

静态配置文件在以下场景成本很高：

- 新增服务需要发布
- 停用故障服务响应慢
- 缺少统一审计

动态管理的目标是：**配置可治理、服务可热更新、操作可审计**。

## 2. 示例表结构

```sql
CREATE TABLE mcp_service_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  service_id VARCHAR(128) NOT NULL,
  service_name VARCHAR(255) NOT NULL,
  type VARCHAR(32) NOT NULL,
  config_json TEXT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

建议补充字段：

- `version`：配置版本
- `operator`：操作人
- `tenant_id`：租户标识
- `remark`：变更说明

## 3. 启动流程

1. 启动时读取 `enabled=1` 的服务配置
2. 反序列化为 Gateway 服务定义
3. 初始化 `McpGateway`
4. 做健康检查后再对外暴露

## 4. 运行时流程

- **新增服务**：插入 DB -> 校验 -> 热加载到网关
- **停用服务**：更新 DB -> 从网关摘除
- **更新服务**：版本比较 -> 安全切换 -> 记录审计

## 5. 关键治理点

### 5.1 变更安全

- 变更前后快照
- 灰度生效
- 快速回滚

### 5.2 权限控制

- 仅平台管理员可变更服务配置
- 关键服务变更可加二次确认

### 5.3 运行监控

- 按服务维度统计调用成功率
- 慢服务告警
- 连续失败自动摘除（可选）

## 6. 与 Agent 协同

动态 MCP 服务可作为 Agent 的可选工具池，配合 `toolRegistry` 做场景化暴露：

- 财务场景只暴露财务相关工具
- 运维场景只暴露运维相关工具

## 7. 迁移建议

从静态配置迁移到动态配置时，建议两阶段：

1. 双读（静态 + 动态）验证一致性
2. 切主动态后保留静态兜底一个版本周期
