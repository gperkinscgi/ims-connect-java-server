package com.cgi.icbc.imsconnect.server;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * SSL/TLS configuration for IMS Connect server.
 * Supports both self-signed and CA-signed certificates.
 */
public class SSLConfiguration {

    private String keystorePath;
    private String keystorePassword;
    private String keyPassword;
    private String truststorePath;
    private String truststorePassword;
    private String keystoreType = "JKS";
    private String truststoreType = "JKS";
    private boolean clientAuthRequired = false;
    private SslProvider sslProvider = SslProvider.JDK;
    private String[] enabledProtocols = {"TLSv1.2", "TLSv1.3"};
    private String[] enabledCipherSuites;

    public SSLConfiguration() {}

    /**
     * Creates SSL configuration for server-side authentication only.
     *
     * @param keystorePath path to the keystore file
     * @param keystorePassword keystore password
     * @param keyPassword private key password
     * @return SSL configuration
     */
    public static SSLConfiguration serverAuth(String keystorePath, String keystorePassword, String keyPassword) {
        SSLConfiguration config = new SSLConfiguration();
        config.keystorePath = keystorePath;
        config.keystorePassword = keystorePassword;
        config.keyPassword = keyPassword;
        return config;
    }

    /**
     * Creates SSL configuration with mutual authentication.
     *
     * @param keystorePath path to the keystore file
     * @param keystorePassword keystore password
     * @param keyPassword private key password
     * @param truststorePath path to the truststore file
     * @param truststorePassword truststore password
     * @return SSL configuration with client auth
     */
    public static SSLConfiguration mutualAuth(String keystorePath, String keystorePassword, String keyPassword,
                                             String truststorePath, String truststorePassword) {
        SSLConfiguration config = serverAuth(keystorePath, keystorePassword, keyPassword);
        config.truststorePath = truststorePath;
        config.truststorePassword = truststorePassword;
        config.clientAuthRequired = true;
        return config;
    }

    /**
     * Builds the Netty SslContext from this configuration.
     *
     * @return configured SslContext
     * @throws Exception if SSL context creation fails
     */
    public SslContext buildSslContext() throws Exception {
        SslContextBuilder contextBuilder = SslContextBuilder.forServer(
                loadKeyManagerFactory(),
                keyPassword
        );

        // Configure SSL provider
        contextBuilder.sslProvider(sslProvider);

        // Configure enabled protocols
        if (enabledProtocols != null && enabledProtocols.length > 0) {
            contextBuilder.protocols(enabledProtocols);
        }

        // Configure cipher suites
        if (enabledCipherSuites != null && enabledCipherSuites.length > 0) {
            contextBuilder.ciphers(java.util.Arrays.asList(enabledCipherSuites));
        }

        // Configure client authentication
        if (clientAuthRequired && truststorePath != null) {
            contextBuilder.trustManager(loadTrustManagerFactory());
            contextBuilder.clientAuth(io.netty.handler.ssl.ClientAuth.REQUIRE);
        } else if (truststorePath != null) {
            contextBuilder.trustManager(loadTrustManagerFactory());
            contextBuilder.clientAuth(io.netty.handler.ssl.ClientAuth.OPTIONAL);
        }

        return contextBuilder.build();
    }

    private KeyManagerFactory loadKeyManagerFactory() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keystoreType);
        try (InputStream keystoreStream = new FileInputStream(new File(keystorePath))) {
            keyStore.load(keystoreStream, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keyPassword.toCharArray());
        return kmf;
    }

    private TrustManagerFactory loadTrustManagerFactory() throws Exception {
        if (truststorePath == null) {
            return null;
        }

        KeyStore trustStore = KeyStore.getInstance(truststoreType);
        try (InputStream truststoreStream = new FileInputStream(new File(truststorePath))) {
            trustStore.load(truststoreStream, truststorePassword.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf;
    }

    // Getters and setters
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

    public SslProvider getSslProvider() { return sslProvider; }
    public void setSslProvider(SslProvider sslProvider) { this.sslProvider = sslProvider; }

    public String[] getEnabledProtocols() { return enabledProtocols; }
    public void setEnabledProtocols(String[] enabledProtocols) { this.enabledProtocols = enabledProtocols; }

    public String[] getEnabledCipherSuites() { return enabledCipherSuites; }
    public void setEnabledCipherSuites(String[] enabledCipherSuites) { this.enabledCipherSuites = enabledCipherSuites; }
}