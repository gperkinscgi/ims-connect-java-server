package com.cgi.icbc.imsconnect;

import com.cgi.icbc.imsconnect.config.IMSConnectProperties;
import com.cgi.icbc.imsconnect.pool.BackendConfig;
import com.cgi.icbc.imsconnect.pool.ConnectionPool;
import com.cgi.icbc.imsconnect.server.IMSConnectServer;
import com.cgi.icbc.imsconnect.server.SSLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;

/**
 * Main Spring Boot application for IMS Connect Server.
 */
@SpringBootApplication
@EnableConfigurationProperties(IMSConnectProperties.class)
public class IMSConnectServerApplication {

    private static final Logger logger = LoggerFactory.getLogger(IMSConnectServerApplication.class);

    private final IMSConnectServer imsConnectServer;
    private final ConnectionPool connectionPool;
    private final IMSConnectProperties properties;

    public IMSConnectServerApplication(IMSConnectServer imsConnectServer,
                                     ConnectionPool connectionPool,
                                     IMSConnectProperties properties) {
        this.imsConnectServer = imsConnectServer;
        this.connectionPool = connectionPool;
        this.properties = properties;
    }

    public static void main(String[] args) {
        SpringApplication.run(IMSConnectServerApplication.class, args);
    }

    @Bean
    public CommandLineRunner startIMSServer() {
        return args -> {
            logger.info("Starting IMS Connect Server...");

            try {
                // Configure SSL if enabled
                if (properties.getSecurity().getSsl().isEnabled()) {
                    configureSSL();
                }

                // Configure backend connections
                configureBackends();

                // Start the IMS Connect server
                CompletableFuture<Void> startFuture = imsConnectServer.start();
                startFuture.get(); // Wait for server to start

                logger.info("IMS Connect Server started successfully on port {}",
                           properties.getServer().getPort());

            } catch (Exception e) {
                logger.error("Failed to start IMS Connect Server", e);
                throw new RuntimeException("Server startup failed", e);
            }
        };
    }

    private void configureSSL() throws Exception {
        IMSConnectProperties.Security.Ssl sslProps = properties.getSecurity().getSsl();

        if (sslProps.getTruststorePath() != null) {
            // Mutual authentication
            SSLConfiguration sslConfig = SSLConfiguration.mutualAuth(
                    sslProps.getKeystorePath(),
                    sslProps.getKeystorePassword(),
                    sslProps.getKeyPassword(),
                    sslProps.getTruststorePath(),
                    sslProps.getTruststorePassword()
            );
            sslConfig.setEnabledProtocols(sslProps.getEnabledProtocols());
            imsConnectServer.configureSsl(sslConfig);
        } else {
            // Server-only authentication
            SSLConfiguration sslConfig = SSLConfiguration.serverAuth(
                    sslProps.getKeystorePath(),
                    sslProps.getKeystorePassword(),
                    sslProps.getKeyPassword()
            );
            sslConfig.setEnabledProtocols(sslProps.getEnabledProtocols());
            imsConnectServer.configureSsl(sslConfig);
        }

        logger.info("SSL/TLS configured successfully");
    }

    private void configureBackends() {
        for (IMSConnectProperties.Backend backendProps : properties.getBackends()) {
            BackendConfig backend = new BackendConfig();
            backend.setName(backendProps.getName());
            backend.setHost(backendProps.getHost());
            backend.setPort(backendProps.getPort());
            backend.setSslEnabled(backendProps.isSslEnabled());
            backend.setWeight(backendProps.getWeight());
            backend.setHealthCheckTimeoutMs(backendProps.getHealthCheckTimeoutMs());
            backend.setDatastoreName(backendProps.getDatastoreName());

            connectionPool.addBackend(backend);
            logger.info("Added backend: {} ({}:{})", backend.getName(), backend.getHost(), backend.getPort());
        }
    }

    @PreDestroy
    public void shutdownServer() {
        logger.info("Shutting down IMS Connect Server...");

        try {
            if (imsConnectServer != null) {
                imsConnectServer.stop().get();
            }

            if (connectionPool != null) {
                connectionPool.shutdown();
            }

            logger.info("IMS Connect Server shutdown complete");

        } catch (Exception e) {
            logger.error("Error during server shutdown", e);
        }
    }
}