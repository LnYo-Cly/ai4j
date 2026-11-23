package io.github.lnyocly.ai4j.ambient.memory;

/**
 * Strict trust hierarchy for ambient memory provenance.
 *
 * <p>{@link #OWNER_DIRECT} &gt; {@link #VERIFIED_TOOL} &gt; {@link #EXTERNAL_UNTRUSTED}.</p>
 */
public enum TrustLevel {

    EXTERNAL_UNTRUSTED(1),
    VERIFIED_TOOL(2),
    OWNER_DIRECT(3);

    private final int rank;

    TrustLevel(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean outranks(TrustLevel other) {
        if (other == null) {
            return true;
        }
        return this.rank > other.rank;
    }

    public boolean atLeast(TrustLevel minimum) {
        if (minimum == null) {
            return true;
        }
        return this.rank >= minimum.rank;
    }
}
