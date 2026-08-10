package io.github.lnyocly.ai4j.platform.openai.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

        /**
         * 是否启用严格模式。OpenAI 官方建议开启，开启后模型的函数调用会严格遵循
         * schema，而不是尽力而为。
         *
         * <p>严格模式对 schema 有硬性要求：{@code additionalProperties} 必须为
         * {@code false}，且 {@code properties} 中所有字段都必须出现在
         * {@code required} 里；不满足时请求会被拒绝。使用
         * {@link Parameter#enforceStrictSchema()} 可以把已有 schema 调整到位。
         *
         * <p>为 {@code null}（默认）时不会出现在请求体中，行为与历史版本一致。
         */
        private Boolean strict;

        /** 兼容构造器：不带 strict（保持既有调用方签名不变）。 */
        public Function(String name, String description, Parameter parameters) {
            this(name, description, parameters, null);
        }


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

            /**
             * 是否允许 schema 之外的额外属性。严格模式要求为 {@code false}。
             *
             * <p>为 {@code null}（默认）时不会出现在请求体中。
             */
            @JsonProperty("additionalProperties")
            private Boolean additionalProperties;

            /** 兼容构造器：不带 additionalProperties（保持既有调用方签名不变）。 */
            public Parameter(String type, Map<String, Property> properties, List<String> required) {
                this(type, properties, required, null);
            }

            /**
             * 把当前 schema 调整为满足严格模式的形态：
             *
             * <ol>
             *   <li>{@code additionalProperties} 置为 {@code false}</li>
             *   <li>把 {@code properties} 中所有字段补进 {@code required}</li>
             * </ol>
             *
             * <p>原本可选的字段会按官方要求改为可空类型（{@code ["string","null"]}），
             * 因此语义仍然是"可以不传值"，只是表达方式从"不在 required 里"变成"值可以为 null"。
             */
            public Parameter enforceStrictSchema() {
                this.additionalProperties = Boolean.FALSE;
                if (properties == null || properties.isEmpty()) {
                    if (required == null) {
                        required = new java.util.ArrayList<String>();
                    }
                    return this;
                }

                List<String> alreadyRequired = required == null
                        ? new java.util.ArrayList<String>()
                        : new java.util.ArrayList<String>(required);

                List<String> allNames = new java.util.ArrayList<String>();
                for (Map.Entry<String, Property> entry : properties.entrySet()) {
                    allNames.add(entry.getKey());
                    if (!alreadyRequired.contains(entry.getKey()) && entry.getValue() != null) {
                        entry.getValue().setNullable(true);
                    }
                }
                this.required = allNames;
                return this;
            }
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
            @JsonIgnore
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

            /**
             * 严格模式下的可选字段，需表达为可空类型（{@code ["string","null"]}）。
             * 仅影响序列化，{@link #getType()} 仍返回原始类型字符串。
             */
            @JsonIgnore
            private boolean nullable;

            /** 兼容构造器：不带 nullable（保持既有调用方签名不变）。 */
            public Property(String type, String description, List<String> enumValues, Property items) {
                this(type, description, enumValues, items, false);
            }

            /**
             * 序列化 {@code type}：普通情况输出字符串，可空时输出
             * {@code ["<type>","null"]}——这是 OpenAI 严格模式表达可选字段的方式。
             */
            @JsonProperty("type")
            public Object getSerializedType() {
                if (type == null) {
                    return null;
                }
                return nullable ? new String[]{type, "null"} : type;
            }

            /** 反序列化时同时接受字符串与数组两种形态。 */
            @JsonProperty("type")
            public void setSerializedType(Object value) {
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    for (Object candidate : values) {
                        if (candidate != null && !"null".equals(candidate)) {
                            this.type = String.valueOf(candidate);
                        } else if ("null".equals(candidate)) {
                            this.nullable = true;
                        }
                    }
                } else if (value != null) {
                    this.type = String.valueOf(value);
                }
            }
        }

    }

}
