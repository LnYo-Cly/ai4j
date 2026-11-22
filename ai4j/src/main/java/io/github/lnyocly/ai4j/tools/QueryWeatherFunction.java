package io.github.lnyocly.ai4j.tools;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.util.function.Function;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/13 17:40
 * @deprecated Sample function tool retained only for legacy demos; supply credentials via
 * {@code ai4j.seniverse.api-key} or {@code SENIVERSE_API_KEY} instead of relying on built-in defaults.
 */
@Deprecated
@Slf4j
@FunctionCall(name = "queryWeather", description = "查询目标地点的天气预报")
public class QueryWeatherFunction implements Function<QueryWeatherFunction.Request, String> {

    private static final String WEATHER_FAILED_MESSAGE = "获取天气失败 当前天气未知";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    @Override
    public String apply(Request request) {
        if (request == null || request.type == null || request.location == null || request.location.trim().isEmpty()) {
            return WEATHER_FAILED_MESSAGE;
        }
        String key = System.getProperty("ai4j.seniverse.api-key", System.getenv("SENIVERSE_API_KEY"));
        if (key == null || key.trim().isEmpty()) {
            return WEATHER_FAILED_MESSAGE;
        }

        // https://api.seniverse.com/v3/weather/hourly.json?key=your_api_key&location=beijing&start=0&hours=24
        // https://api.seniverse.com/v3/weather/daily.json?key=your_api_key&location=beijing&start=0&days=5
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.seniverse.com")
                .addPathSegments("v3/weather")
                .addPathSegment(request.type.name() + ".json")
                .addQueryParameter("key", key.trim())
                .addQueryParameter("location", request.location)
                .addQueryParameter("days", String.valueOf(request.days))
                .build();

        okhttp3.Request http = new okhttp3.Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = HTTP_CLIENT.newCall(http).execute()) {
            if (response.isSuccessful()) {
                // 解析响应体
                ResponseBody body = response.body();
                return body != null ? body.string() : "";
            } else {
                return WEATHER_FAILED_MESSAGE;
            }
        } catch (Exception e) {
            log.warn("Failed to query weather for location={}", request.location, e);
            return WEATHER_FAILED_MESSAGE;
        }
    }

    @Data
    @FunctionRequest
    public static class Request{
        @FunctionParameter(description = "需要查询天气的目标位置, 可以是城市中文名、城市拼音/英文名、省市名称组合、IP 地址、经纬度")
        private String location;
        @FunctionParameter(description = "需要查询未来天气的天数, 最多15日")
        private int days = 15;
        @FunctionParameter(description = "预报的天气类型，daily表示预报多天天气、hourly表示预测当天24天气、now为当前天气实况")
        private Type type;
    }

    public enum Type{
        daily,
        hourly,
        now
    }
}
