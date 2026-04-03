package io.github.lnyocly.ai4j.agent.runtime;

import java.util.Collections;
import java.util.List;

public interface Planner {

    List<String> plan(String goal);

    static Planner simple() {
        return new SimplePlanner();
    }

    class SimplePlanner implements Planner {
        @Override
        public List<String> plan(String goal) {
            if (goal == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(goal);
        }
    }
}
