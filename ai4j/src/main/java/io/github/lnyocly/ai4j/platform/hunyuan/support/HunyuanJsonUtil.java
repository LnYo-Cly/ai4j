package io.github.lnyocly.ai4j.platform.hunyuan.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * @Author cly
 * @Description 用于JSON字符串驼峰转换的工具类
 * @Date 2024/8/30 23:12
 */
public final class HunyuanJsonUtil {

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
        JsonNode root = JsonCodec.readTree(json);
        JsonNode converted = processJsonNode(root, isCamelCase);
        return converted.toString();
    }

    private static JsonNode processJsonNode(JsonNode node, boolean isCamelCase) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            return processJsonObject((ObjectNode) node, isCamelCase);
        }
        if (node.isArray()) {
            return processJsonArray((ArrayNode) node, isCamelCase);
        }
        return node;
    }

    private static ObjectNode processJsonObject(ObjectNode jsonObject, boolean isCamelCase) {
        ObjectNode newJsonObject = JsonCodec.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> entries = jsonObject.fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String newKey = isCamelCase ? toCamelCaseWithUppercase(entry.getKey()) : toSnakeCase(entry.getKey());
            newJsonObject.set(newKey, processJsonNode(entry.getValue(), isCamelCase));
        }
        return newJsonObject;
    }

    private static ArrayNode processJsonArray(ArrayNode jsonArray, boolean isCamelCase) {
        ArrayNode newArray = JsonCodec.createArrayNode();
        for (int i = 0; i < jsonArray.size(); i++) {
            newArray.add(processJsonNode(jsonArray.get(i), isCamelCase));
        }
        return newArray;
    }

    private static String toCamelCaseWithUppercase(String key) {
        String[] parts = key.split("_");
        StringBuilder camelCaseKey = new StringBuilder();
        for (String part : parts) {
            if (part.length() > 0) {
                camelCaseKey.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return camelCaseKey.toString();
    }

    private static String toSnakeCase(String key) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append("_");
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private HunyuanJsonUtil() {
    }
}
