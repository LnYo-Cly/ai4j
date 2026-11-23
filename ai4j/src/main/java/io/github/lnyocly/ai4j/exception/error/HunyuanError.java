package io.github.lnyocly.ai4j.exception.error;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 腾讯混元错误实体
 *
 * {"Response":{"RequestId":"e4650694-f018-4490-b4d0-d5242cd68106","Error":{"Code":"InvalidParameterValue.Model","Message":"模型不存在"}}}
 *
 * @Date 2024/9/18 21:28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HunyuanError {
    @JsonProperty("Response")
    @JsonAlias("response")
    private Response Response;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Response {
        @JsonProperty("Error")
        @JsonAlias("error")
        private Error Error;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Error {
            @JsonProperty("Code")
            @JsonAlias("code")
            private String Code;
            @JsonProperty("Message")
            @JsonAlias("message")
            private String Message;
        }
    }
}
