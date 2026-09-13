package io.github.lnyocly.ai4j.harness;

@FunctionalInterface
public interface HarnessStateMutation {

    HarnessState apply(HarnessState current);
}
