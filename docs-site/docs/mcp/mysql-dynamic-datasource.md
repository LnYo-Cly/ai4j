---
sidebar_position: 5
---

# MySQL 动态 MCP 服务管理

这页必须先说一个边界：

> AI4J 当前没有内置 “MySQL MCP 配置中心” 成品实现，但它已经把这个扩展点预留出来了。

真正的扩展点是：

- `McpConfigSource`
- `McpGatewayConfigSourceBinding`

也就是说，MySQL 动态管理不是现成开关，而是“基于现有配置源 SPI 做一层数据库实现”。

## 1. 为什么这个能力值得单独设计

静态 `mcp-servers-config.json` 很适合：

- 本地开发
- 单服务验证
- 少量固定服务

一旦进入这些场景，文件配置就开始吃力：

- 新增服务要发版
- 停用故障服务响应慢
- 缺少审计和操作人记录
- 难做租户隔离

这时真正需要的不是“把 JSON 放数据库”，而是：

- 配置源可热更新
- 变更可审计
- 服务切换可回滚

## 2. 代码层真正可复用的骨架是什么

当前仓库已经给了 3 个关键点：

### `McpConfigSource`

这是配置源 SPI，要求实现：

- `getAllConfigs()`
- `getConfig(serverId)`
- `addConfigChangeListener(...)`
- `removeConfigChangeListener(...)`

并通过监听器通知：

- `onConfigAdded`
- `onConfigRemoved`
- `onConfigUpdated`

### `FileMcpConfigSource`

这是默认文件版实现，价值不在于“文件”，而在于它给了一个完整参考：

- 载入全部配置
- 基于旧快照和新快照做 diff
- 对外发出增删改事件

### `McpGatewayConfigSourceBinding`

这是最关键的一层桥，它把配置源事件翻译成实际网关动作：

- 新增 -> 创建 client -> `gateway.addMcpClient(...)`
- 更新 -> 重建 client -> `gateway.addMcpClient(...)`
- 删除 -> `gateway.removeMcpClient(...)`

所以，做 MySQL 动态配置时，你真正要补的是“数据库配置源”，不是重写整个 gateway。

## 3. 推荐的数据建模方式

最小表结构至少要能表达下面几类信息：

```sql
CREATE TABLE mcp_service_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  service_id VARCHAR(128) NOT NULL,
  transport_type VARCHAR(32) NOT NULL,
  command_text VARCHAR(255) NULL,
  args_json TEXT NULL,
  url VARCHAR(512) NULL,
  headers_json TEXT NULL,
  env_json TEXT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  version BIGINT NOT NULL,
  tenant_id VARCHAR(128) NULL,
  operator VARCHAR(128) NULL,
  remark VARCHAR(512) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

为什么不建议直接只放一个 `config_json`：

- 查询和审计不方便
- 很难做字段级校验
- 管理台难做结构化编辑

更稳的方案是：

- 核心运行字段结构化存储
- 补充扩展字段再放 JSON

## 4. 不是所有字段都值得进数据库

基于当前 AI4J 运行时，最值得入库并真正会生效的字段是：

- `service_id`
- `type`
- `command`
- `args`
- `env`
- `url`
- `headers`
- `enabled`

而这些字段目前更偏治理元数据：

- `priority`
- `tags`
- `requiresAuth`
- `authTypes`
- `autoReconnect`
- `reconnectInterval`
- `maxReconnectAttempts`
- `connectTimeout`

这不是说它们没价值，而是说当前 runtime 没有把它们全部接线。MySQL 后台如果把它们做成“会即时改变底层行为”的开关，会误导使用者。

## 5. 推荐实现方式：自定义 `McpConfigSource`

一个典型 MySQL 配置源可以长这样：

```java
public class MysqlMcpConfigSource implements McpConfigSource {

    private final List<ConfigChangeListener> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, McpServerConfig.McpServerInfo> cache = new HashMap<>();

    @Override
    public Map<String, McpServerConfig.McpServerInfo> getAllConfigs() {
        return new HashMap<>(cache);
    }

    @Override
    public McpServerConfig.McpServerInfo getConfig(String serverId) {
        return cache.get(serverId);
    }

    @Override
    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    public void reloadFromDatabase() {
        Map<String, McpServerConfig.McpServerInfo> oldSnapshot = new HashMap<>(cache);
        Map<String, McpServerConfig.McpServerInfo> newSnapshot = loadEnabledRows();
        cache = newSnapshot;
        diffAndNotify(oldSnapshot, newSnapshot);
    }
}
```

这条路线的重点不是代码量，而是两件事：

- 始终以“启用配置集”作为有效服务集合
- 每次 reload 后做 diff，再发事件

## 6. 变更是怎么真正进入网关的

只要把 MySQL 配置源绑定给 gateway：

```java
MysqlMcpConfigSource source = new MysqlMcpConfigSource(...);
McpGateway gateway = new McpGateway();
gateway.setConfigSource(source);
gateway.initialize().join();
```

后续你只要让 `source.reloadFromDatabase()` 发出正确的增删改事件，`McpGatewayConfigSourceBinding` 就会自动完成：

- 建 client
- 接入 gateway
- 刷新工具目录
- 下线旧 client

这也是为什么说 MySQL 动态配置是“扩展配置源”，不是“扩展网关”。

## 7. 监听数据库变更有两种常见实现

### 轮询型

- 定时查询 `updated_at` 或版本号
- 对比快照
- 触发 diff

优点：

- 简单
- 好实现

缺点：

- 有延迟
- 高频轮询会给数据库增加负担

### 事件型

- 配合 binlog / CDC / MQ / 管理台事件
- 定向通知配置源刷新

优点：

- 延迟低
- 变更链更清晰

缺点：

- 实现复杂度更高

如果你只是先把平台能力做起来，轮询已经够用。

## 8. 设计时必须补上的 5 个治理点

### 命名治理

`serviceId` 和 tool 名字不能冲突，否则 gateway 的目录映射会互相覆盖。

### 密钥治理

不要把真实 token 明文长期存进 `headers_json`。

更稳的方案是：

- 数据库存密钥引用
- 运行时再解密或从密钥服务注入

### 审计治理

至少记录：

- 谁改了哪个服务
- 改了哪些字段
- 何时生效
- 是否回滚

### 回滚治理

建议保留配置历史版本，不要只保留最新一条。

### 生效治理

更新第三方 MCP 时，最好先做：

1. 配置校验
2. 连通性探测
3. 再切入正式 gateway

不要让无效配置直接污染运行时。

## 9. 与 Agent 的关系

即使服务来源改成 MySQL，Agent 暴露语义也不会变。

Agent 仍然只通过：

```java
.toolRegistry(Collections.<String>emptyList(), Arrays.asList("weather-http"))
```

选择本次可见服务。

也就是说：

- MySQL 管的是“服务目录怎么变”
- Agent 白名单管的是“这次任务看见哪些服务”

## 10. 迁移策略建议

从静态 JSON 迁到 MySQL 时，推荐两阶段：

### 阶段一：双源对照

- 文件配置继续生效
- MySQL 配置仅用于对照和管理台验证

### 阶段二：切主数据库

- gateway 改绑 MySQL `McpConfigSource`
- 文件配置只保留兜底
- 一段观察期后再彻底去掉文件源

这样做的好处是：

- 不会一次性把连接问题、配置问题、平台问题叠在一起

## 11. 这页最该记住的结论

AI4J 当前没有现成的 MySQL MCP 配置中心，但它已经把实现这种能力的关键插槽准备好了：

- `McpConfigSource`
- `McpGatewayConfigSourceBinding`

所以正确做法不是魔改网关，而是补一个数据库配置源，并把变更治理、密钥管理、审计和回滚一起设计进去。
