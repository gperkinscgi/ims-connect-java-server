package com.cgi.icbc.imsconnect.pool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection pool for managing backend IMS system connections.
 * Provides connection pooling, health monitoring, and load balancing.
 */
public class ConnectionPool {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);

    private final ConnectionPoolConfig config;
    private final ConcurrentHashMap<String, BackendPool> backendPools = new ConcurrentHashMap<>();
    private final LoadBalancer loadBalancer;
    private volatile boolean shutdown = false;

    public ConnectionPool(ConnectionPoolConfig config) {
        this.config = config;
        this.loadBalancer = new RoundRobinLoadBalancer();
    }

    /**
     * Adds a backend IMS system to the connection pool.
     *
     * @param backend the backend configuration
     */
    public void addBackend(BackendConfig backend) {
        BackendPool pool = new BackendPool(backend, config);
        backendPools.put(backend.getName(), pool);
        loadBalancer.addBackend(backend.getName());

        logger.info("Added backend: {} ({}:{})", backend.getName(), backend.getHost(), backend.getPort());
    }

    /**
     * Removes a backend from the connection pool.
     *
     * @param backendName the backend name
     */
    public void removeBackend(String backendName) {
        BackendPool pool = backendPools.remove(backendName);
        if (pool != null) {
            pool.shutdown();
            loadBalancer.removeBackend(backendName);
            logger.info("Removed backend: {}", backendName);
        }
    }

    /**
     * Acquires a connection from the pool using load balancing.
     *
     * @param timeoutMs timeout in milliseconds
     * @return pooled connection or null if timeout
     */
    public PooledConnection acquireConnection(long timeoutMs) {
        if (shutdown) {
            return null;
        }

        String backendName = loadBalancer.selectBackend();
        if (backendName == null) {
            logger.warn("No available backends for connection");
            return null;
        }

        BackendPool pool = backendPools.get(backendName);
        if (pool == null) {
            logger.warn("Backend pool not found: {}", backendName);
            return null;
        }

        return pool.acquireConnection(timeoutMs);
    }

    /**
     * Returns a connection to the pool.
     *
     * @param connection the connection to return
     */
    public void releaseConnection(PooledConnection connection) {
        if (connection == null) {
            return;
        }

        BackendPool pool = backendPools.get(connection.getBackendName());
        if (pool != null) {
            pool.releaseConnection(connection);
        } else {
            // Backend was removed, close the connection
            connection.close();
        }
    }

    /**
     * Gets connection pool statistics.
     *
     * @return pool statistics
     */
    public ConnectionPoolStats getStatistics() {
        int totalConnections = 0;
        int activeConnections = 0;
        int availableConnections = 0;

        for (BackendPool pool : backendPools.values()) {
            BackendPoolStats stats = pool.getStatistics();
            totalConnections += stats.getTotalConnections();
            activeConnections += stats.getActiveConnections();
            availableConnections += stats.getAvailableConnections();
        }

        return new ConnectionPoolStats(
                backendPools.size(),
                totalConnections,
                activeConnections,
                availableConnections
        );
    }

    /**
     * Shuts down the connection pool.
     */
    public void shutdown() {
        shutdown = true;

        for (BackendPool pool : backendPools.values()) {
            pool.shutdown();
        }

        backendPools.clear();
        logger.info("Connection pool shutdown complete");
    }

    /**
     * Individual backend connection pool.
     */
    private static class BackendPool {
        private final BackendConfig backend;
        private final ConnectionPoolConfig config;
        private final BlockingQueue<PooledConnection> availableConnections;
        private final AtomicInteger totalConnections = new AtomicInteger(0);
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private volatile boolean shutdown = false;

        public BackendPool(BackendConfig backend, ConnectionPoolConfig config) {
            this.backend = backend;
            this.config = config;
            this.availableConnections = new LinkedBlockingQueue<>(config.getMaxConnectionsPerBackend());

            // Pre-create minimum connections
            for (int i = 0; i < config.getMinConnectionsPerBackend(); i++) {
                createConnection();
            }
        }

        public PooledConnection acquireConnection(long timeoutMs) {
            if (shutdown) {
                return null;
            }

            PooledConnection connection = null;

            try {
                // Try to get an existing connection
                connection = availableConnections.poll(timeoutMs, TimeUnit.MILLISECONDS);

                if (connection == null && totalConnections.get() < config.getMaxConnectionsPerBackend()) {
                    // Create new connection if under limit
                    connection = createConnection();
                }

                if (connection != null) {
                    if (connection.isValid()) {
                        activeConnections.incrementAndGet();
                        return connection;
                    } else {
                        // Connection is invalid, close and try again
                        connection.close();
                        totalConnections.decrementAndGet();
                        return acquireConnection(timeoutMs);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return null;
        }

        public void releaseConnection(PooledConnection connection) {
            if (shutdown || !connection.isValid()) {
                connection.close();
                totalConnections.decrementAndGet();
                return;
            }

            if (availableConnections.offer(connection)) {
                activeConnections.decrementAndGet();
            } else {
                // Pool is full, close excess connection
                connection.close();
                totalConnections.decrementAndGet();
            }
        }

        private PooledConnection createConnection() {
            try {
                PooledConnection connection = new PooledConnection(backend);
                totalConnections.incrementAndGet();
                return connection;

            } catch (Exception e) {
                logger.error("Failed to create connection to backend: {}", backend.getName(), e);
                return null;
            }
        }

        public BackendPoolStats getStatistics() {
            return new BackendPoolStats(
                    backend.getName(),
                    totalConnections.get(),
                    activeConnections.get(),
                    availableConnections.size()
            );
        }

        public void shutdown() {
            shutdown = true;

            // Close all available connections
            PooledConnection connection;
            while ((connection = availableConnections.poll()) != null) {
                connection.close();
            }

            logger.info("Backend pool shutdown: {}", backend.getName());
        }
    }

    /**
     * Connection pool statistics.
     */
    public static class ConnectionPoolStats {
        private final int totalBackends;
        private final int totalConnections;
        private final int activeConnections;
        private final int availableConnections;

        public ConnectionPoolStats(int totalBackends, int totalConnections,
                                 int activeConnections, int availableConnections) {
            this.totalBackends = totalBackends;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.availableConnections = availableConnections;
        }

        public int getTotalBackends() { return totalBackends; }
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getAvailableConnections() { return availableConnections; }

        @Override
        public String toString() {
            return String.format("ConnectionPoolStats{backends=%d, total=%d, active=%d, available=%d}",
                               totalBackends, totalConnections, activeConnections, availableConnections);
        }
    }

    /**
     * Backend pool statistics.
     */
    public static class BackendPoolStats {
        private final String backendName;
        private final int totalConnections;
        private final int activeConnections;
        private final int availableConnections;

        public BackendPoolStats(String backendName, int totalConnections,
                              int activeConnections, int availableConnections) {
            this.backendName = backendName;
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.availableConnections = availableConnections;
        }

        public String getBackendName() { return backendName; }
        public int getTotalConnections() { return totalConnections; }
        public int getActiveConnections() { return activeConnections; }
        public int getAvailableConnections() { return availableConnections; }
    }
}