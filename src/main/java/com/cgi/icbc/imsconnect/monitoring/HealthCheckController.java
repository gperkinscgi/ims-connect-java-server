package com.cgi.icbc.imsconnect.monitoring;

import com.cgi.icbc.imsconnect.pool.ConnectionPool;
import com.cgi.icbc.imsconnect.server.SessionManager;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for health check and monitoring endpoints.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthCheckController {

    private final SessionManager sessionManager;
    private final ConnectionPool connectionPool;
    private final Instant startTime;

    public HealthCheckController(SessionManager sessionManager, ConnectionPool connectionPool) {
        this.sessionManager = sessionManager;
        this.connectionPool = connectionPool;
        this.startTime = Instant.now();
    }

    /**
     * Basic health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());
        health.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());

        return ResponseEntity.ok(health);
    }

    /**
     * Detailed health check with component status.
     */
    @GetMapping("/health/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> components = new HashMap<>();

        // Session manager health
        try {
            SessionManager.SessionStatistics sessionStats = sessionManager.getStatistics();
            Map<String, Object> sessionHealth = new HashMap<>();
            sessionHealth.put("status", "UP");
            sessionHealth.put("activeSessions", sessionStats.getActiveSessions());
            sessionHealth.put("activeClients", sessionStats.getActiveClients());
            sessionHealth.put("queuedMessages", sessionStats.getQueuedMessages());
            components.put("sessionManager", sessionHealth);
        } catch (Exception e) {
            Map<String, Object> sessionHealth = new HashMap<>();
            sessionHealth.put("status", "DOWN");
            sessionHealth.put("error", e.getMessage());
            components.put("sessionManager", sessionHealth);
        }

        // Connection pool health
        try {
            ConnectionPool.ConnectionPoolStats poolStats = connectionPool.getStatistics();
            Map<String, Object> poolHealth = new HashMap<>();
            poolHealth.put("status", poolStats.getTotalBackends() > 0 ? "UP" : "DOWN");
            poolHealth.put("totalBackends", poolStats.getTotalBackends());
            poolHealth.put("totalConnections", poolStats.getTotalConnections());
            poolHealth.put("activeConnections", poolStats.getActiveConnections());
            poolHealth.put("availableConnections", poolStats.getAvailableConnections());
            components.put("connectionPool", poolHealth);
        } catch (Exception e) {
            Map<String, Object> poolHealth = new HashMap<>();
            poolHealth.put("status", "DOWN");
            poolHealth.put("error", e.getMessage());
            components.put("connectionPool", poolHealth);
        }

        // Overall status
        boolean allUp = components.values().stream()
                .allMatch(component -> "UP".equals(((Map<?, ?>) component).get("status")));

        health.put("status", allUp ? "UP" : "DOWN");
        health.put("timestamp", Instant.now());
        health.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());
        health.put("components", components);

        return ResponseEntity.ok(health);
    }

    /**
     * Server metrics endpoint.
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Runtime metrics
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> jvm = new HashMap<>();
        jvm.put("maxMemory", runtime.maxMemory());
        jvm.put("totalMemory", runtime.totalMemory());
        jvm.put("freeMemory", runtime.freeMemory());
        jvm.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
        jvm.put("availableProcessors", runtime.availableProcessors());
        metrics.put("jvm", jvm);

        // Session metrics
        SessionManager.SessionStatistics sessionStats = sessionManager.getStatistics();
        Map<String, Object> sessions = new HashMap<>();
        sessions.put("activeSessions", sessionStats.getActiveSessions());
        sessions.put("activeClients", sessionStats.getActiveClients());
        sessions.put("queuedMessages", sessionStats.getQueuedMessages());
        sessions.put("activeTimeouts", sessionStats.getActiveTimeouts());
        metrics.put("sessions", sessions);

        // Connection pool metrics
        ConnectionPool.ConnectionPoolStats poolStats = connectionPool.getStatistics();
        Map<String, Object> pool = new HashMap<>();
        pool.put("totalBackends", poolStats.getTotalBackends());
        pool.put("totalConnections", poolStats.getTotalConnections());
        pool.put("activeConnections", poolStats.getActiveConnections());
        pool.put("availableConnections", poolStats.getAvailableConnections());
        pool.put("utilization", poolStats.getTotalConnections() > 0 ?
                (double) poolStats.getActiveConnections() / poolStats.getTotalConnections() : 0.0);
        metrics.put("connectionPool", pool);

        metrics.put("timestamp", Instant.now());
        metrics.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());

        return ResponseEntity.ok(metrics);
    }

    /**
     * Readiness probe endpoint.
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> readiness = new HashMap<>();

        try {
            // Check if essential components are ready
            SessionManager.SessionStatistics sessionStats = sessionManager.getStatistics();
            ConnectionPool.ConnectionPoolStats poolStats = connectionPool.getStatistics();

            boolean ready = poolStats.getTotalBackends() > 0; // At least one backend available

            readiness.put("ready", ready);
            readiness.put("timestamp", Instant.now());

            if (ready) {
                return ResponseEntity.ok(readiness);
            } else {
                readiness.put("reason", "No backend connections available");
                return ResponseEntity.status(503).body(readiness);
            }

        } catch (Exception e) {
            readiness.put("ready", false);
            readiness.put("reason", "Health check failed: " + e.getMessage());
            readiness.put("timestamp", Instant.now());
            return ResponseEntity.status(503).body(readiness);
        }
    }

    /**
     * Liveness probe endpoint.
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> live() {
        Map<String, Object> liveness = new HashMap<>();
        liveness.put("alive", true);
        liveness.put("timestamp", Instant.now());
        liveness.put("uptime", java.time.Duration.between(startTime, Instant.now()).toString());

        return ResponseEntity.ok(liveness);
    }
}