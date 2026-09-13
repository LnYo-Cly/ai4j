package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;

final class HarnessJson {

    private HarnessJson() {
    }

    static <T> T copy(T value, Class<T> type) {
        if (value == null) {
            return null;
        }
        return JSON.parseObject(JSON.toJSONString(value), type);
    }
}
