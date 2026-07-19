package io.github.lnyocly.ai4j.rag;

import java.util.Collections;
import java.util.List;

public class RagOnlineEvaluator {

    private final RagJudge judge;

    public RagOnlineEvaluator(RagJudge judge) {
        if (judge == null) {
            throw new IllegalArgumentException("judge is required");
        }
        this.judge = judge;
    }

    public RagJudgeEvaluation evaluate(RagResult result, String answer) throws Exception {
        RagJudgeEvaluation evaluation = judge.judge(RagJudgeRequest.builder()
                .query(result == null ? null : result.getQuery())
                .answer(answer)
                .context(result == null ? null : result.getContext())
                .hits(hits(result))
                .build());
        if (result != null) {
            RagTrace trace = result.getTrace();
            if (trace == null) {
                trace = new RagTrace();
                result.setTrace(trace);
            }
            trace.setJudgeEvaluation(evaluation);
        }
        return evaluation;
    }

    private List<RagHit> hits(RagResult result) {
        if (result == null || result.getHits() == null) {
            return Collections.emptyList();
        }
        return result.getHits();
    }
}
