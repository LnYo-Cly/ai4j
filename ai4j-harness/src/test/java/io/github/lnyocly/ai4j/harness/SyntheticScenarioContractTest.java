package io.github.lnyocly.ai4j.harness;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises {@link ArtifactContractGate} as the internal oracle for business
 * domains that have no external benchmark: each synthetic scenario declares a
 * contract the way a domain author would, then checks a hand-written correct
 * artifact passes and a deliberately contaminated artifact fails.
 */
public class SyntheticScenarioContractTest {

    private static final String BASE = "/scenarios/";

    private static final ArtifactReader CLASSPATH_READER = new ArtifactReader() {
        @Override
        public String read(String location) {
            InputStream in = SyntheticScenarioContractTest.class
                    .getResourceAsStream(BASE + location);
            if (in == null) {
                return null;
            }
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                in.close();
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException error) {
                return null;
            }
        }
    };

    private static GateResult evaluate(ArtifactContract contract) {
        return new ArtifactContractGate("fidelity", contract, CLASSPATH_READER)
                .evaluate(null, null, null);
    }

    @Test
    public void s1HandoverPreservesTicketIdsAndVocabulary() {
        ArtifactContract contract = ArtifactContract.builder()
                .source("tickets", "s1-customer-service-handoff/sources/tickets_export.csv")
                .artifact(ArtifactContract.ArtifactSpec.builder("handover",
                                "s1-customer-service-handoff/expected/handover.csv")
                        .requiredFields("ticket_id", "action", "approver_role", "reason")
                        .allowedFields("ticket_id", "action", "approver_role", "reason")
                        .identifierField("ticket_id", "tickets")
                        .coverageField("ticket_id", "tickets", "ticket_id")
                        .controlledVocabulary("action", "escalate", "monitor", "close")
                        .controlledVocabulary("approver_role", "shift_lead",
                                "vip_success_manager", "billing_ops_lead")
                        .build())
                .build();
        assertTrue(evaluate(contract).isPassed());
    }

    @Test
    public void s1ContaminatedHandoverFailsOnInventedIds() {
        ArtifactContract contract = ArtifactContract.builder()
                .source("tickets", "s1-customer-service-handoff/sources/tickets_export.csv")
                .artifact(ArtifactContract.ArtifactSpec.builder("handover",
                                "s1-customer-service-handoff/contaminated/handover.csv")
                        .requiredFields("ticket_id", "action", "approver_role", "reason")
                        .allowedFields("ticket_id", "action", "approver_role", "reason")
                        .identifierField("ticket_id", "tickets")
                        .coverageField("ticket_id", "tickets", "ticket_id")
                        .controlledVocabulary("action", "escalate", "monitor", "close")
                        .controlledVocabulary("approver_role", "shift_lead",
                                "vip_success_manager", "billing_ops_lead")
                        .build())
                .build();
        GateResult result = evaluate(contract);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("missing required field 'ticket_id'")
                || result.getReason().contains("not found verbatim"));
    }

    @Test
    public void s2ResumeLedgerHonorsStatusVocabulary() {
        ArtifactContract contract = ArtifactContract.builder()
                .source("manifest", "s2-fulfillment-batch-resume/sources/batch_manifest.csv")
                .artifact(ArtifactContract.ArtifactSpec.builder("resume_ledger",
                                "s2-fulfillment-batch-resume/expected/resume_ledger.csv")
                        .requiredFields("order_id", "ledger_status", "note")
                        .allowedFields("order_id", "ledger_status", "note")
                        .identifierField("order_id", "manifest")
                        .coverageField("order_id", "manifest", "order_id")
                        .controlledVocabulary("ledger_status", "skipped_completed",
                                "retried", "processed_new", "rejected_unrecoverable")
                        .build())
                .build();
        assertTrue(evaluate(contract).isPassed());
    }

    @Test
    public void s2ContaminatedLedgerFailsOnShadowStatusColumn() {
        ArtifactContract contract = ArtifactContract.builder()
                .source("manifest", "s2-fulfillment-batch-resume/sources/batch_manifest.csv")
                .artifact(ArtifactContract.ArtifactSpec.builder("resume_ledger",
                                "s2-fulfillment-batch-resume/contaminated/resume_ledger.csv")
                        .requiredFields("order_id", "ledger_status", "note")
                        .allowedFields("order_id", "ledger_status", "note")
                        .identifierField("order_id", "manifest")
                        .coverageField("order_id", "manifest", "order_id")
                        .controlledVocabulary("ledger_status", "skipped_completed",
                                "retried", "processed_new", "rejected_unrecoverable")
                        .build())
                .build();
        GateResult result = evaluate(contract);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("undeclared field 'status'")
                && result.getReason().contains("missing required field 'ledger_status'"));
    }

    @Test
    public void s3RegisterRetainsSupersededRows() {
        ArtifactContract contract = ArtifactContract.builder()
                .source("register", "s3-governance-decision-register/sources/decision_register.csv")
                .artifact(ArtifactContract.ArtifactSpec.builder("updated_register",
                                "s3-governance-decision-register/expected/updated_register.csv")
                        .requiredFields("decision_id", "title", "status", "conflict_ref")
                        .allowedFields("decision_id", "title", "status", "conflict_ref")
                        .identifierField("decision_id", "register")
                        .coverageField("decision_id", "register", "decision_id")
                        .controlledVocabulary("status", "active", "superseded", "withdrawn")
                        .build())
                .build();
        assertTrue(evaluate(contract).isPassed());
    }

    @Test
    public void s3ContaminatedRegisterFailsOnDroppedRow() {
        ArtifactContract contract = ArtifactContract.builder()
                .source("register", "s3-governance-decision-register/sources/decision_register.csv")
                .artifact(ArtifactContract.ArtifactSpec.builder("updated_register",
                                "s3-governance-decision-register/contaminated/updated_register.csv")
                        .requiredFields("decision_id", "title", "status", "conflict_ref")
                        .allowedFields("decision_id", "title", "status", "conflict_ref")
                        .identifierField("decision_id", "register")
                        .coverageField("decision_id", "register", "decision_id")
                        .controlledVocabulary("status", "active", "superseded", "withdrawn")
                        .build())
                .build();
        GateResult result = evaluate(contract);
        assertFalse(result.isPassed());
        assertTrue(result.getReason().contains("missing 'DEC-2024-032'"));
    }
}
