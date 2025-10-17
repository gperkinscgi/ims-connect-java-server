package com.cgi.icbc.imsconnect.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages client ID generation and tracking for IMS Connect sessions.
 * Ensures unique client IDs and handles session cleanup.
 */
public class ClientIdManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientIdManager.class);

    private final AtomicLong sequenceNumber = new AtomicLong(1);
    private final Map<String, ClientSession> activeSessions = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final String serverPrefix;

    public ClientIdManager() {
        this("IMS");
    }

    public ClientIdManager(String serverPrefix) {
        this.serverPrefix = serverPrefix;
    }

    /**
     * Generates a unique client ID.
     *
     * @return generated client ID (8 characters max)
     */
    public String generateClientId() {
        long sequence = sequenceNumber.getAndIncrement();
        // Format: prefix + sequence (padded to 8 chars total)
        String clientId = String.format("%s%05d", serverPrefix, sequence % 100000);

        // Ensure uniqueness by checking against active sessions
        lock.readLock().lock();
        try {
            while (activeSessions.containsKey(clientId)) {
                sequence = sequenceNumber.getAndIncrement();
                clientId = String.format("%s%05d", serverPrefix, sequence % 100000);
            }
        } finally {
            lock.readLock().unlock();
        }

        logger.debug("Generated client ID: {}", clientId);
        return clientId;
    }

    /**
     * Registers a client session.
     *
     * @param clientId the client ID
     * @param connectionId the connection identifier
     * @return true if registration successful, false if client ID already exists
     */
    public boolean registerClient(String clientId, String connectionId) {
        lock.writeLock().lock();
        try {
            if (activeSessions.containsKey(clientId)) {
                logger.warn("Client ID {} already exists, rejecting registration", clientId);
                return false;
            }

            ClientSession session = new ClientSession(clientId, connectionId, Instant.now());
            activeSessions.put(clientId, session);
            logger.info("Registered client: {} on connection: {}", clientId, connectionId);
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Unregisters a client session.
     *
     * @param clientId the client ID to unregister
     * @return true if client was found and removed
     */
    public boolean unregisterClient(String clientId) {
        lock.writeLock().lock();
        try {
            ClientSession session = activeSessions.remove(clientId);
            if (session != null) {
                logger.info("Unregistered client: {} from connection: {}", clientId, session.getConnectionId());
                return true;
            } else {
                logger.debug("Client ID {} not found for unregistration", clientId);
                return false;
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if a client ID is currently active.
     *
     * @param clientId the client ID to check
     * @return true if client is active
     */
    public boolean isClientActive(String clientId) {
        lock.readLock().lock();
        try {
            return activeSessions.containsKey(clientId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Gets the session information for a client.
     *
     * @param clientId the client ID
     * @return client session or null if not found
     */
    public ClientSession getClientSession(String clientId) {
        lock.readLock().lock();
        try {
            return activeSessions.get(clientId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Handles duplicate client ID scenario based on IMS Connect protocol.
     * If IRMF3CANCID flag is set, terminates existing session.
     *
     * @param clientId the duplicate client ID
     * @param connectionId the new connection ID
     * @param cancelExisting whether to cancel existing session
     * @return true if duplicate was handled successfully
     */
    public boolean handleDuplicateClientId(String clientId, String connectionId, boolean cancelExisting) {
        lock.writeLock().lock();
        try {
            ClientSession existingSession = activeSessions.get(clientId);
            if (existingSession == null) {
                // No duplicate, proceed with registration
                return registerClient(clientId, connectionId);
            }

            if (cancelExisting) {
                logger.info("Cancelling existing session for client ID: {} on connection: {}",
                           clientId, existingSession.getConnectionId());

                // Remove existing session
                activeSessions.remove(clientId);

                // Register new session
                ClientSession newSession = new ClientSession(clientId, connectionId, Instant.now());
                activeSessions.put(clientId, newSession);

                logger.info("Registered new session for client ID: {} on connection: {}", clientId, connectionId);
                return true;

            } else {
                logger.warn("Duplicate client ID {} rejected - existing session on connection: {}",
                           clientId, existingSession.getConnectionId());
                return false;
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets the count of active client sessions.
     *
     * @return number of active sessions
     */
    public int getActiveSessionCount() {
        lock.readLock().lock();
        try {
            return activeSessions.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cleans up sessions older than the specified duration.
     *
     * @param maxAgeSeconds maximum age in seconds
     * @return number of sessions cleaned up
     */
    public int cleanupExpiredSessions(long maxAgeSeconds) {
        lock.writeLock().lock();
        try {
            Instant cutoff = Instant.now().minusSeconds(maxAgeSeconds);
            int removedCount = 0;

            activeSessions.entrySet().removeIf(entry -> {
                if (entry.getValue().getCreationTime().isBefore(cutoff)) {
                    logger.info("Cleaning up expired session: {} (age: {}s)",
                               entry.getKey(),
                               java.time.Duration.between(entry.getValue().getCreationTime(), Instant.now()).getSeconds());
                    return true;
                }
                return false;
            });

            if (removedCount > 0) {
                logger.info("Cleaned up {} expired sessions", removedCount);
            }

            return removedCount;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Validates client ID format according to IMS Connect standards.
     *
     * @param clientId the client ID to validate
     * @return true if valid format
     */
    public static boolean isValidClientIdFormat(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return false;
        }

        String trimmed = clientId.trim();
        return trimmed.length() <= 8 && trimmed.matches("[A-Z0-9]+");
    }

    /**
     * Represents an active client session.
     */
    public static class ClientSession {
        private final String clientId;
        private final String connectionId;
        private final Instant creationTime;
        private volatile Instant lastActivityTime;

        public ClientSession(String clientId, String connectionId, Instant creationTime) {
            this.clientId = clientId;
            this.connectionId = connectionId;
            this.creationTime = creationTime;
            this.lastActivityTime = creationTime;
        }

        public String getClientId() { return clientId; }
        public String getConnectionId() { return connectionId; }
        public Instant getCreationTime() { return creationTime; }
        public Instant getLastActivityTime() { return lastActivityTime; }

        public void updateLastActivity() {
            this.lastActivityTime = Instant.now();
        }

        @Override
        public String toString() {
            return String.format("ClientSession{clientId='%s', connectionId='%s', created=%s}",
                               clientId, connectionId, creationTime);
        }
    }
}