package io.github.lnyocly.ai4j.harness;

/** A concurrent command could not be applied without violating a lease or CAS rule. */
public class HarnessConflictException extends HarnessStoreException {

    public HarnessConflictException(String message) {
        super(message);
    }
}
