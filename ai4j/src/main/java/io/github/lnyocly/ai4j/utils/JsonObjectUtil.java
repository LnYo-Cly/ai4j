package io.github.lnyocly.ai4j.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author isxuwl
 * @Description 用于JSON字符串驼峰转换的工具类
 * @Date 2024/9/13 11:50
 */
public class JsonObjectUtil {

    // 将 JSON 字符串的字段转换为大写驼峰形式
    public static String toCamelCaseWithUppercaseJson(String json) {
        return convertJsonString(json, true);
    }

    // 将 JSON 字符串的字段转换为下划线形式
    public static String toSnakeCaseJson(String json) {
        return convertJsonString(json, false);
    }

    // 根据参数 isCamelCase 决定转换为驼峰命名还是下划线命名
    private static String convertJsonString(String json, boolean isCamelCase) {
        JSONObject jsonObject = JSON.parseObject(json);
        JSONObject newJsonObject = processJsonObject(jsonObject, isCamelCase);
        return newJsonObject.toJSONString();
    }

    private static JSONObject processJsonObject(JSONObject jsonObject, boolean isCamelCase) {
        JSONObject newJsonObject = new JSONObject();
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            String newKey = isCamelCase ? toCamelCaseWithUppercase(entry.getKey()) : toSnakeCase(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof JSONObject) {
                value = processJsonObject((JSONObject) value, isCamelCase);
            } else if (value instanceof JSONArray) {
                value = processJsonArray((JSONArray) value, isCamelCase);
            }

            newJsonObject.put(newKey, value);
        }
        return newJsonObject;
    }

    private static JSONArray processJsonArray(JSONArray jsonArray, boolean isCamelCase) {
        return jsonArray.stream()
                .map(element -> {
                    if (element instanceof JSONObject) {
                        return processJsonObject((JSONObject) element, isCamelCase);
                    } else if (element instanceof JSONArray) {
                        return processJsonArray((JSONArray) element, isCamelCase);
                    } else {
                        return element;
                    }
                })
                .collect(Collectors.toCollection(JSONArray::new));
    }

    private static String toCamelCaseWithUppercase(String key) {
        return String.join("", key.split("_"))
                .chars()
                .mapToObj(c -> Character.isUpperCase(c) ? "_" + Character.toLowerCase(c) : String.valueOf((char) c))
                .collect(Collectors.joining())
                .replaceFirst("^_", "");
    }

    private static String toSnakeCase(String key) {
        return key.chars()
                .mapToObj(c -> Character.isUpperCase(c) ? "_" + Character.toLowerCase(c) : String.valueOf((char) c))
                .collect(Collectors.joining())
                .replaceFirst("^_", "");
    }

    public static void main(String[] args) {
        JsonObjectUtil util = new JsonObjectUtil();

        // 测试大写驼峰转换
        String camelCaseJson = util.toCamelCaseWithUppercaseJson("{\"request_id\":\"123\",\"created_time\":456}");
        System.out.println(camelCaseJson);

        // 测试下划线转换
        String snakeCaseJson = util.toSnakeCaseJson("{\"RequestId\":\"123\",\"CreatedTime\":456}");
        System.out.println(snakeCaseJson);
    }
}