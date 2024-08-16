# ai4j
一款JavaSDK整合Openai、Zhipu等大平台的AI接口，将各个平台的请求与相应均转换为Openai格式，消除差异化。


## 支持的平台
+ OpenAi
+ 待添加

## 支持的服务
+ Chat Completions
+ Embedding
+ 待添加

## 特性
+ 支持Spring以及普通Java应用、支持JDK1.8
+ 统一的输入输出
+ 轻松使用Tool Calls
+ 内置向量数据库支持: Pinecone


## 导入

### Maven
```xml
<!-- 非Spring应用 -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j</artifactId>
    <version>${project.version}</version>
</dependency>

```
```xml
<!-- Spring应用 -->
<dependency>
    <groupId>io.github.lnyo-cly</groupId>
    <artifactId>ai4j-spring-boot-stater</artifactId>
    <version>${project.version}</version>
</dependency>
```
## 快速开始

### 获取服务实例

#### 非Spring获取

#### Spring获取
```java
@Autowired
private AiService aiService;
```

### Chat Completions