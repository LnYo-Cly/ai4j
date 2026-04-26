# Annotation-based Tools

AI4J 推荐用注解来声明本地函数工具。

## 1. 三个核心注解

- `@FunctionCall`
- `@FunctionRequest`
- `@FunctionParameter`

## 2. 这套方式的好处

- 工具 schema 更稳定
- 参数描述更清楚
- 比手写原始对象更容易维护

## 3. 适合什么场景

- 本地 Java 工具
- 需要稳定输入结构
- 想和 `Chat`、`Responses`、`Agent` 复用同一套工具描述
