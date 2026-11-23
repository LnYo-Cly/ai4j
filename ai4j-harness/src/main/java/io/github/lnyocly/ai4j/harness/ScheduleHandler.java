package io.github.lnyocly.ai4j.harness;

@FunctionalInterface
public interface ScheduleHandler {

    String run(ScheduleContext context) throws Exception;
}
