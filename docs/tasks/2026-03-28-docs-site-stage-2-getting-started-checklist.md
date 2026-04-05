# docs-site 第二阶段实施清单

日期：2026-03-28

## 目标

第二阶段聚焦一件事：

让新用户可以在官网里快速完成依赖引入、版本判断和第一个成功调用。

---

## 范围

### 1. 安装链路

- [x] 补 Maven 直接依赖
- [x] 补 BOM 引用方式
- [x] 补 Gradle 引用方式
- [x] 补 Spring Boot starter 与 Flowgram starter 坐标

### 2. 版本说明

- [x] 增加版本与兼容性页面
- [x] 明确 JDK 基线
- [x] 明确 Spring Boot starter 基线
- [x] 说明 BOM 的适用场景

### 3. 最小示例

- [x] 修正 `JDK8 + OpenAI` 页面为非 Spring 最小示例
- [x] 覆盖同步调用、流式调用、Tool 调用
- [x] 重写 Spring Boot 快速开始为真实首调路径
- [x] 明确“先同步、再流式、再工具”的验证顺序

### 4. 排障

- [x] 补依赖/BOM 混用排障
- [x] 补 Spring Boot 无 `AiService` 排障
- [x] 保留流式、Tool、编码等常见问题

---

## 文件级变更

- [x] `docs-site/docs/getting-started/installation.md`
- [x] `docs-site/docs/getting-started/version-compatibility.md`
- [x] `docs-site/docs/getting-started/quickstart-openai-jdk8.md`
- [x] `docs-site/docs/getting-started/quickstart-springboot.md`
- [x] `docs-site/docs/getting-started/troubleshooting.md`
- [x] `docs-site/sidebars.ts`

---

## 验收标准

- [x] 用户能快速找到正确依赖坐标
- [x] 用户能判断自己项目的版本基线是否合适
- [x] 非 Spring 与 Spring Boot 各自都有清晰的首调路径
- [x] 站点构建通过
