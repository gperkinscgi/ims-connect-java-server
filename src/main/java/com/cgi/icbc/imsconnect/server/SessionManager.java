package com.cgi.icbc.imsconnect.server;

import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages IMS Connect client sessions and their lifecycle.
 * Integrates client ID management, message queuing, and timeout handling.
 */
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final ClientIdManager clientIdManager;
    private final MessageQueue messageQueue;
    private final TransactionTimeoutManager timeoutManager;
    private final Map<String, IMSSession> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "IMS-Session-Cleanup"));

    public SessionManager() {
        this(new ClientIdManager(), new MessageQueue(), new TransactionTimeoutManager());
    }

    public SessionManager(ClientIdManager clientIdManager, MessageQueue messageQueue,
                         TransactionTimeoutManager timeoutManager) {
        this.clientIdManager = clientIdManager;
        this.messageQueue = messageQueue;
        this.timeoutManager = timeoutManager;

        // Start periodic cleanup
        startCleanupTask();
    }

    /**
     * Creates a new session for a connection.
     *
     * @param connectionId the connection identifier
     * @param ctx the channel context
     * @return created session
     */
    public IMSSession createSession(String connectionId, ChannelHandlerContext ctx) {
        IMSSession session = new IMSSession(connectionId, ctx, Instant.now());
        activeSessions.put(connectionId, session);

        logger.info("Created session for connection: {}", connectionId);
        return session;
    }

    /**
     * Gets an existing session by connection ID.
     *
     * @param connectionId the connection identifier
     * @return session or null if not found
     */
    public IMSSession getSession(String connectionId) {
        return activeSessions.get(connectionId);
    }

    /**
     * Registers a client ID with a session.
     *
     * @param connectionId the connection identifier
     * @param clientId the client ID
     * @param cancelExisting whether to cancel existing client with same ID
     * @return true if registration successful
     */
    public boolean registerClientId(String connectionId, String clientId, boolean cancelExisting) {
        IMSSession session = activeSessions.get(connectionId);
        if (session == null) {
            logger.warn("Session not found for connection: {}", connectionId);
            return false;
        }

        boolean registered = clientIdManager.handleDuplicateClientId(clientId, connectionId, cancelExisting);
        if (registered) {
            session.setClientId(clientId);
            logger.info("Registered client ID {} for connection: {}", clientId, connectionId);
        } else {
            logger.warn("Failed to register client ID {} for connection: {}", clientId, connectionId);
        }

        return registered;
    }

    /**
     * Closes a session and cleans up resources.
     *
     * @param connectionId the connection identifier
     * @return true if session was found and closed
     */
    public boolean closeSession(String connectionId) {
        IMSSession session = activeSessions.remove(connectionId);
        if (session != null) {
            // Cleanup client ID if registered
            if (session.getClientId() != null) {
                clientIdManager.unregisterClient(session.getClientId());
                messageQueue.clearClientQueue(session.getClientId());
                timeoutManager.cancelClientTimeouts(session.getClientId());
            }

            session.markClosed();
            logger.info("Closed session for connection: {} (client: {})",
                       connectionId, session.getClientId());
            return true;
        }

        logger.debug("Session not found for connection: {}", connectionId);
        return false;
    }

    /**
     * Updates the last activity time for a session.
     *
     * @param connectionId the connection identifier
     */
    public void updateSessionActivity(String connectionId) {
        IMSSession session = activeSessions.get(connectionId);
        if (session != null) {
            session.updateLastActivity();
        }
    }

    /**
     * Gets session statistics.
     *
     * @return session statistics
     */
    public SessionStatistics getStatistics() {
        int activeSessions = this.activeSessions.size();
        int activeClients = clientIdManager.getActiveSessionCount();
        MessageQueue.QueueStatistics queueStats = messageQueue.getStatistics();
        int activeTimeouts = timeoutManager.getActiveTimeoutCount();

        return new SessionStatistics(activeSessions, activeClients,
                                   queueStats.getTotalMessages(), activeTimeouts);
    }

    /**
     * Shuts down the session manager.
     */
    public void shutdown() {
        logger.info("Shutting down session manager...");

        // Close all active sessions
        activeSessions.keySet().forEach(this::closeSession);

        // Shutdown components
        timeoutManager.shutdown();
        cleanupExecutor.shutdown();

        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupExecutor.shutdownNow();
        }

        logger.info("Session manager shutdown complete");
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleWithFixedDelay(this::performCleanup, 60, 60, TimeUnit.SECONDS);
    }

    private void performCleanup() {
        try {
            logger.debug("Performing session cleanup...");

            // Cleanup expired client sessions (older than 1 hour)
            int expiredSessions = clientIdManager.cleanupExpiredSessions(3600);

            // Cleanup expired messages
            int expiredMessages = messageQueue.cleanupExpiredMessages();

            // Cleanup inactive connections (connections without activity for 2 hours)
            int inactiveSessions = cleanupInactiveSessions(7200);

            if (expiredSessions > 0 || expiredMessages > 0 || inactiveSessions > 0) {
                logger.info("Cleanup completed: {} expired sessions, {} expired messages, {} inactive connections",
                           expiredSessions, expiredMessages, inactiveSessions);
            }

        } catch (Exception e) {
            logger.error("Error during session cleanup", e);
        }
    }

    private int cleanupInactiveSessions(long maxIdleSeconds) {
        Instant cutoff = Instant.now().minusSeconds(maxIdleSeconds);
        int removedCount = 0;

        activeSessions.entrySet().removeIf(entry -> {
            IMSSession session = entry.getValue();
            if (session.getLastActivityTime().isBefore(cutoff)) {
                logger.info("Cleaning up inactive session: {} (last activity: {})",
                           entry.getKey(), session.getLastActivityTime());

                // Cleanup associated resources
                if (session.getClientId() != null) {
                    clientIdManager.unregisterClient(session.getClientId());
                    messageQueue.clearClientQueue(session.getClientId());
                    timeoutManager.cancelClientTimeouts(session.getClientId());
                }

                session.markClosed();
                return true;
            }
            return false;
        });

        return removedCount;
    }

    /**
     * Represents an IMS Connect session.
     */
    public static class IMSSession {
        private final String connectionId;
        private final ChannelHandlerContext channelContext;
        private final Instant creationTime;
        private volatile Instant lastActivityTime;
        private volatile String clientId;
        private volatile boolean closed = false;

        public IMSSession(String connectionId, ChannelHandlerContext channelContext, Instant creationTime) {
            this.connectionId = connectionId;
            this.channelContext = channelContext;
            this.creationTime = creationTime;
            this.lastActivityTime = creationTime;
        }

        public String getConnectionId() { return connectionId; }
        public ChannelHandlerContext getChannelContext() { return channelContext; }
        public Instant getCreationTime() { return creationTime; }
        public Instant getLastActivityTime() { return lastActivityTime; }
        public String getClientId() { return clientId; }
        public boolean isClosed() { return closed; }

        public void setClientId(String clientId) { this.clientId = clientId; }
        public void updateLastActivity() { this.lastActivityTime = Instant.now(); }
        public void markClosed() { this.closed = true; }

        @Override
        public String toString() {
            return String.format("IMSSession{connectionId='%s', clientId='%s', created=%s, active=%s}",
                               connectionId, clientId, creationTime, lastActivityTime);
        }
    }

    /**
     * Session statistics.
     */
    public static class SessionStatistics {
        private final int activeSessions;
        private final int activeClients;
        private final int queuedMessages;
        private final int activeTimeouts;

        public SessionStatistics(int activeSessions, int activeClients, int queuedMessages, int activeTimeouts) {
            this.activeSessions = activeSessions;
            this.activeClients = activeClients;
            this.queuedMessages = queuedMessages;
            this.activeTimeouts = activeTimeouts;
        }

        public int getActiveSessions() { return activeSessions; }
        public int getActiveClients() { return activeClients; }
        public int getQueuedMessages() { return queuedMessages; }
        public int getActiveTimeouts() { return activeTimeouts; }

        @Override
        public String toString() {
            return String.format("SessionStats{sessions=%d, clients=%d, messages=%d, timeouts=%d}",
                               activeSessions, activeClients, queuedMessages, activeTimeouts);
        }
    }
}