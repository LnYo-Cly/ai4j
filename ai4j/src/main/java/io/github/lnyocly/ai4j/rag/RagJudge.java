package io.github.lnyocly.ai4j.rag;

public interface RagJudge {

    RagJudgeEvaluation judge(RagJudgeRequest request) throws Exception;
}