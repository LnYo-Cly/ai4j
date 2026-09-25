package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.vector.service.PineconeService;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.chroma.ChromaVectorStore;
import io.github.lnyocly.ai4j.vector.store.elasticsearch.ElasticsearchVectorStore;
import io.github.lnyocly.ai4j.vector.store.pinecone.PineconeVectorStore;
import io.github.lnyocly.ai4j.vector.store.qdrant.QdrantVectorStore;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class VectorStoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfigAutoConfiguration.class);

    @Test
    public void test_vector_stores_are_opt_in() {
        contextRunner.run(context -> {
            Assert.assertFalse(context.containsBean("elasticsearchVectorStore"));
            Assert.assertFalse(context.containsBean("chromaVectorStore"));
            Assert.assertFalse(context.containsBean("pineconeVectorStore"));
            Assert.assertFalse(context.containsBean("pineconeService"));
            Assert.assertFalse(context.containsBean("qdrantVectorStore"));
            Assert.assertFalse(context.containsBean("vectorStore"));
        });
    }

    @Test
    public void test_single_enabled_store_is_the_primary_vector_store() {
        contextRunner
                .withPropertyValues("ai.vector.chroma.enabled=true")
                .run(context -> {
                    Assert.assertTrue(context.containsBean("vectorStore"));
                    Assert.assertTrue(context.getBean(VectorStore.class) instanceof ChromaVectorStore);
                });
    }

    @Test
    public void test_primary_property_selects_among_multiple_enabled_stores() {
        contextRunner
                .withPropertyValues(
                        "ai.vector.chroma.enabled=true",
                        "ai.vector.qdrant.enabled=true",
                        "ai.vector.primary=qdrant")
                .run(context -> {
                    Assert.assertTrue(context.getBean(VectorStore.class) instanceof QdrantVectorStore);
                    Assert.assertTrue(context.getBean("chromaVectorStore") instanceof ChromaVectorStore);
                    Assert.assertTrue(context.getBean("qdrantVectorStore") instanceof QdrantVectorStore);
                });
    }

    @Test
    public void test_multiple_enabled_stores_without_primary_fails_fast() {
        contextRunner
                .withPropertyValues(
                        "ai.vector.chroma.enabled=true",
                        "ai.vector.qdrant.enabled=true")
                .run(context -> {
                    Assert.assertNotNull(context.getStartupFailure());
                    Assert.assertTrue(context.getStartupFailure().getMessage().contains("ai.vector.primary"));
                });
    }

    @Test
    public void test_primary_pointing_to_disabled_store_fails_fast() {
        contextRunner
                .withPropertyValues(
                        "ai.vector.chroma.enabled=true",
                        "ai.vector.primary=elasticsearch")
                .run(context -> {
                    Assert.assertNotNull(context.getStartupFailure());
                    Assert.assertTrue(context.getStartupFailure().getMessage().contains("ai.vector.primary"));
                });
    }

    @Test
    public void test_elasticsearch_vector_store_is_created_when_enabled() {
        contextRunner
                .withPropertyValues(
                        "ai.vector.elasticsearch.enabled=true",
                        "ai.vector.elasticsearch.host=http://localhost:9200",
                        "ai.vector.elasticsearch.index-name=kb_vectors",
                        "ai.vector.elasticsearch.vector-dim=512")
                .run(context -> {
                    Assert.assertTrue(context.containsBean("elasticsearchVectorStore"));
                    Assert.assertNotNull(context.getBean(ElasticsearchVectorStore.class));

                    ElasticsearchConfigProperties properties =
                            context.getBean(ElasticsearchConfigProperties.class);
                    Assert.assertEquals("kb_vectors", properties.getIndexName());
                    Assert.assertEquals(512, properties.getVectorDim());
                });
    }

    @Test
    public void test_chroma_vector_store_is_created_when_enabled() {
        contextRunner
                .withPropertyValues(
                        "ai.vector.chroma.enabled=true",
                        "ai.vector.chroma.collection=kb_docs")
                .run(context -> {
                    Assert.assertTrue(context.containsBean("chromaVectorStore"));
                    Assert.assertNotNull(context.getBean(ChromaVectorStore.class));

                    ChromaConfigProperties properties = context.getBean(ChromaConfigProperties.class);
                    Assert.assertEquals("kb_docs", properties.getCollection());
                });
    }

    @Test
    public void test_pinecone_vector_store_is_created_when_enabled() {
        contextRunner
                .withPropertyValues("ai.vector.pinecone.enabled=true")
                .run(context -> {
                    Assert.assertTrue(context.containsBean("pineconeService"));
                    Assert.assertTrue(context.containsBean("pineconeVectorStore"));
                    Assert.assertNotNull(context.getBean(PineconeVectorStore.class));
                    Assert.assertNotNull(context.getBean(PineconeService.class));
                });
    }
}
