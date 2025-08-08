package io.github.lnyocly.ai4j.platform.openai.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/12 14:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tool {

    /**
     * 工具类型，目前为“function”
     */
    private String type;
    private Function function;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {

        /**
         * 函数名称
         */
        private String name;

        /**
         * 函数描述
         */
        private String description;

        /**
         * 函数的参数 key为参数名称，value为参数属性
         */
        private Parameter parameters;



        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Parameter {

            private String type = "object";

            /**
             * 函数的参数 key为参数名称，value为参数属性
             */
            private Map<String, Property> properties;

            /**
             * 必须的参数
             */
            private List<String> required;

        }

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Property {
            /**
             * 属性类型
             */
            private String type;

            /**
             * 属性描述
             */
            private String description;

            /**
             * 枚举项
             */
            @JsonProperty("enum")
            private List<String> enumValues;

            /**
             * 数组元素类型定义（当type为array时使用）
             */
            private Property items;
        }

    }

}
