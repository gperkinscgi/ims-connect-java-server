package com.cgi.icbc.imsconnect.config;

import com.cgi.icbc.imsconnect.conversation.ConversationManager;
import com.cgi.icbc.imsconnect.handlers.LegacySystemMessageHandler;
import com.cgi.icbc.imsconnect.handlers.OTMABankingTransactionHandler;
import com.cgi.icbc.imsconnect.handlers.SystemMessageHandler;
import com.cgi.icbc.imsconnect.monitoring.HealthCheckController;
import com.cgi.icbc.imsconnect.monitoring.MetricsCollector;
import com.cgi.icbc.imsconnect.pool.ConnectionPool;
import com.cgi.icbc.imsconnect.pool.ConnectionPoolConfig;
import com.cgi.icbc.imsconnect.security.AuditLogger;
import com.cgi.icbc.imsconnect.security.RACFSecurityParser;
import com.cgi.icbc.imsconnect.security.TransactionSecurityValidator;
import com.cgi.icbc.imsconnect.server.DefaultIMSServerHandler;
import com.cgi.icbc.imsconnect.server.IMSConnectServer;
import com.cgi.icbc.imsconnect.server.OTMAServerHandler;
import com.cgi.icbc.imsconnect.server.OTMATransactionHandler;
import com.cgi.icbc.imsconnect.server.SessionManager;
import com.cgi.icbc.imsconnect.server.ServerConfiguration;
import com.cgi.icbc.imsconnect.service.AccountService;
import com.cgi.icbc.imsconnect.transaction.IMSTransactionManager;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configuration for IMS Connect server components.
 */
@Configuration
@EnableConfigurationProperties(IMSConnectProperties.class)
public class IMSConnectAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(IMSConnectAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ConnectionPoolConfig connectionPoolConfig(IMSConnectProperties properties) {
        ConnectionPoolConfig config = new ConnectionPoolConfig();
        IMSConnectProperties.Pool poolProps = properties.getPool();

        config.setMinConnectionsPerBackend(poolProps.getMinConnectionsPerBackend());
        config.setMaxConnectionsPerBackend(poolProps.getMaxConnectionsPerBackend());
        config.setConnectionTimeoutMs(poolProps.getConnectionTimeoutMs());
        config.setConnectionMaxIdleMs(poolProps.getConnectionMaxIdleMs());
        config.setHealthCheckIntervalMs(poolProps.getHealthCheckIntervalMs());
        config.setMaxRetries(poolProps.getMaxRetries());
        config.setRetryDelayMs(poolProps.getRetryDelayMs());

        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionPool connectionPool(ConnectionPoolConfig config) {
        return new ConnectionPool(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultIMSServerHandler imsServerHandler(SessionManager sessionManager) {
        return new DefaultIMSServerHandler(sessionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServerConfiguration serverConfiguration(IMSConnectProperties properties) {
        ServerConfiguration config = new ServerConfiguration();
        IMSConnectProperties.Server serverProps = properties.getServer();

        config.setBossThreads(serverProps.getBossThreads());
        config.setWorkerThreads(serverProps.getWorkerThreads());
        config.setBacklogSize(serverProps.getBacklogSize());
        config.setKeepAlive(serverProps.isKeepAlive());
        config.setTcpNoDelay(serverProps.isTcpNoDelay());
        config.setReadIdleTimeSeconds(serverProps.getReadIdleTimeSeconds());
        config.setWriteIdleTimeSeconds(serverProps.getWriteIdleTimeSeconds());
        config.setAllIdleTimeSeconds(serverProps.getAllIdleTimeSeconds());
        config.setShutdownTimeoutSeconds(serverProps.getShutdownTimeoutSeconds());
        config.setMaxMessageSize(serverProps.getMaxMessageSize());
        config.setDatastoreName(serverProps.getDatastoreName());

        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public IMSConnectServer imsConnectServer(IMSConnectProperties properties,
                                           DefaultIMSServerHandler handler,
                                           ServerConfiguration config,
                                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                                           OTMAServerHandler otmaHandler) {
        int port = properties.getServer().getPort();

        if (otmaHandler != null) {
            logger.info("Creating IMS Connect server with OTMA support");
            return new IMSConnectServer(port, handler, otmaHandler, config);
        } else {
            logger.info("Creating legacy IMS Connect server");
            return new IMSConnectServer(port, handler, config);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.monitoring.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public HealthCheckController healthCheckController(SessionManager sessionManager,
                                                     ConnectionPool connectionPool) {
        return new HealthCheckController(sessionManager, connectionPool);
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.monitoring.metrics-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MetricsCollector metricsCollector(MeterRegistry meterRegistry,
                                           SessionManager sessionManager,
                                           ConnectionPool connectionPool) {
        return new MetricsCollector(meterRegistry, sessionManager, connectionPool);
    }

    // OTMA Support Beans
    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ConversationManager conversationManager() {
        return new ConversationManager();
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public IMSTransactionManager imsTransactionManager(AuditLogger auditLogger) {
        return new IMSTransactionManager(auditLogger);
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public RACFSecurityParser racfSecurityParser() {
        return new RACFSecurityParser();
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public TransactionSecurityValidator transactionSecurityValidator() {
        return new TransactionSecurityValidator();
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public AuditLogger auditLogger() {
        return new AuditLogger();
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public AccountService accountService() {
        return new AccountService();
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public OTMABankingTransactionHandler otmaBankingTransactionHandler(AccountService accountService) {
        return new OTMABankingTransactionHandler(accountService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SystemMessageHandler systemMessageHandler() {
        return new SystemMessageHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public LegacySystemMessageHandler legacySystemMessageHandler() {
        return new LegacySystemMessageHandler();
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public OTMAServerHandler otmaServerHandler(List<OTMATransactionHandler> transactionHandlers,
                                             IMSTransactionManager transactionManager,
                                             ConversationManager conversationManager,
                                             RACFSecurityParser securityParser,
                                             TransactionSecurityValidator securityValidator,
                                             AuditLogger auditLogger) {
        return new OTMAServerHandler(transactionHandlers, transactionManager, conversationManager,
                                   securityParser, securityValidator, auditLogger);
    }
}