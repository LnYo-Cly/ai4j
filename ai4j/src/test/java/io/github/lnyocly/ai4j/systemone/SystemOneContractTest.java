package io.github.lnyocly.ai4j.systemone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.systemone.entity.ChoiceQuestion;
import io.github.lnyocly.ai4j.systemone.entity.NoulCriteria;
import io.github.lnyocly.ai4j.systemone.entity.NoulQuestion;
import io.github.lnyocly.ai4j.systemone.entity.ScoreQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class SystemOneContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void shouldSerializeAllQuestionTypes() throws Exception {
        Map<String, SystemOneQuestion> questions = new LinkedHashMap<String, SystemOneQuestion>();

        Map<String, Object> choiceCriteria = new LinkedHashMap<String, Object>();
        choiceCriteria.put("billing", "Invoices and payments");
        choiceCriteria.put("support", "Product usage questions");
        questions.put("route", ChoiceQuestion.of("Pick a branch", choiceCriteria));

        questions.put("severity", ScoreQuestion.of("Rate severity",
                Arrays.<Object>asList("trivial", "minor", "critical")));

        questions.put("has_pii", NoulQuestion.of("Contains PII?",
                new NoulCriteria("content includes PII", "no PII present")));

        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("text", "my invoice is wrong");
        SystemOneRequest request = SystemOneRequest.of(state, "jev-latest", questions);

        JsonNode root = mapper.readTree(mapper.writeValueAsString(request));

        Assert.assertEquals("jev-latest", root.get("model").asText());
        Assert.assertEquals("my invoice is wrong", root.get("state").get("text").asText());

        JsonNode route = root.get("questions").get("route");
        Assert.assertEquals("choice", route.get("type").asText());
        Assert.assertEquals("Pick a branch", route.get("instructions").asText());
        Assert.assertEquals("Invoices and payments", route.get("criteria").get("billing").asText());

        JsonNode severity = root.get("questions").get("severity");
        Assert.assertEquals("score", severity.get("type").asText());
        Assert.assertEquals(3, severity.get("criteria").size());
        Assert.assertEquals("critical", severity.get("criteria").get(2).asText());

        JsonNode pii = root.get("questions").get("has_pii");
        Assert.assertEquals("noul", pii.get("type").asText());
        Assert.assertEquals("content includes PII", pii.get("criteria").get("true").asText());
        Assert.assertEquals("no PII present", pii.get("criteria").get("false").asText());
    }

    @Test
    public void shouldDeserializeTypedAnswers() throws Exception {
        String json = "{"
                + "\"model\":\"jev-2026-09\","
                + "\"answers\":{"
                + "  \"route\":{\"type\":\"choice\",\"choice\":\"billing\","
                + "    \"probabilities\":{\"billing\":0.91,\"support\":0.09},\"confidence\":0.91},"
                + "  \"severity\":{\"type\":\"score\",\"score\":2,"
                + "    \"probabilities\":{\"0\":0.05,\"1\":0.15,\"2\":0.8},\"confidence\":0.8,"
                + "    \"legend\":{\"0\":\"trivial\",\"1\":\"minor\",\"2\":\"critical\"}},"
                + "  \"has_pii\":{\"type\":\"noul\",\"noul\":0.02}"
                + "},"
                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":30}"
                + "}";

        SystemOneResponse response = mapper.readValue(json, SystemOneResponse.class);

        Assert.assertEquals("jev-2026-09", response.getModel());
        Assert.assertEquals(Long.valueOf(120), response.getUsage().getInputTokens());

        SystemOneAnswer route = response.getAnswers().get("route");
        Assert.assertEquals("choice", route.getType());
        Assert.assertEquals("billing", route.getChoice());
        Assert.assertEquals(0.91, route.getConfidence(), 1e-6);
        Assert.assertEquals(0.09, route.getProbabilities().get("support"), 1e-6);

        SystemOneAnswer severity = response.getAnswers().get("severity");
        Assert.assertEquals(2.0, severity.getScore(), 1e-6);
        Assert.assertEquals("critical", severity.getLegend().get("2"));

        SystemOneAnswer pii = response.getAnswers().get("has_pii");
        Assert.assertEquals(0.02, pii.getNoul(), 1e-6);

        Assert.assertEquals(1, response.choices().size());
        Assert.assertEquals(1, response.scores().size());
        Assert.assertEquals(1, response.nouls().size());

        Assert.assertEquals("billing", response.choice("route"));
        Assert.assertEquals(2.0, response.score("severity"), 1e-6);
        Assert.assertEquals(0.02, response.noul("has_pii"), 1e-6);
        Assert.assertEquals(0.91, response.confidence("route"), 1e-6);
        Assert.assertNull(response.choice("missing"));
        Assert.assertNull(response.noul(null));
    }

    @Test
    public void shouldBuildIdenticalJsonFromBuilderAndMap() throws Exception {
        Map<String, Object> choiceCriteria = new LinkedHashMap<String, Object>();
        choiceCriteria.put("billing", "Invoices and payments");
        choiceCriteria.put("support", "Product usage questions");

        Map<String, SystemOneQuestion> questions = new LinkedHashMap<String, SystemOneQuestion>();
        questions.put("route", ChoiceQuestion.of("Pick a branch", choiceCriteria));
        questions.put("severity", ScoreQuestion.of("Rate severity",
                Arrays.<Object>asList("trivial", "minor", "critical")));
        questions.put("has_pii", NoulQuestion.of("Contains PII?",
                new NoulCriteria("content includes PII", "no PII present")));

        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("text", "my invoice is wrong");

        SystemOneRequest viaMap = SystemOneRequest.of(state, "jev-latest", questions);
        SystemOneRequest viaBuilder = SystemOneRequest.builder()
                .state(state)
                .model("jev-latest")
                .choice("route", "Pick a branch", choiceCriteria)
                .score("severity", "Rate severity",
                        Arrays.<Object>asList("trivial", "minor", "critical"))
                .noul("has_pii", "Contains PII?",
                        new NoulCriteria("content includes PII", "no PII present"))
                .build();

        Assert.assertEquals(mapper.writeValueAsString(viaMap), mapper.writeValueAsString(viaBuilder));

        SystemOneRequest noulOnly = SystemOneRequest.builder()
                .state("plain text state")
                .noul("is_spam", "Is this spam?")
                .build();
        JsonNode root = mapper.readTree(mapper.writeValueAsString(noulOnly));
        Assert.assertEquals("plain text state", root.get("state").asText());
        Assert.assertEquals("noul", root.get("questions").get("is_spam").get("type").asText());
        Assert.assertFalse(root.get("questions").get("is_spam").has("criteria"));
    }

    @Test
    public void shouldTolerateUnknownAnswerFields() throws Exception {
        String json = "{\"answers\":{\"q\":{\"type\":\"choice\",\"choice\":\"a\",\"future_field\":[1,2]}}}";
        SystemOneResponse response = mapper.readValue(json, SystemOneResponse.class);
        Assert.assertEquals("a", response.getAnswers().get("q").getChoice());
    }
}
