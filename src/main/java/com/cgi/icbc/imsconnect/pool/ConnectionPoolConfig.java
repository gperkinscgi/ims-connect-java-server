package com.cgi.icbc.imsconnect.pool;

/**
 * Configuration for connection pooling.
 */
public class ConnectionPoolConfig {

    private int minConnectionsPerBackend = 2;
    private int maxConnectionsPerBackend = 20;
    private long connectionTimeoutMs = 5000;
    private long connectionMaxIdleMs = 300000; // 5 minutes
    private long healthCheckIntervalMs = 30000; // 30 seconds
    private int maxRetries = 3;
    private long retryDelayMs = 1000;

    public int getMinConnectionsPerBackend() { return minConnectionsPerBackend; }
    public void setMinConnectionsPerBackend(int minConnectionsPerBackend) { this.minConnectionsPerBackend = minConnectionsPerBackend; }

    public int getMaxConnectionsPerBackend() { return maxConnectionsPerBackend; }
    public void setMaxConnectionsPerBackend(int maxConnectionsPerBackend) { this.maxConnectionsPerBackend = maxConnectionsPerBackend; }

    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public long getConnectionMaxIdleMs() { return connectionMaxIdleMs; }
    public void setConnectionMaxIdleMs(long connectionMaxIdleMs) { this.connectionMaxIdleMs = connectionMaxIdleMs; }

    public long getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
    public void setHealthCheckIntervalMs(long healthCheckIntervalMs) { this.healthCheckIntervalMs = healthCheckIntervalMs; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
}