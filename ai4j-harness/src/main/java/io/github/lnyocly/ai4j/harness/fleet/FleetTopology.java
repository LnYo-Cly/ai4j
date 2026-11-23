package io.github.lnyocly.ai4j.harness.fleet;

/**
 * Five multi-node deployment topologies supported by ai4j-harness 2.0.
 */
public enum FleetTopology {
    LOCAL,
    SHARED_CLUSTER,
    FLEET_CENTER,
    FLEET_EDGE,
    FLEET_PROXY
}
