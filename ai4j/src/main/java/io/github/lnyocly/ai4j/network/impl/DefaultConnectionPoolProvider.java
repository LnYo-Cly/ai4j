package io.github.lnyocly.ai4j.network.impl;

import io.github.lnyocly.ai4j.network.ConnectionPoolProvider;
import okhttp3.ConnectionPool;

/**
 * @Author cly
 * @Description ConnectionPool默认实现
 * @Date 2024/10/16 23:11
 */
public class DefaultConnectionPoolProvider implements ConnectionPoolProvider {
    @Override
    public ConnectionPool getConnectionPool() {
        return new ConnectionPool();
    }
}
