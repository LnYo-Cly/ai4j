package io.github.lnyocly;

import io.github.lnyocly.ai4j.config.TypeSafeConfig;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.ISystemOneService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.systemone.entity.ChoiceQuestion;
import io.github.lnyocly.ai4j.systemone.entity.NoulQuestion;
import io.github.lnyocly.ai4j.systemone.entity.ScoreQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneModelCard;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneResponse;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TypeSafe System One (Jev) live test.
 *
 * <p>Configuration via environment variables:
 * <ul>
 *   <li>{@code TYPESAFE_API_KEY} — required, skipped when absent</li>
 *   <li>{@code TYPESAFE_BASE_URL} — optional, defaults to {@code https://api.typesafe.ai/}</li>
 *   <li>{@code TYPESAFE_DEFAULT_MODEL} — optional, defaults to {@code jev-latest}</li>
 * </ul>
 */
@Category(LiveProviderTest.class)
public class TypeSafeSystemOneLiveTest {

    private ISystemOneService systemOneService;
    private String model;

    @Before
    public void init() {
        String apiKey = LiveProviderTestSupport.requireEnv(
                "Skip because TYPESAFE_API_KEY is not configured", "TYPESAFE_API_KEY");

        TypeSafeConfig config = new TypeSafeConfig();
        config.setApiKey(apiKey);
        String baseUrl = System.getenv("TYPESAFE_BASE_URL");
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            config.setApiHost(baseUrl);
        }
        this.model = System.getenv("TYPESAFE_DEFAULT_MODEL");
        if (model == null || model.trim().isEmpty()) {
            this.model = "jev-latest";
        }

        Configuration configuration = new Configuration();
        configuration.setTypeSafeConfig(config);
        systemOneService = new AiService(configuration).getSystemOneService(PlatformType.TYPESAFE);
    }

    @Test
    public void test_evaluate_choiceScoreNoul() throws Exception {
        Map<String, SystemOneQuestion> questions = new LinkedHashMap<String, SystemOneQuestion>();

        Map<String, Object> routeCriteria = new LinkedHashMap<String, Object>();
        routeCriteria.put("billing", "Invoices, refunds, subscription payments");
        routeCriteria.put("support", "Product usage, bugs, how-to questions");
        routeCriteria.put("sales", "Pricing, upgrades, new purchases");
        questions.put("route", ChoiceQuestion.of("Which team should handle this?", routeCriteria));

        questions.put("urgency", ScoreQuestion.of("How urgent is the request?",
                Arrays.<Object>asList("low", "normal", "high", "critical")));

        questions.put("contains_pii", NoulQuestion.of(
                "Does the message contain personally identifiable information?"));

        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("message", "I was charged twice on my credit card ending 4242, please refund ASAP");
        state.put("channel", "email");

        SystemOneResponse response = systemOneService.evaluate(
                SystemOneRequest.of(state, model, questions));

        System.out.println("=== TYPESAFE systemone: " + model + " ===");
        System.out.println(response);

        assertNotNull(response);
        assertNotNull(response.getAnswers());

        SystemOneAnswer route = response.getAnswers().get("route");
        assertNotNull(route);
        assertNotNull(route.getChoice());
        assertTrue(routeCriteria.containsKey(route.getChoice()));

        SystemOneAnswer urgency = response.getAnswers().get("urgency");
        assertNotNull(urgency);

        SystemOneAnswer pii = response.getAnswers().get("contains_pii");
        assertNotNull(pii);
    }

    @Test
    public void test_listModels() throws Exception {
        List<SystemOneModelCard> models = systemOneService.listModels();
        System.out.println("=== TYPESAFE models ===");
        System.out.println(models);
        assertNotNull(models);
    }
}
