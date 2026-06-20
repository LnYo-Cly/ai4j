# CLI permissions command UX - Execution Strategy

## 策略

以最小 CLI host 切片实现 `/permissions`：先补 command parity，再补 docs 和 tests。不跨到 `ai4j-agent` 或 `ai4j-coding`。

## 顺序

1. 注册 slash root 和 completion；
2. CLI runtime 输出只读 summary；
3. ACP 输出同样 summary；
4. help/docs 同步；
5. targeted tests；
6. broad CLI + docs build；
7. Harness review/PR。

## 停止条件

- 需要编辑 runtime permission policy API；
- 需要支持运行时切换 approval mode；
- 需要打印 raw tool payload；
- 需要真实 provider 才能验证。
