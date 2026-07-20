# Embedding / RAG

[Back to English README](../../../README-EN.md) · [中文 README](../../../README.md)

## Embedding service

```java
public void test_embed() throws Exception {
    // Obtain embedding service instance
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // Build request parameters
    Embedding embeddingReq = Embedding.builder().input("1+1").build();

    // Send embedding request
    EmbeddingResponse embeddingResp = embeddingService.embedding(embeddingReq);

    System.out.println(embeddingResp);
}
```

## RAG
### Configure vector database
```yml
ai:
  vector:
    pinecone:
      url: ""
      key: ""
```
### Obtain instance
```java
@Autowired
private PineconeService pineconeService;
```
### Insert into vector database
```java
public void test_insert_vector_store() throws Exception {
    // Obtain embedding service instance
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // Read file content using Tika
    String fileContent = TikaUtil.parseFile(new File("D:\\data\\test\\test.txt"));

    // Split text content
    RecursiveCharacterTextSplitter recursiveCharacterTextSplitter = new RecursiveCharacterTextSplitter(1000, 200);
    List<String> contentList = recursiveCharacterTextSplitter.splitText(fileContent);

    // Convert to vector
    Embedding build = Embedding.builder()
            .input(contentList)
            .model("text-embedding-3-small")
            .build();
    EmbeddingResponse embedding = embeddingService.embedding(build);
    List<List<Float>> vectors = embedding.getData().stream().map(EmbeddingObject::getEmbedding).collect(Collectors.toList());
    VertorDataEntity vertorDataEntity = new VertorDataEntity();
    vertorDataEntity.setVector(vectors);
    vertorDataEntity.setContent(contentList);

    // Vector storage
    Integer count = pineconeService.insert(vertorDataEntity, "userId");

}
```
### Query from vector database
```java
public void test_query_vector_store() throws Exception {
    // // Obtain embedding service instance
    IEmbeddingService embeddingService = aiService.getEmbeddingService(PlatformType.OPENAI);

    // Build the question to be queried and convert it to a vector
    Embedding build = Embedding.builder()
            .input("question")
            .model("text-embedding-3-small")
            .build();
    EmbeddingResponse embedding = embeddingService.embedding(build);
    List<Float> question = embedding.getData().get(0).getEmbedding();

    // Build the query object for the vector database
    PineconeQuery pineconeQueryReq = PineconeQuery.builder()
            .namespace("userId")
            .vector(question)
            .build();

    String result = pineconeService.query(pineconeQueryReq, " ");

    // Carry the result and have a conversation with the chat service.
    // ......
}
```

### Delete data from vector database
```java
public void test_delete_vector_store() throws Exception {
    // Build parameters
    PineconeDelete pineconeDelete = PineconeDelete.builder()
                                    .deleteAll(true)
                                    .namespace("userId")
                                    .build();
    // Delete
    Boolean res = pineconeService.delete(pineconeDelete);
}
```
