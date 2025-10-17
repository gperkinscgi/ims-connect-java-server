package com.cgi.icbc.imsconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for IMS Connect server.
 */
@Configuration
@ConfigurationProperties(prefix = "ims-connect")
public class IMSConnectProperties {

    private Server server = new Server();
    private Pool pool = new Pool();
    private Security security = new Security();
    private Monitoring monitoring = new Monitoring();
    private Otma otma = new Otma();
    private SystemMessages systemMessages = new SystemMessages();
    private List<Backend> backends = new ArrayList<>();

    public static class Server {
        private int port = 9999;
        private String datastoreName = "IMSA";
        private int bossThreads = 1;
        private int workerThreads = Runtime.getRuntime().availableProcessors();
        private int backlogSize = 128;
        private boolean keepAlive = true;
        private boolean tcpNoDelay = true;
        private int readIdleTimeSeconds = 300;
        private int writeIdleTimeSeconds = 300;
        private int allIdleTimeSeconds = 600;
        private int shutdownTimeoutSeconds = 30;
        private int maxMessageSize = 32 * 1024;

        // Getters and setters
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getDatastoreName() { return datastoreName; }
        public void setDatastoreName(String datastoreName) { this.datastoreName = datastoreName; }

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
    }

    public static class Pool {
        private int minConnectionsPerBackend = 2;
        private int maxConnectionsPerBackend = 20;
        private long connectionTimeoutMs = 5000;
        private long connectionMaxIdleMs = 300000;
        private long healthCheckIntervalMs = 30000;
        private int maxRetries = 3;
        private long retryDelayMs = 1000;

        // Getters and setters
        public int getMinConnectionsPerBackend() { return minConnectionsPerBackend; }
        public void setMinConnectionsPerBackend(int minConnectionsPerBackend) { this.minConnectionsPerBackend = minConnectionsPerBackend; }

        public int getMaxConnectionsPerBackend() { return maxConnectionsPerBackend; }
        public void setMaxConnectionsPerBackend(int maxConnectionsPerBackend) { this.maxConnectionsPerBackend = maxConnectionsPerBackend; }

        public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
        public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

        public long getConnectionMaxIdleMs() { return connectionMaxIdleMs; }
        public void setConnectionMaxIdleMs(long connectionMaxIdleMs) { this.connectionMaxIdleMs = connectionMaxIdleMs; }

        public long getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
        public void setHealthCheckIntervalMs(long healthCheckIntervalMs) { this.healthCheckIntervalMs = healthCheckIntervalMs; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }

    public static class Security {
        private boolean enabled = false;
        private Ssl ssl = new Ssl();
        private Authentication authentication = new Authentication();

        public static class Ssl {
            private boolean enabled = false;
            private String keystorePath;
            private String keystorePassword;
            private String keyPassword;
            private String truststorePath;
            private String truststorePassword;
            private String keystoreType = "JKS";
            private String truststoreType = "JKS";
            private boolean clientAuthRequired = false;
            private String[] enabledProtocols = {"TLSv1.2", "TLSv1.3"};

            // Getters and setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public String getKeystorePath() { return keystorePath; }
            public void setKeystorePath(String keystorePath) { this.keystorePath = keystorePath; }

            public String getKeystorePassword() { return keystorePassword; }
            public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }

            public String getKeyPassword() { return keyPassword; }
            public void setKeyPassword(String keyPassword) { this.keyPassword = keyPassword; }

            public String getTruststorePath() { return truststorePath; }
            public void setTruststorePath(String truststorePath) { this.truststorePath = truststorePath; }

            public String getTruststorePassword() { return truststorePassword; }
            public void setTruststorePassword(String truststorePassword) { this.truststorePassword = truststorePassword; }

            public String getKeystoreType() { return keystoreType; }
            public void setKeystoreType(String keystoreType) { this.keystoreType = keystoreType; }

            public String getTruststoreType() { return truststoreType; }
            public void setTruststoreType(String truststoreType) { this.truststoreType = truststoreType; }

            public boolean isClientAuthRequired() { return clientAuthRequired; }
            public void setClientAuthRequired(boolean clientAuthRequired) { this.clientAuthRequired = clientAuthRequired; }

            public String[] getEnabledProtocols() { return enabledProtocols; }
            public void setEnabledProtocols(String[] enabledProtocols) { this.enabledProtocols = enabledProtocols; }
        }

        public static class Authentication {
            private boolean required = false;
            private String type = "basic"; // basic, ldap, custom
            private String defaultUser = "admin";
            private String defaultPassword = "admin";

            // Getters and setters
            public boolean isRequired() { return required; }
            public void setRequired(boolean required) { this.required = required; }

            public String getType() { return type; }
            public void setType(String type) { this.type = type; }

            public String getDefaultUser() { return defaultUser; }
            public void setDefaultUser(String defaultUser) { this.defaultUser = defaultUser; }

            public String getDefaultPassword() { return defaultPassword; }
            public void setDefaultPassword(String defaultPassword) { this.defaultPassword = defaultPassword; }
        }

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Ssl getSsl() { return ssl; }
        public void setSsl(Ssl ssl) { this.ssl = ssl; }

        public Authentication getAuthentication() { return authentication; }
        public void setAuthentication(Authentication authentication) { this.authentication = authentication; }
    }

    public static class Monitoring {
        private boolean enabled = true;
        private boolean metricsEnabled = true;
        private boolean healthCheckEnabled = true;
        private String metricsPath = "/actuator/metrics";
        private String healthPath = "/actuator/health";

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isMetricsEnabled() { return metricsEnabled; }
        public void setMetricsEnabled(boolean metricsEnabled) { this.metricsEnabled = metricsEnabled; }

        public boolean isHealthCheckEnabled() { return healthCheckEnabled; }
        public void setHealthCheckEnabled(boolean healthCheckEnabled) { this.healthCheckEnabled = healthCheckEnabled; }

        public String getMetricsPath() { return metricsPath; }
        public void setMetricsPath(String metricsPath) { this.metricsPath = metricsPath; }

        public String getHealthPath() { return healthPath; }
        public void setHealthPath(String healthPath) { this.healthPath = healthPath; }
    }

    public static class Backend {
        private String name;
        private String host;
        private int port;
        private boolean sslEnabled = false;
        private int weight = 1;
        private long healthCheckTimeoutMs = 5000;
        private String datastoreName;

        // Getters and setters
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
    }

    public static class Otma {
        private boolean enabled = true;
        private Conversations conversations = new Conversations();
        private Security security = new Security();
        private List<String> supportedLterms = new ArrayList<>();

        public static class Conversations {
            private boolean enabled = true;
            private int maxConversations = 1000;
            private long conversationTimeoutMs = 300000; // 5 minutes
            private long cleanupIntervalMs = 60000; // 1 minute

            // Getters and setters
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }

            public int getMaxConversations() { return maxConversations; }
            public void setMaxConversations(int maxConversations) { this.maxConversations = maxConversations; }

            public long getConversationTimeoutMs() { return conversationTimeoutMs; }
            public void setConversationTimeoutMs(long conversationTimeoutMs) { this.conversationTimeoutMs = conversationTimeoutMs; }

            public long getCleanupIntervalMs() { return cleanupIntervalMs; }
            public void setCleanupIntervalMs(long cleanupIntervalMs) { this.cleanupIntervalMs = cleanupIntervalMs; }
        }

        public static class Security {
            private boolean racfEnabled = true;
            private boolean auditEnabled = true;
            private boolean tokenValidationEnabled = true;
            private List<String> exemptTransactionCodes = new ArrayList<>();

            // Getters and setters
            public boolean isRacfEnabled() { return racfEnabled; }
            public void setRacfEnabled(boolean racfEnabled) { this.racfEnabled = racfEnabled; }

            public boolean isAuditEnabled() { return auditEnabled; }
            public void setAuditEnabled(boolean auditEnabled) { this.auditEnabled = auditEnabled; }

            public boolean isTokenValidationEnabled() { return tokenValidationEnabled; }
            public void setTokenValidationEnabled(boolean tokenValidationEnabled) { this.tokenValidationEnabled = tokenValidationEnabled; }

            public List<String> getExemptTransactionCodes() { return exemptTransactionCodes; }
            public void setExemptTransactionCodes(List<String> exemptTransactionCodes) { this.exemptTransactionCodes = exemptTransactionCodes; }
        }

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Conversations getConversations() { return conversations; }
        public void setConversations(Conversations conversations) { this.conversations = conversations; }

        public Security getSecurity() { return security; }
        public void setSecurity(Security security) { this.security = security; }

        public List<String> getSupportedLterms() { return supportedLterms; }
        public void setSupportedLterms(List<String> supportedLterms) { this.supportedLterms = supportedLterms; }
    }

    public static class SystemMessages {
        private boolean enabled = true;
        private boolean pingEnabled = true;
        private boolean notifyEnabled = true;
        private boolean echoEnabled = true;
        private boolean statusEnabled = true;
        private boolean requireAuthentication = false;
        private int maxMessageSize = 1024;
        private List<String> allowedClients = new ArrayList<>();

        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isPingEnabled() { return pingEnabled; }
        public void setPingEnabled(boolean pingEnabled) { this.pingEnabled = pingEnabled; }

        public boolean isNotifyEnabled() { return notifyEnabled; }
        public void setNotifyEnabled(boolean notifyEnabled) { this.notifyEnabled = notifyEnabled; }

        public boolean isEchoEnabled() { return echoEnabled; }
        public void setEchoEnabled(boolean echoEnabled) { this.echoEnabled = echoEnabled; }

        public boolean isStatusEnabled() { return statusEnabled; }
        public void setStatusEnabled(boolean statusEnabled) { this.statusEnabled = statusEnabled; }

        public boolean isRequireAuthentication() { return requireAuthentication; }
        public void setRequireAuthentication(boolean requireAuthentication) { this.requireAuthentication = requireAuthentication; }

        public int getMaxMessageSize() { return maxMessageSize; }
        public void setMaxMessageSize(int maxMessageSize) { this.maxMessageSize = maxMessageSize; }

        public List<String> getAllowedClients() { return allowedClients; }
        public void setAllowedClients(List<String> allowedClients) { this.allowedClients = allowedClients; }
    }

    // Main class getters and setters
    public Server getServer() { return server; }
    public void setServer(Server server) { this.server = server; }

    public Pool getPool() { return pool; }
    public void setPool(Pool pool) { this.pool = pool; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public Monitoring getMonitoring() { return monitoring; }
    public void setMonitoring(Monitoring monitoring) { this.monitoring = monitoring; }

    public Otma getOtma() { return otma; }
    public void setOtma(Otma otma) { this.otma = otma; }

    public SystemMessages getSystemMessages() { return systemMessages; }
    public void setSystemMessages(SystemMessages systemMessages) { this.systemMessages = systemMessages; }

    public List<Backend> getBackends() { return backends; }
    public void setBackends(List<Backend> backends) { this.backends = backends; }
}