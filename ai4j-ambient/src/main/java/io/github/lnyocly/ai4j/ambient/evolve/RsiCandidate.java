package io.github.lnyocly.ai4j.ambient.evolve;

/**
 * Common contract implemented by L1, L2, and L3 RSI evolution candidates.
 */
public interface RsiCandidate {

    String getCandidateId();

    String getScopeKey();

    String getDiagnosisId();

    CandidateTier getTier();

    String getBaseVersion();

    String getTargetVersion();

    String getMetaVersion();

    String getDigest();

    CandidateStatus getStatus();

    void setStatus(CandidateStatus status);
}
