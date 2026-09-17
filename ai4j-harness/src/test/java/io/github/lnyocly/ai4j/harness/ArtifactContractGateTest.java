package io.github.lnyocly.ai4j.harness;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArtifactContractGateTest {

    private static final String TICKETS_CSV =
            "ticket_id,tier,status\n"
            + "TK-4401,vip,open\n"
            + "TK-4402,standard,open\n";

    private static final String HANDOVER_CSV =
            "ticket_id,action,reason\n"
            + "TK-4401,escalate,vip unresolved over sla\n"
            + "TK-4402,hold,waiting on customer\n";

    private ArtifactContract contract() {
        return ArtifactContract.builder()
                .source("tickets", "in/tickets.csv")
                .artifact(ArtifactContract.ArtifactSpec.builder("handover", "out/handover.csv")
                        .requiredFields("ticket_id", "action", "reason")
                        .allowedFields("ticket_id", "action", "attempt_count", "reason", "owner")
                        .identifierField("ticket_id", "tickets")
                        .controlledVocabulary("action", "escalate", "hold", "resolve", "reject")
                        .build())
                .build();
    }

    private ArtifactContractGate gate(Map<String, String> files) {
        return new ArtifactContractGate("fidelity", contract(), new ArtifactReader() {
            @Override
            public String read(String location) {
                return files.get(location);
            }
        });
    }

    private Map<String, String> files(String handover) {
        Map<String, String> files = new HashMap<String, String>();
        files.put("in/tickets.csv", TICKETS_CSV);
        if (handover != null) {
            files.put("out/handover.csv", handover);
        }
        return files;
    }

    @Test
    public void passesWhenArtifactHonorsContract() {
        assertTrue(gate(files(HANDOVER_CSV))
                .evaluate(null, null, null).isPassed());
    }

    @Test
    public void passesWithEmptyContract() {
        ArtifactContractGate gate = new ArtifactContractGate("fidelity",
                ArtifactContract.builder().build(), new ArtifactReader() {
            @Override
            public String read(String location) {
                return null;
            }
        });
        assertTrue(gate.evaluate(null, null, null).isPassed());
    }

    @Test
    public void failsWhenArtifactMissing() {
        GateResult result = gate(files(null)).evaluate(null, null, null);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("not found"));
    }

    @Test
    public void failsWhenRequiredFieldMissing() {
        String artifact = "ticket_id,action\nTK-4401,escalate\n";
        GateResult result = gate(files(artifact)).evaluate(null, null, null);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("missing required field 'reason'"));
    }

    @Test
    public void failsWhenUndeclaredFieldShadows() {
        String artifact = "ticket_id,status,action,reason\n"
                + "TK-4401,open,escalate,vip\n";
        GateResult result = gate(files(artifact)).evaluate(null, null, null);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("undeclared field 'status'"));
    }

    @Test
    public void failsWhenIdentifierInvented() {
        String artifact = "ticket_id,action,reason\n"
                + "ESC-001,escalate,vip\n";
        GateResult result = gate(files(artifact)).evaluate(null, null, null);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("not found verbatim"));
    }

    @Test
    public void failsWhenVocabularyViolated() {
        String artifact = "ticket_id,action,reason\n"
                + "TK-4401,refunded,vip\n";
        GateResult result = gate(files(artifact)).evaluate(null, null, null);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("outside declared vocabulary"));
    }

    @Test
    public void failsWhenSourceRowsDropped() {
        Map<String, String> files = new HashMap<String, String>();
        files.put("in/register.csv",
                "decision_id,status\nDEC-01,active\nDEC-02,superseded\n");
        files.put("out/register.csv",
                "decision_id,status\nDEC-01,active\n");
        ArtifactContractGate gate = new ArtifactContractGate("fidelity",
                ArtifactContract.builder()
                        .source("register", "in/register.csv")
                        .artifact(ArtifactContract.ArtifactSpec.builder("register", "out/register.csv")
                                .requiredFields("decision_id", "status")
                                .coverageField("decision_id", "register", "decision_id")
                                .build())
                        .build(),
                new ArtifactReader() {
                    @Override
                    public String read(String location) {
                        return files.get(location);
                    }
                });
        GateResult result = gate.evaluate(null, null, null);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("missing 'DEC-02'"));
    }

    @Test
    public void parsesJsonArtifacts() {
        Map<String, String> files = new HashMap<String, String>();
        files.put("in/tickets.csv", TICKETS_CSV);
        files.put("out/handover.json",
                "[{\"ticket_id\":\"TK-4401\",\"action\":\"escalate\",\"reason\":\"vip\"}]");
        ArtifactContractGate gate = new ArtifactContractGate("fidelity",
                ArtifactContract.builder()
                        .source("tickets", "in/tickets.csv")
                        .artifact(ArtifactContract.ArtifactSpec.builder("handover", "out/handover.json")
                                .requiredFields("ticket_id", "action")
                                .identifierField("ticket_id", "tickets")
                                .controlledVocabulary("action", "escalate", "hold")
                                .build())
                        .build(),
                new ArtifactReader() {
                    @Override
                    public String read(String location) {
                        return files.get(location);
                    }
                });
        assertTrue(gate.evaluate(null, null, null).isPassed());
    }
}
