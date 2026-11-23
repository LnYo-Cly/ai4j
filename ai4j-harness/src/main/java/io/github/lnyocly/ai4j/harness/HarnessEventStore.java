package io.github.lnyocly.ai4j.harness;

import java.util.List;

/**
 * Extended CQRS event ledger and projection store contract implemented by
 * durable stores such as {@link JdbcHarnessStore} and {@link FileHarnessStore}.
 */
public interface HarnessEventStore extends HarnessStore {

    MutationReceipt appendEventAndProject(HarnessEventRecord event);

    MutationReceipt appendEventAndProject(HarnessEventRecord event,
                                          String entityStatus,
                                          String entityPayloadJson);

    CursorPage<HarnessEntityRecord> listEntitiesPage(String entityType,
                                                     String statusFilter,
                                                     String cursor,
                                                     int limit);

    List<HarnessEventRecord> listEventsSince(long afterSeq, int limit);

    int rebuildEntitiesFromEvents();

    WriterEpochFence claimWriterEpoch(String nodeId, long expectedEpoch);

    WriterEpochFence currentWriterEpoch();
}
