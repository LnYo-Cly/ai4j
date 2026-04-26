package io.github.lnyocly.ai4j.flowgram.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/flowgram/demo/mock")
public class FlowGramDemoMockController {

    @GetMapping("/weather")
    public Map<String, Object> weather(@RequestParam(value = "city", required = false) String city,
                                       @RequestParam(value = "date", required = false) String date) {
        String safeCity = isBlank(city) ? "杭州" : city.trim();
        String safeDate = isBlank(date) ? "2026-04-02" : date.trim();
        int seed = Math.abs((safeCity + "|" + safeDate).hashCode());

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("city", safeCity);
        payload.put("date", safeDate);
        payload.put("weather", pickWeather(seed));
        payload.put("temperature", pickTemperature(seed));
        payload.put("advice", pickAdvice(seed));
        return payload;
    }

    private String pickWeather(int seed) {
        String[] values = new String[]{"晴", "多云", "小雨", "阴"};
        return values[seed % values.length];
    }

    private String pickTemperature(int seed) {
        int min = 12 + (seed % 7);
        int max = min + 5 + (seed % 4);
        return min + "-" + max + "C";
    }

    private String pickAdvice(int seed) {
        String[] values = new String[]{
                "适合按计划出行",
                "建议提前十分钟进站",
                "请留意沿途降雨变化",
                "建议准备一件薄外套"
        };
        return values[seed % values.length];
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
