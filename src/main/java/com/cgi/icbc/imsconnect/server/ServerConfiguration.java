package com.cgi.icbc.imsconnect.server;

/**
 * Configuration class for IMS Connect server settings.
 */
public class ServerConfiguration {

    private int bossThreads = 1;
    private int workerThreads = Runtime.getRuntime().availableProcessors();
    private int backlogSize = 128;
    private boolean keepAlive = true;
    private boolean tcpNoDelay = true;
    private int readIdleTimeSeconds = 300;  // 5 minutes
    private int writeIdleTimeSeconds = 300; // 5 minutes
    private int allIdleTimeSeconds = 600;   // 10 minutes
    private int shutdownTimeoutSeconds = 30;
    private int maxMessageSize = 32 * 1024; // 32KB max message size
    private String datastoreName = "IMSA";

    public static ServerConfiguration defaultConfig() {
        return new ServerConfiguration();
    }

    // Getters and setters
    public int getBossThreads() { return bossThreads; }
    public void setBossThreads(int bossThreads) { this.bossThreads = bossThreads; }

    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }

    public int getBacklogSize() { return backlogSize; }
    public void setBacklogSize(int backlogSize) { this.backlogSize = backlogSize; }

    public boolean isKeepAlive() { return keepAlive; }
    public void setKeepAlive(boolean keepAlive) { this.keepAlive = keepAlive; }

    public boolean isTcpNoDelay() { return tcpNoDelay; }
    public void setTcpNoDelay(boolean tcpNoDelay) { this.tcpNoDelay = tcpNoDelay; }

    public int getReadIdleTimeSeconds() { return readIdleTimeSeconds; }
    public void setReadIdleTimeSeconds(int readIdleTimeSeconds) { this.readIdleTimeSeconds = readIdleTimeSeconds; }

    public int getWriteIdleTimeSeconds() { return writeIdleTimeSeconds; }
    public void setWriteIdleTimeSeconds(int writeIdleTimeSeconds) { this.writeIdleTimeSeconds = writeIdleTimeSeconds; }

    public int getAllIdleTimeSeconds() { return allIdleTimeSeconds; }
    public void setAllIdleTimeSeconds(int allIdleTimeSeconds) { this.allIdleTimeSeconds = allIdleTimeSeconds; }

    public int getShutdownTimeoutSeconds() { return shutdownTimeoutSeconds; }
    public void setShutdownTimeoutSeconds(int shutdownTimeoutSeconds) { this.shutdownTimeoutSeconds = shutdownTimeoutSeconds; }

    public int getMaxMessageSize() { return maxMessageSize; }
    public void setMaxMessageSize(int maxMessageSize) { this.maxMessageSize = maxMessageSize; }

    public String getDatastoreName() { return datastoreName; }
    public void setDatastoreName(String datastoreName) { this.datastoreName = datastoreName; }
}