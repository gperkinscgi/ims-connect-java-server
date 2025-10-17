package com.cgi.icbc.imsconnect.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Demonstration of the complete IMS Connect server with all advanced features.
 * Shows how to configure and start the server with SSL, session management, and custom handlers.
 */
public class IMSConnectServerDemo {

    private static final Logger logger = LoggerFactory.getLogger(IMSConnectServerDemo.class);

    public static void main(String[] args) {
        try {
            // Create session manager with all components
            SessionManager sessionManager = new SessionManager();

            // Create server handler
            DefaultIMSServerHandler handler = new DefaultIMSServerHandler(sessionManager);

            // Configure server
            ServerConfiguration config = ServerConfiguration.defaultConfig();
            config.setDatastoreName("DEMOIMS");
            config.setMaxMessageSize(64 * 1024); // 64KB max messages
            config.setBacklogSize(256);
            config.setWorkerThreads(8);

            // Create server
            IMSConnectServer server = new IMSConnectServer(9999, handler, config);

            // Optional: Configure SSL (uncomment to enable)
            // configureSsl(server);

            // Start server
            logger.info("Starting IMS Connect server on port 9999...");
            CompletableFuture<Void> startFuture = server.start();

            startFuture.get(30, TimeUnit.SECONDS);
            logger.info("IMS Connect server started successfully!");

            // Print server statistics periodically
            startStatisticsReporting(sessionManager);

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down IMS Connect server...");
                try {
                    server.stop().get(10, TimeUnit.SECONDS);
                    sessionManager.shutdown();
                    logger.info("Server shutdown complete");
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                }
            }));

            logger.info("IMS Connect server is running. Press Ctrl+C to stop.");

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start IMS Connect server", e);
            System.exit(1);
        }
    }

    private static void configureSsl(IMSConnectServer server) {
        try {
            // Example SSL configuration
            SSLConfiguration sslConfig = SSLConfiguration.serverAuth(
                "keystore.jks",      // Path to keystore
                "keystorepass",      // Keystore password
                "keypass"            // Key password
            );

            // For mutual authentication:
            // SSLConfiguration sslConfig = SSLConfiguration.mutualAuth(
            //     "keystore.jks", "keystorepass", "keypass",
            //     "truststore.jks", "truststorepass"
            // );

            server.configureSsl(sslConfig);
            logger.info("SSL/TLS configured for secure connections");

        } catch (Exception e) {
            logger.error("Failed to configure SSL", e);
        }
    }

    private static void startStatisticsReporting(SessionManager sessionManager) {
        Thread statsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000); // Report every minute

                    SessionManager.SessionStatistics stats = sessionManager.getStatistics();
                    logger.info("Server Statistics: {}", stats);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error reporting statistics", e);
                }
            }
        }, "Statistics-Reporter");

        statsThread.setDaemon(true);
        statsThread.start();
    }
}