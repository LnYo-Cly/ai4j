package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.Endpoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class ModelClientRagJudgeTest {

    @Test
    public void evaluatesAndClampsScoresFromLenientJson() throws Exception {
        ModelClientRagQueryPlannerTest.RecordingModelClient client =
                new ModelClientRagQueryPlannerTest.RecordingModelClient(
                        Endpoint.builder(Protocol.CHAT_COMPLETIONS).baseUrl("https://api.example.com").build(),
                        "Here is the evaluation:\n```json\n"
                                + "{\"faithfulnessScore\": 1.25, \"contextRelevanceScore\": -0.2, "
                                + "\"answerRelevanceScore\": \"0.85\", \"reason\": \"Grounded in hit 1\"}\n```");

        ModelClientRagJudge judge = ModelClientRagJudge.builder()
                .modelClient(client)
                .model("judge-model")
                .maxContextChars(300)
                .build();

        RagJudgeEvaluation evaluation = judge.judge(RagJudgeRequest.builder()
                .query("What targets Java 8?")
                .hits(Arrays.asList(
                        RagHit.builder().sourceName("README.md").content("ai4j targets Java 8 bytecode.").build(),
                        RagHit.builder().sourceName("pom.xml").content("animal-sniffer enforces java18.").build()))
                .answer("ai4j targets Java 8.")
                .build());

        Assert.assertEquals(1.0, evaluation.getFaithfulnessScore().doubleValue(), 0.0001);
        Assert.assertEquals(0.0, evaluation.getContextRelevanceScore().doubleValue(), 0.0001);
        Assert.assertEquals(0.85, evaluation.getAnswerRelevanceScore().doubleValue(), 0.0001);
        Assert.assertEquals("Grounded in hit 1", evaluation.getReason());
        Assert.assertNotNull(evaluation.getRawOutput());
        Assert.assertEquals("judge-model", client.lastRequest().getModel());
        Assert.assertNotNull(client.lastRequest().getProviderOptions().get("response_format"));
        String userPrompt = ((Message) client.lastRequest().getItems().get(1)).textContent();
        Assert.assertTrue(userPrompt.contains("README.md"));
        Assert.assertTrue(userPrompt.contains("ai4j targets Java 8."));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankAnswer() throws Exception {
        ModelClientRagJudge judge = new ModelClientRagJudge(
                new ModelClientRagQueryPlannerTest.RecordingModelClient(
                        Endpoint.builder(Protocol.RESPONSES).baseUrl("https://api.example.com").build(),
                        "{}"),
                "judge-model");
        judge.judge(RagJudgeRequest.builder().query("q").answer("   ").build());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnparseableOutput() throws Exception {
        ModelClientRagJudge judge = new ModelClientRagJudge(
                new ModelClientRagQueryPlannerTest.RecordingModelClient(
                        Endpoint.builder(Protocol.RESPONSES).baseUrl("https://api.example.com").build(),
                        "plain text without json"),
                "judge-model");
        judge.judge(RagJudgeRequest.builder().query("q").context("c").answer("a").build());
    }
}
