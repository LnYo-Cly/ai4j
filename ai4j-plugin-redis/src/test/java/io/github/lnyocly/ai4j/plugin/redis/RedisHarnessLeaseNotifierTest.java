package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.harness.HarnessConflictException;
import io.github.lnyocly.ai4j.harness.LeaseRecord;
import io.github.lnyocly.ai4j.harness.WriterEpochFence;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class RedisHarnessLeaseNotifierTest {

    @Test
    public void enforcesWriterEpochAndFencingTokenAcrossPublishPollAck() {
        InMemoryRedisClientAdapter client = new InMemoryRedisClientAdapter();
        RedisHarnessLeaseNotifier notifier = new RedisHarnessLeaseNotifier(client, "harness:lease:", 300L);

        WriterEpochFence epoch1 = notifier.claimWriterEpoch("node-1", 0L);
        Assert.assertEquals(1L, epoch1.getEpoch());
        Assert.assertEquals("node-1", epoch1.getHolderNodeId());

        try {
            notifier.claimWriterEpoch("node-2", 0L);
            Assert.fail("Expected HarnessConflictException for stale expectedEpoch");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("writer epoch conflict"));
        }

        LeaseRecord lease1 = LeaseRecord.builder()
                .leaseId("lease-1")
                .executionId("exec-1")
                .workerId("worker-1")
                .fencingToken(10L)
                .build();

        RedisHarnessLeaseNotifier.LeaseNotificationResult published =
                notifier.publish(lease1, epoch1, "CLAIMED", "worker-1 claimed lease");
        Assert.assertTrue(published.isAccepted());
        Assert.assertEquals(1, client.getPublishedMessages().size());
        String ntf1Id = published.getNotification().getNotificationId();

        // Older fencing token on same execution must be rejected as STALE_FENCE.
        LeaseRecord staleFenceLease = LeaseRecord.builder()
                .leaseId("lease-0")
                .executionId("exec-1")
                .workerId("worker-0")
                .fencingToken(9L)
                .build();
        RedisHarnessLeaseNotifier.LeaseNotificationResult staleFenceResult =
                notifier.publish(staleFenceLease, epoch1, "HEARTBEAT", "stale fence");
        Assert.assertTrue(staleFenceResult.isFenced());
        Assert.assertEquals(RedisHarnessLeaseNotifier.Outcome.STALE_FENCE, staleFenceResult.getOutcome());

        // Older writer epoch must be rejected as STALE_EPOCH.
        RedisHarnessLeaseNotifier.LeaseNotificationResult staleEpochResult =
                notifier.publish(lease1, new WriterEpochFence(0L, "zombie-node", 100L), "RELEASED", "zombie epoch");
        Assert.assertTrue(staleEpochResult.isFenced());
        Assert.assertEquals(RedisHarnessLeaseNotifier.Outcome.STALE_EPOCH, staleEpochResult.getOutcome());

        List<RedisHarnessLeaseNotifier.LeaseNotification> polled = notifier.poll(10);
        Assert.assertEquals(1, polled.size());
        Assert.assertEquals(ntf1Id, polled.get(0).getNotificationId());

        // Valid ack succeeds once and is idempotent on replay.
        Assert.assertTrue(notifier.ack(ntf1Id, 1L, 10L));
        Assert.assertFalse(notifier.ack(ntf1Id, 1L, 10L));
        Assert.assertTrue(notifier.poll().isEmpty());

        // Publish under epoch 1, then advance to epoch 2 -> pending epoch 1 notification is automatically fenced out!
        RedisHarnessLeaseNotifier.LeaseNotificationResult pendingInEpoch1 =
                notifier.publish(lease1, epoch1, "HEARTBEAT", "before split-brain");
        Assert.assertTrue(pendingInEpoch1.isAccepted());
        String oldId = pendingInEpoch1.getNotification().getNotificationId();

        WriterEpochFence epoch2 = notifier.claimWriterEpoch("node-2", 1L);
        Assert.assertEquals(2L, epoch2.getEpoch());
        Assert.assertFalse("Stale notification from epoch 1 must not ack in epoch 2",
                notifier.ack(oldId, 1L, 10L));
        Assert.assertTrue("Poll must drop notifications from superseded epochs", notifier.poll().isEmpty());
    }
}
