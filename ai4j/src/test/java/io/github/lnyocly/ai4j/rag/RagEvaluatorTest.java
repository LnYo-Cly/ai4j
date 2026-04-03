package io.github.lnyocly.ai4j.rag;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RagEvaluatorTest {

    @Test
    public void shouldComputeStandardRetrievalMetrics() {
        List<RagHit> hits = Arrays.asList(
                RagHit.builder().id("a").content("A").build(),
                RagHit.builder().id("b").content("B").build(),
                RagHit.builder().id("c").content("C").build(),
                RagHit.builder().id("d").content("D").build()
        );

        RagEvaluation evaluation = new RagEvaluator().evaluate(hits, Arrays.asList("b", "d"), 4);

        Assert.assertEquals(Integer.valueOf(4), evaluation.getEvaluatedAtK());
        Assert.assertEquals(Integer.valueOf(4), evaluation.getRetrievedCount());
        Assert.assertEquals(Integer.valueOf(2), evaluation.getRelevantCount());
        Assert.assertEquals(Integer.valueOf(2), evaluation.getTruePositiveCount());
        Assert.assertEquals(0.5d, evaluation.getPrecisionAtK(), 0.0001d);
        Assert.assertEquals(1.0d, evaluation.getRecallAtK(), 0.0001d);
        Assert.assertEquals(0.6667d, evaluation.getF1AtK(), 0.001d);
        Assert.assertEquals(0.5d, evaluation.getMrr(), 0.0001d);
        Assert.assertEquals(0.6509d, evaluation.getNdcg(), 0.001d);
    }

    @Test
    public void shouldHandleNoRelevantDocuments() {
        List<RagHit> hits = Arrays.asList(
                RagHit.builder().id("a").content("A").build(),
                RagHit.builder().id("b").content("B").build()
        );

        RagEvaluation evaluation = new RagEvaluator().evaluate(hits, Arrays.asList("z"), 2);

        Assert.assertEquals(0.0d, evaluation.getPrecisionAtK(), 0.0001d);
        Assert.assertEquals(0.0d, evaluation.getRecallAtK(), 0.0001d);
        Assert.assertEquals(0.0d, evaluation.getF1AtK(), 0.0001d);
        Assert.assertEquals(0.0d, evaluation.getMrr(), 0.0001d);
        Assert.assertEquals(0.0d, evaluation.getNdcg(), 0.0001d);
    }
}
