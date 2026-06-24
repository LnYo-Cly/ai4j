package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link HashChainedEventLog}: a fresh chain verifies intact; tampering an event (or its
 * order) is detected at the first broken link; restore reseals deterministically.
 */
public class HashChainedEventLogTest {

    private static AgentEvent ev(String id) {
        return AgentEvent.builder()
                .eventId(id)
                .runId("run-1")
                .sessionId("sess-1")
                .turnId("turn-1")
                .type(AgentEventType.TOOL_CALL)
                .step(1)
                .message("m-" + id)
                .build();
    }

    @Test
    public void freshChainVerifiesIntact() {
        HashChainedEventLog log = new HashChainedEventLog();
        log.append(ev("1"));
        log.append(ev("2"));
        log.append(ev("3"));

        ChainVerification v = log.verifyChain();
        assertTrue("untampered chain must verify", v.isValid());
        assertEquals(-1, v.getFirstBrokenIndex());
        assertEquals(3, log.getChainHashes().size());
        // link hashes are distinct and non-trivial
        assertNotEquals(log.getChainHashes().get(0), log.getChainHashes().get(1));
        assertEquals(64, log.getChainHashes().get(0).length());
    }

    @Test
    public void tamperingAnEventIsDetectedAtThatIndex() {
        HashChainedEventLog log = new HashChainedEventLog();
        log.append(ev("1"));
        log.append(ev("2"));
        log.append(ev("3"));
        // simulate an after-the-fact edit of link 1 without resealing
        log.tamperEvent(1, ev("TAMPERED"));

        ChainVerification v = log.verifyChain();
        assertFalse("tampered chain must not verify", v.isValid());
        assertEquals("first broken link must be the tampered one", 1, v.getFirstBrokenIndex());
    }

    @Test
    public void restoreRebuildsChainAndVerifiesIntact() {
        HashChainedEventLog log1 = new HashChainedEventLog();
        log1.append(ev("1"));
        log1.append(ev("2"));
        List<AgentSessionEvent> events = log1.getEvents();

        HashChainedEventLog log2 = new HashChainedEventLog();
        log2.restore(events);
        assertTrue("restored chain reseals deterministically", log2.verifyChain().isValid());
        // determinism: same events -> same chain hashes
        assertEquals(log1.getChainHashes(), log2.getChainHashes());
    }

    @Test
    public void appendingAsAgentListenerWorks() {
        HashChainedEventLog log = new HashChainedEventLog();
        log.onEvent(ev("1")); // via AgentListener surface
        log.onEvent(ev("2"));
        assertTrue(log.verifyChain().isValid());
        assertEquals(2, log.getEvents().size());
    }

    @Test
    public void clearResetsTheChain() {
        HashChainedEventLog log = new HashChainedEventLog();
        log.append(ev("1"));
        log.clear();
        assertEquals(0, log.getEvents().size());
        assertTrue(log.verifyChain().isValid());
    }
}
