package io.github.lnyocly.ai4j.agent.session;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tamper-evident, hash-chained {@link AgentSessionEventLog}. Each appended event is sealed into a
 * link: {@code hash = sha256(prevHash || "|" || canonical(event))}, where {@code prevHash} is the
 * previous link's hash (genesis for the first). {@link #verifyChain()} recomputes the chain from
 * genesis and reports the first link whose stored hash no longer matches — so any after-the-fact
 * edit, deletion, or reordering of a logged event is detectable.
 *
 * <p>Drop-in replacement for {@link InMemoryAgentSessionEventLog}: implements the same
 * {@link AgentSessionEventLog} + {@link AgentListener} surface, so it can be registered wherever the
 * in-memory log is used, and the chain makes the recorded tool/model/sandbox events auditable.</p>
 */
public class HashChainedEventLog implements AgentSessionEventLog, AgentListener {

    private static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    static final class Link {
        final AgentSessionEvent event;
        final String prevHash;
        final String hash;

        Link(AgentSessionEvent event, String prevHash, String hash) {
            this.event = event;
            this.prevHash = prevHash;
            this.hash = hash;
        }
    }

    private final List<Link> links = new ArrayList<Link>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized void append(AgentEvent event) {
        if (event == null) {
            return;
        }
        long seq = sequence.incrementAndGet();
        AgentSessionEvent wrapped = new AgentSessionEvent(seq, System.currentTimeMillis(), event);
        String prevHash = links.isEmpty() ? GENESIS_HASH : links.get(links.size() - 1).hash;
        String hash = computeHash(prevHash, event);
        links.add(new Link(wrapped, prevHash, hash));
    }

    @Override
    public void onEvent(AgentEvent event) {
        append(event);
    }

    @Override
    public synchronized List<AgentSessionEvent> getEvents() {
        List<AgentSessionEvent> copy = new ArrayList<AgentSessionEvent>(links.size());
        for (Link link : links) {
            if (link != null && link.event != null) {
                copy.add(link.event.copy());
            }
        }
        return copy;
    }

    /** Returns the stored hash of each link, in order (the chain itself). */
    public synchronized List<String> getChainHashes() {
        List<String> hashes = new ArrayList<String>(links.size());
        for (Link link : links) {
            hashes.add(link.hash);
        }
        return hashes;
    }

    /**
     * Recomputes the chain from genesis and compares to the stored hashes.
     */
    public synchronized ChainVerification verifyChain() {
        String prevHash = GENESIS_HASH;
        for (int i = 0; i < links.size(); i++) {
            Link link = links.get(i);
            String expected = computeHash(prevHash, link.event.getEvent());
            if (!expected.equals(link.hash) || !prevHash.equals(link.prevHash)) {
                return new ChainVerification(false, i);
            }
            prevHash = link.hash;
        }
        return ChainVerification.INTACT;
    }

    /**
     * Replaces the event payload at {@code index} WITHOUT resealing the link, simulating an
     * after-the-fact edit. The next {@link #verifyChain()} must report this index as broken.
     */
    synchronized void tamperEvent(int index, AgentEvent replacement) {
        if (index < 0 || index >= links.size()) {
            throw new IndexOutOfBoundsException("link index " + index);
        }
        Link existing = links.get(index);
        AgentSessionEvent tampered = new AgentSessionEvent(
                existing.event.getSequence(), existing.event.getRecordedAtEpochMs(), replacement);
        links.set(index, new Link(tampered, existing.prevHash, existing.hash));
    }

    @Override
    public synchronized void restore(List<AgentSessionEvent> restoredEvents) {
        links.clear();
        sequence.set(0);
        if (restoredEvents == null) {
            return;
        }
        for (AgentSessionEvent event : restoredEvents) {
            if (event == null) {
                continue;
            }
            AgentSessionEvent copy = event.copy();
            String prevHash = links.isEmpty() ? GENESIS_HASH : links.get(links.size() - 1).hash;
            String hash = computeHash(prevHash, copy.getEvent());
            links.add(new Link(copy, prevHash, hash));
            if (copy.getSequence() > sequence.get()) {
                sequence.set(copy.getSequence());
            }
        }
    }

    @Override
    public synchronized void clear() {
        links.clear();
        sequence.set(0);
    }

    /** hash = sha256(prevHash || "|" || canonical(event)) as lowercase hex. */
    static String computeHash(String prevHash, AgentEvent event) {
        String canonical = JSON.toJSONString(event);
        String input = prevHash + "|" + canonical;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
