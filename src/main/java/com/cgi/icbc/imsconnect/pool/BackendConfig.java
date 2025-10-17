package com.cgi.icbc.imsconnect.pool;

/**
 * Configuration for a backend IMS system.
 */
public class BackendConfig {

    private String name;
    private String host;
    private int port;
    private boolean sslEnabled = false;
    private int weight = 1; // For weighted load balancing
    private long healthCheckTimeoutMs = 5000;
    private String datastoreName;

    public BackendConfig() {}

    public BackendConfig(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public boolean isSslEnabled() { return sslEnabled; }
    public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }

    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }

    public long getHealthCheckTimeoutMs() { return healthCheckTimeoutMs; }
    public void setHealthCheckTimeoutMs(long healthCheckTimeoutMs) { this.healthCheckTimeoutMs = healthCheckTimeoutMs; }

    public String getDatastoreName() { return datastoreName; }
    public void setDatastoreName(String datastoreName) { this.datastoreName = datastoreName; }

    @Override
    public String toString() {
        return String.format("BackendConfig{name='%s', host='%s', port=%d, ssl=%b, weight=%d}",
                           name, host, port, sslEnabled, weight);
    }
}