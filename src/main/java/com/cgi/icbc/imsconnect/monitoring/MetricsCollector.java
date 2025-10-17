package com.cgi.icbc.imsconnect.monitoring;

import com.cgi.icbc.imsconnect.pool.ConnectionPool;
import com.cgi.icbc.imsconnect.server.SessionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and exposes metrics for monitoring systems like Prometheus.
 */
@Component
public class MetricsCollector {

    private final MeterRegistry meterRegistry;
    private final SessionManager sessionManager;
    private final ConnectionPool connectionPool;

    // Custom metrics
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);

    // Micrometer metrics
    private Counter transactionCounter;
    private Counter errorCounter;
    private Counter connectionCounter;
    private Timer transactionTimer;

    public MetricsCollector(MeterRegistry meterRegistry, SessionManager sessionManager, ConnectionPool connectionPool) {
        this.meterRegistry = meterRegistry;
        this.sessionManager = sessionManager;
        this.connectionPool = connectionPool;
    }

    @PostConstruct
    public void initializeMetrics() {
        // Counters
        transactionCounter = Counter.builder("ims_transactions_total")
                .description("Total number of IMS transactions processed")
                .register(meterRegistry);

        errorCounter = Counter.builder("ims_errors_total")
                .description("Total number of IMS transaction errors")
                .register(meterRegistry);

        connectionCounter = Counter.builder("ims_connections_total")
                .description("Total number of client connections")
                .register(meterRegistry);

        // Timer
        transactionTimer = Timer.builder("ims_transaction_duration")
                .description("Duration of IMS transaction processing")
                .register(meterRegistry);

        // Gauges for real-time metrics
        Gauge.builder("ims_active_sessions")
                .description("Number of active IMS sessions")
                .register(meterRegistry, this, MetricsCollector::getActiveSessions);

        Gauge.builder("ims_active_clients")
                .description("Number of active IMS clients")
                .register(meterRegistry, this, MetricsCollector::getActiveClients);

        Gauge.builder("ims_queued_messages")
                .description("Number of queued messages")
                .register(meterRegistry, this, MetricsCollector::getQueuedMessages);

        Gauge.builder("ims_pool_total_connections")
                .description("Total connections in pool")
                .register(meterRegistry, this, MetricsCollector::getPoolTotalConnections);

        Gauge.builder("ims_pool_active_connections")
                .description("Active connections in pool")
                .register(meterRegistry, this, MetricsCollector::getPoolActiveConnections);

        Gauge.builder("ims_pool_available_connections")
                .description("Available connections in pool")
                .register(meterRegistry, this, MetricsCollector::getPoolAvailableConnections);

        // JVM metrics
        Gauge.builder("jvm_memory_used_bytes")
                .description("Used JVM memory in bytes")
                .register(meterRegistry, this, MetricsCollector::getUsedMemory);

        Gauge.builder("jvm_memory_max_bytes")
                .description("Maximum JVM memory in bytes")
                .register(meterRegistry, this, MetricsCollector::getMaxMemory);
    }

    // Public methods for recording metrics
    public void recordTransaction() {
        transactionCounter.increment();
        totalTransactions.incrementAndGet();
    }

    public void recordError() {
        errorCounter.increment();
        totalErrors.incrementAndGet();
    }

    public void recordConnection() {
        connectionCounter.increment();
        totalConnections.incrementAndGet();
    }

    public Timer.Sample startTransactionTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTransactionDuration(Timer.Sample sample) {
        sample.stop(transactionTimer);
    }

    // Gauge value providers
    private double getActiveSessions(MetricsCollector collector) {
        try {
            return sessionManager.getStatistics().getActiveSessions();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getActiveClients(MetricsCollector collector) {
        try {
            return sessionManager.getStatistics().getActiveClients();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getQueuedMessages(MetricsCollector collector) {
        try {
            return sessionManager.getStatistics().getQueuedMessages();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getPoolTotalConnections(MetricsCollector collector) {
        try {
            return connectionPool.getStatistics().getTotalConnections();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getPoolActiveConnections(MetricsCollector collector) {
        try {
            return connectionPool.getStatistics().getActiveConnections();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getPoolAvailableConnections(MetricsCollector collector) {
        try {
            return connectionPool.getStatistics().getAvailableConnections();
        } catch (Exception e) {
            return 0;
        }
    }

    private double getUsedMemory(MetricsCollector collector) {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private double getMaxMemory(MetricsCollector collector) {
        return Runtime.getRuntime().maxMemory();
    }

    // Getters for custom metrics
    public long getTotalTransactions() { return totalTransactions.get(); }
    public long getTotalErrors() { return totalErrors.get(); }
    public long getTotalConnections() { return totalConnections.get(); }

    public double getErrorRate() {
        long total = totalTransactions.get();
        return total > 0 ? (double) totalErrors.get() / total : 0.0;
    }
}