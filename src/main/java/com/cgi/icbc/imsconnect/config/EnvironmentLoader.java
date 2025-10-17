package com.cgi.icbc.imsconnect.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads environment variables from .env file for development.
 */
public class EnvironmentLoader implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentLoader.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();

        // Only load .env file in development profile
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDevelopment = activeProfiles.length == 0 ||
                               java.util.Arrays.asList(activeProfiles).contains("development") ||
                               java.util.Arrays.asList(activeProfiles).contains("dev");

        if (isDevelopment) {
            loadDotEnvFile(environment);
        } else {
            logger.info("Skipping .env file loading for profiles: {}", java.util.Arrays.toString(activeProfiles));
        }
    }

    private void loadDotEnvFile(ConfigurableEnvironment environment) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .filename(".env")
                    .ignoreIfMissing()
                    .load();

            Map<String, Object> envMap = new HashMap<>();

            // SSL Configuration
            addIfPresent(dotenv, envMap, "SSL_KEYSTORE_PASSWORD");
            addIfPresent(dotenv, envMap, "SSL_KEY_PASSWORD");
            addIfPresent(dotenv, envMap, "SSL_TRUSTSTORE_PASSWORD");

            // Mainframe Configuration
            addIfPresent(dotenv, envMap, "MAINFRAME_HOST_1");
            addIfPresent(dotenv, envMap, "MAINFRAME_PORT_1");
            addIfPresent(dotenv, envMap, "MAINFRAME_HOST_2");
            addIfPresent(dotenv, envMap, "MAINFRAME_PORT_2");

            // Database Configuration
            addIfPresent(dotenv, envMap, "DB_USERNAME");
            addIfPresent(dotenv, envMap, "DB_PASSWORD");
            addIfPresent(dotenv, envMap, "DB_URL");

            // Security Configuration
            addIfPresent(dotenv, envMap, "ADMIN_PASSWORD");
            addIfPresent(dotenv, envMap, "MONITOR_PASSWORD");

            // Application Settings
            addIfPresent(dotenv, envMap, "LOG_LEVEL");
            addIfPresent(dotenv, envMap, "JVM_OPTS");

            // External Services
            addIfPresent(dotenv, envMap, "PROMETHEUS_URL");
            addIfPresent(dotenv, envMap, "GRAFANA_URL");

            if (!envMap.isEmpty()) {
                environment.getPropertySources().addFirst(
                        new MapPropertySource("dotenv", envMap));
                logger.info("Loaded {} environment variables from .env file", envMap.size());
            } else {
                logger.debug("No .env file found or no variables loaded");
            }

        } catch (Exception e) {
            logger.warn("Failed to load .env file: {}", e.getMessage());
        }
    }

    private void addIfPresent(Dotenv dotenv, Map<String, Object> envMap, String key) {
        String value = dotenv.get(key);
        if (value != null && !value.trim().isEmpty()) {
            envMap.put(key, value);
            logger.debug("Loaded environment variable: {}", key);
        }
    }
}