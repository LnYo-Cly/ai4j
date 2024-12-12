package io.github.lnyocly.ai4j.websearch.searxng;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/12/11 21:39
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearXNGResponse {
    private String query;

    @JsonProperty("number_of_results")
    private String numberOfResults;

    private List<Result> results;


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Result {
        private String url;
        private String title;
        private String content;
        //@JsonProperty("parsed_url")
        //private List<String> parsedUrl;
        //private Date publishedDate;
        //private List<String> engines;
        //private String category;
        //private float score;
    }
}
