package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.MessageRole;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.Protocol;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RagContextProjectorTest {

    @Test
    @SuppressWarnings("unchecked")
    public void projectsSystemContextMessageAndMetadataWithoutMutatingOriginal() throws Exception {
        RagChunk chunk = RagChunk.builder()
                .chunkId("chunk-1")
                .documentId("doc-1")
                .content("Cordis uses ServiceKey for typed DI and Disposable hooks for cleanup.")
                .pageNumber(Integer.valueOf(3))
                .sectionTitle("Architecture")
                .build();
        DefaultRagService ragService = new DefaultRagService(Bm25Retriever.fromChunks(Collections.singletonList(chunk)));

        ModelRequest original = ModelRequest.builder()
                .protocol(Protocol.CHAT_COMPLETIONS)
                .model("gpt-4o-mini")
                .message(Message.system().text("You are a helpful assistant.").build())
                .message(Message.user().text("How does Cordis manage cleanup?").build())
                .temperature(Double.valueOf(0.2))
                .applicationMetadata(Collections.<String, Object>singletonMap("traceId", "req-1"))
                .build();

        RagContextProjector projector = RagContextProjector.builder()
                .maxContextChars(500)
                .injectAsSystemMessage(true)
                .build();

        ModelRequest projected = projector.project(original, ragService);

        Assert.assertEquals(2, original.getItems().size());
        Assert.assertEquals(3, projected.getItems().size());
        Assert.assertEquals(Protocol.CHAT_COMPLETIONS, projected.getProtocol());
        Assert.assertEquals("gpt-4o-mini", projected.getModel());
        Assert.assertEquals("req-1", projected.getApplicationMetadata().get("traceId"));

        Message injected = (Message) projected.getItems().get(1);
        Assert.assertEquals(MessageRole.SYSTEM, injected.getRole());
        Assert.assertTrue(injected.textContent().contains("Cordis uses ServiceKey"));
        Assert.assertEquals("How does Cordis manage cleanup?",
                injected.getApplicationMetadata().get(RagContextProjector.META_RAG_QUERY));
        Assert.assertEquals(Integer.valueOf(1),
                injected.getApplicationMetadata().get(RagContextProjector.META_RAG_HIT_COUNT));
        List<Map<String, Object>> citations =
                (List<Map<String, Object>>) injected.getApplicationMetadata().get(RagContextProjector.META_RAG_CITATIONS);
        Assert.assertNotNull(citations);
        Assert.assertEquals(1, citations.size());
    }

    @Test
    public void supportsUserMessageInjectionAndTruncationFromRagContext() {
        RagContext context = RagContext.builder()
                .text("0123456789ABCDEF")
                .citations(Arrays.asList(
                        RagCitation.builder().citationId("C1").sourceName("guide.md").snippet("snippet 1").build()))
                .build();

        RagContextProjector projector = RagContextProjector.builder()
                .injectAsSystemMessage(false)
                .maxContextChars(10)
                .build();

        ModelRequest request = ModelRequest.builder()
                .model("gpt-4o-mini")
                .message(Message.user().text("Question").build())
                .build();
        ModelRequest projected = projector.project(request, "Question", context);

        Assert.assertEquals(2, projected.getItems().size());
        Message contextMsg = (Message) projected.getItems().get(0);
        Assert.assertEquals(MessageRole.USER, contextMsg.getRole());
        Assert.assertTrue(contextMsg.textContent().contains("0123456789"));
        Assert.assertFalse(contextMsg.textContent().contains("ABCDEF"));
    }

    @Test
    public void returnsOriginalRequestWhenResultHasNoContextOrCitations() {
        ModelRequest request = ModelRequest.builder()
                .model("gpt-4o-mini")
                .message(Message.user().text("Hello").build())
                .build();
        RagContextProjector projector = new RagContextProjector();
        Assert.assertSame(request, projector.project(request, RagResult.builder().query("Hello").build()));
        Assert.assertEquals("Hello", RagContextProjector.extractLatestUserQuery(request));
    }
}
