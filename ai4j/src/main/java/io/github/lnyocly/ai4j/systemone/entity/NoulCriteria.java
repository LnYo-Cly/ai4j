package io.github.lnyocly.ai4j.systemone.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional Noul criteria: descriptions for the {@code true}/{@code false}
 * branches. Each accepts EntryType (String, Map, List, or null).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoulCriteria {

    @JsonProperty("true")
    private Object trueBranch;

    @JsonProperty("false")
    private Object falseBranch;
}
