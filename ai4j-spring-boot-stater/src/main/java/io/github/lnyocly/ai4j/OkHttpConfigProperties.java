package io.github.lnyocly.ai4j;

import okhttp3.logging.HttpLoggingInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * @Author cly
 * @Description TODO
 * @Date 2024/8/10 0:49
 */
@ConfigurationProperties(prefix = "ai.okhttp")
public class OkHttpConfigProperties {

    private Proxy.Type proxyType = Proxy.Type.HTTP;
    private String proxyUrl = "127.0.0.1";
    private int proxyPort = 10809;

    private HttpLoggingInterceptor.Level log = HttpLoggingInterceptor.Level.HEADERS;
    private int connectTimeout = 300;
    private int writeTimeout = 300;
    private int readTimeout = 300;
    private TimeUnit timeUnit = TimeUnit.SECONDS;

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public HttpLoggingInterceptor.Level getLog() {
        return log;
    }

    public void setLog(HttpLoggingInterceptor.Level log) {
        this.log = log;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public void setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    // Getters and Setters
    public String getProxyUrl() {
        return proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public Proxy.Type getProxyType() {
        return proxyType;
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType = proxyType;
    }
}
