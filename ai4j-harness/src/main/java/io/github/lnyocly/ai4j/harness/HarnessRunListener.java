package io.github.lnyocly.ai4j.harness;

/** Receives results produced by automatic asynchronous wakeup continuation. */
public interface HarnessRunListener {

    void onResult(HarnessRunResult result);
}
