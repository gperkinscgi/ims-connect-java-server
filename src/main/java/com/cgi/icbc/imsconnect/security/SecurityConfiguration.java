package com.cgi.icbc.imsconnect.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for IMS Connect server management endpoints.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "ims-connect.security.enabled", havingValue = "true")
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // Public health endpoints (no auth required)
                .requestMatchers("/api/v1/live", "/api/v1/ready").permitAll()

                // Basic health endpoint (no auth required)
                .requestMatchers("/api/v1/health").permitAll()

                // Actuator endpoints - require authentication
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // Detailed monitoring endpoints - require authentication
                .requestMatchers("/api/v1/health/detailed", "/api/v1/metrics").hasRole("ADMIN")

                // All other requests require authentication
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> basic.realmName("IMS Connect Server"))
            .csrf(csrf -> csrf.disable()) // Disable CSRF for API endpoints
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                )
            );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // Get passwords from environment variables (loaded from .env in development)
        String adminPassword = System.getenv("ADMIN_PASSWORD");
        String monitorPassword = System.getenv("MONITOR_PASSWORD");

        // Fallback to defaults if not set (for development)
        adminPassword = adminPassword != null ? adminPassword : "admin";
        monitorPassword = monitorPassword != null ? monitorPassword : "monitor";

        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();

        UserDetails monitor = User.builder()
                .username("monitor")
                .password(passwordEncoder.encode(monitorPassword))
                .roles("MONITOR")
                .build();

        return new InMemoryUserDetailsManager(admin, monitor);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}