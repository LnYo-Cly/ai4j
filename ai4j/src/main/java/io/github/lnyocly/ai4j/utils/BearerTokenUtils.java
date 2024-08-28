package io.github.lnyocly.ai4j.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class BearerTokenUtils {
    // 过期时间；默认24小时
    private static final long EXPIRE_MILLIS = 24 * 60 * 60 * 1000L;

    // 缓存服务
    public static Cache<String, String> cache = CacheBuilder.newBuilder()
            .initialCapacity(100)
            .expireAfterWrite(EXPIRE_MILLIS - (60 * 1000L), TimeUnit.MILLISECONDS)
            .build();

    /**
     * 对 API Key 进行签名
     * 新版机制中平台颁发的 API Key 同时包含 “用户标识 id” 和 “签名密钥 secret”，即格式为 {id}.{secret}
     *
     * @param apiKey 智谱APIkey
     * @return Token
     */
    public static String getToken(String apiKey) {
        // 分割APIKEY
        String[] args = apiKey.split("\\.");
        if (args.length != 2) {
            throw new IllegalArgumentException("API Key 格式错误");
        }
        String id = args[0];
        String secret = args[1];
        // 缓存Token
        String token = cache.getIfPresent(apiKey);
        if (null != token) return token;
        // 创建Token
        Algorithm algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> payload = new HashMap<>();
        payload.put("api_key", id);
        payload.put("exp", System.currentTimeMillis() + EXPIRE_MILLIS);
        payload.put("timestamp", System.currentTimeMillis());
        Map<String, Object> headerClaims = new HashMap<>();
        headerClaims.put("alg", "HS256");
        headerClaims.put("sign_type", "SIGN");
        token = JWT.create().withPayload(payload).withHeader(headerClaims).sign(algorithm);
        cache.put(id, token);
        return token;
    }

}
