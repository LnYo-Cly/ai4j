package io.github.lnyocly.ai4j.agent.session;

/**
 * Result of {@link HashChainedEventLog#verifyChain()}.
 *
 * <p>{@code valid} is true iff every link's stored hash matches a recomputation from the genesis
 * hash. {@code firstBrokenIndex} is the 0-based position of the first tampered/reordered link, or
 * -1 when the chain is intact.</p>
 */
public final class ChainVerification {

    public static final ChainVerification INTACT = new ChainVerification(true, -1);

    private final boolean valid;
    private final int firstBrokenIndex;

    ChainVerification(boolean valid, int firstBrokenIndex) {
        this.valid = valid;
        this.firstBrokenIndex = firstBrokenIndex;
    }

    public boolean isValid() {
        return valid;
    }

    /** First broken link index (0-based), or -1 if the chain is intact. */
    public int getFirstBrokenIndex() {
        return firstBrokenIndex;
    }
}
