package io.github.lnyocly.ai4j.network;

import okhttp3.ConnectionPool;

/**
 * @Author cly
 * @Description ConnectionPool提供器
 * @Date 2024/10/16 23:10
 */
public interface ConnectionPoolProvider {
    ConnectionPool getConnectionPool();
}
