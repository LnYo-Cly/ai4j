package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Identity used for attribution and authorization at the Harness boundary. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessActor {

    private String kind;
    private String id;
    private String displayName;

    public static HarnessActor agent(String id) {
        return HarnessActor.builder().kind("agent").id(id).displayName(id).build();
    }

    public static HarnessActor human(String id) {
        return HarnessActor.builder().kind("human").id(id).displayName(id).build();
    }

    public static HarnessActor system(String id) {
        return HarnessActor.builder().kind("system").id(id).displayName(id).build();
    }

    public static HarnessActor worker(String id) {
        return HarnessActor.builder().kind("worker").id(id).displayName(id).build();
    }

    public boolean isAgent() {
        return "agent".equalsIgnoreCase(kind);
    }

    public boolean isHuman() {
        return "human".equalsIgnoreCase(kind);
    }

    public boolean isSystem() {
        return "system".equalsIgnoreCase(kind);
    }
}
