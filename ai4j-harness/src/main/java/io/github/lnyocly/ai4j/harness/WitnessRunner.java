package io.github.lnyocly.ai4j.harness;

/**
 * Independent host-side gate verifier invoked by {@code witnessRerun}.
 */
@FunctionalInterface
public interface WitnessRunner {

    WitnessResult run(WitnessRequest request) throws Exception;
}
