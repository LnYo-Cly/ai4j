package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.systemone.SystemOneGuardrail;
import io.github.lnyocly.ai4j.agent.systemone.SystemOneRouter;
import io.github.lnyocly.ai4j.agent.workflow.AgentNode;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowContext;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneAnswer;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneQuestion;
import io.github.lnyocly.ai4j.systemone.entity.SystemOneRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class SystemOneAgentTest {

    private static SystemOneAnswer choice(String choice, double confidence) {
        SystemOneAnswer answer = new SystemOneAnswer();
        answer.setType("choice");
        answer.setChoice(choice);
        answer.setConfidence(confidence);
        return answer;
    }

    private static SystemOneAnswer noul(double probability) {
        SystemOneAnswer answer = new SystemOneAnswer();
        answer.setType("noul");
        answer.setNoul(probability);
        return answer;
    }

    private static WorkflowContext contextWithState() {
        Map<String, Object> state = new LinkedHashMap<String, Object>();
        state.put("text", "where is my invoice?");
        return WorkflowContext.builder().state(state).build();
    }

    @Test
    public void routerReturnsWinningChoiceWhenConfident() {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("route", choice("billing", 0.93));

        SystemOneRouter router = new SystemOneRouter(jev)
                .route("billing").route("support")
                .minConfidence(0.7)
                .fallbackRoute("generic");

        String route = router.route(contextWithState(), AgentRequest.builder().build(), null);
        Assert.assertEquals("billing", route);
    }

    @Test
    public void routerFallsBackBelowMinConfidence() {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("route", choice("billing", 0.4));

        SystemOneRouter router = new SystemOneRouter(jev)
                .route("billing").route("support")
                .minConfidence(0.7)
                .fallbackRoute("generic");

        String route = router.route(contextWithState(), AgentRequest.builder().build(), null);
        Assert.assertEquals("generic", route);
    }

    @Test
    public void routerFallsBackWhenAnswerMissing() {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("other", choice("billing", 0.99));

        SystemOneRouter router = new SystemOneRouter(jev)
                .route("billing").route("support")
                .fallbackRoute("generic");

        String route = router.route(contextWithState(), AgentRequest.builder().build(), null);
        Assert.assertEquals("generic", route);
    }

    @Test
    public void routerSendsChoiceQuestionWithCriteria() {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("route", choice("billing", 0.9));

        new SystemOneRouter(jev)
                .instructions("Pick a branch")
                .route("billing", "Invoices and payments")
                .route("support", "Usage questions")
                .route(contextWithState(), AgentRequest.builder().build(), null);

        Assert.assertEquals(1, jev.getRequests().size());
        SystemOneRequest sent = jev.getRequests().get(0);
        SystemOneQuestion question = sent.getQuestions().get("route");
        Assert.assertEquals("choice", question.getType());
        Assert.assertEquals("Pick a branch", question.getInstructions());
        Map<String, Object> state = (Map<String, Object>) sent.getState();
        Assert.assertEquals("where is my invoice?", state.get("text"));
    }

    @Test
    public void guardrailBlocksAtThreshold() throws Exception {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("guardrail", noul(0.9))
                .enqueueAnswer("guardrail", noul(0.9));

        SystemOneGuardrail guardrail = new SystemOneGuardrail(jev)
                .instructions("Contains PII?")
                .threshold(0.8);

        Assert.assertTrue(guardrail.isViolation(contextWithState().getState()));
        Assert.assertFalse(guardrail.matches(contextWithState(), AgentRequest.builder().build(), null));
    }

    @Test
    public void guardrailAllowsBelowThreshold() throws Exception {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("guardrail", noul(0.3));

        SystemOneGuardrail guardrail = new SystemOneGuardrail(jev)
                .instructions("Contains PII?")
                .threshold(0.8);

        Assert.assertTrue(guardrail.matches(contextWithState(), AgentRequest.builder().build(), null));
    }

    @Test
    public void guardrailNodeReturnsViolationResult() throws Exception {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("guardrail", noul(0.95));

        AgentResult blocked = AgentResult.builder().outputText("blocked").build();
        AgentNode node = new SystemOneGuardrail(jev).threshold(0.5).asNode(blocked);

        AgentResult result = node.execute(contextWithState(), AgentRequest.builder().input("hi").build());
        Assert.assertEquals("blocked", result.getOutputText());
    }

    @Test
    public void guardrailNodePassesThroughInput() throws Exception {
        ScriptedSystemOneService jev = new ScriptedSystemOneService()
                .enqueueAnswer("guardrail", noul(0.1));

        AgentResult blocked = AgentResult.builder().outputText("blocked").build();
        AgentNode node = new SystemOneGuardrail(jev).threshold(0.5).asNode(blocked);

        AgentResult result = node.execute(contextWithState(), AgentRequest.builder().input("hello").build());
        Assert.assertEquals("hello", result.getOutputText());
    }

    @Test(expected = IllegalStateException.class)
    public void scriptedServiceFailsWhenExhausted() throws Exception {
        new ScriptedSystemOneService().failWhenExhausted(true).evaluate(new SystemOneRequest());
    }
}
