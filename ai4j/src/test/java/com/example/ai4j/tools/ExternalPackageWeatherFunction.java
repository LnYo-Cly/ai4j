package com.example.ai4j.tools;

import io.github.lnyocly.ai4j.annotation.FunctionCall;
import io.github.lnyocly.ai4j.annotation.FunctionParameter;
import io.github.lnyocly.ai4j.annotation.FunctionRequest;

import java.util.function.Function;

/**
 * 故意定义在 SDK 包名之外：用于验证 ToolUtil 的扫描范围可以被扩展到
 * {@code io.github.lnyocly.ai4j} 之外（见 ExternalScanPackageTest）。
 */
@FunctionCall(name = "external_package_weather", description = "Weather tool declared outside the SDK package")
public class ExternalPackageWeatherFunction implements Function<ExternalPackageWeatherFunction.Request, String> {

    @Override
    public String apply(Request request) {
        return "weather:" + request.getCity();
    }

    @FunctionRequest
    public static class Request {
        @FunctionParameter(description = "City name")
        private String city;

        @FunctionParameter(description = "Forecast days", required = false)
        private Integer days;

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public Integer getDays() {
            return days;
        }

        public void setDays(Integer days) {
            this.days = days;
        }
    }
}
