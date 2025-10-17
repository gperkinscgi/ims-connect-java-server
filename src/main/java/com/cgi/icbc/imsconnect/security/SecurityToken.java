package com.cgi.icbc.imsconnect.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Represents a parsed RACF security token containing user authentication
 * and authorization information.
 */
public class SecurityToken {

    private final String userId;
    private final String primaryGroup;
    private final Set<String> groups;
    private final Set<String> authorities;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final String issuer;
    private final byte[] rawToken;
    private final TokenType tokenType;

    public enum TokenType {
        RACF_PASSWORD,
        RACF_PASSTICKET,
        KERBEROS_TICKET,
        CUSTOM_TOKEN,
        CERTIFICATE
    }

    private SecurityToken(Builder builder) {
        this.userId = builder.userId;
        this.primaryGroup = builder.primaryGroup;
        this.groups = Set.copyOf(builder.groups);
        this.authorities = Set.copyOf(builder.authorities);
        this.issuedAt = builder.issuedAt;
        this.expiresAt = builder.expiresAt;
        this.issuer = builder.issuer;
        this.rawToken = builder.rawToken != null ? builder.rawToken.clone() : null;
        this.tokenType = builder.tokenType;
    }

    // Getters
    public String getUserId() { return userId; }
    public String getPrimaryGroup() { return primaryGroup; }
    public Set<String> getGroups() { return groups; }
    public Set<String> getAuthorities() { return authorities; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getIssuer() { return issuer; }
    public byte[] getRawToken() { return rawToken != null ? rawToken.clone() : null; }
    public TokenType getTokenType() { return tokenType; }

    // Validation methods
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean hasAuthority(String authority) {
        return authorities.contains(authority);
    }

    public boolean isMemberOf(String group) {
        return groups.contains(group);
    }

    public boolean isValid() {
        return userId != null && !userId.trim().isEmpty() && !isExpired();
    }

    // Factory method for parsing token bytes
    public static SecurityToken fromBytes(byte[] tokenBytes, TokenType tokenType) {
        if (tokenBytes == null || tokenBytes.length == 0) {
            throw new SecurityTokenException("Token bytes cannot be null or empty");
        }

        return switch (tokenType) {
            case RACF_PASSWORD -> parseRacfPasswordToken(tokenBytes);
            case RACF_PASSTICKET -> parseRacfPassTicket(tokenBytes);
            case KERBEROS_TICKET -> parseKerberosTicket(tokenBytes);
            case CUSTOM_TOKEN -> parseCustomToken(tokenBytes);
            case CERTIFICATE -> parseCertificateToken(tokenBytes);
        };
    }

    private static SecurityToken parseRacfPasswordToken(byte[] tokenBytes) {
        // Parse RACF password authentication token
        // This is a simplified implementation - real RACF integration would
        // involve calling RACF APIs or security exits

        // For demo purposes, assume fixed format:
        // [8 bytes userId][8 bytes group][4 bytes expiration][remaining authorities]
        if (tokenBytes.length < 20) {
            throw new SecurityTokenException("Invalid RACF password token format");
        }

        try {
            String userId = new String(tokenBytes, 0, 8).trim();
            String group = new String(tokenBytes, 8, 8).trim();

            // Parse expiration (seconds since epoch)
            int expiration = java.nio.ByteBuffer.wrap(tokenBytes, 16, 4).getInt();
            Instant expiresAt = expiration > 0 ? Instant.ofEpochSecond(expiration) : null;

            return builder()
                .userId(userId)
                .primaryGroup(group)
                .group(group)
                .authority("IMS.TXN.*")  // Default authority
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .issuer("RACF")
                .tokenType(TokenType.RACF_PASSWORD)
                .rawToken(tokenBytes)
                .build();

        } catch (Exception e) {
            throw new SecurityTokenException("Failed to parse RACF password token", e);
        }
    }

    private static SecurityToken parseRacfPassTicket(byte[] tokenBytes) {
        // Parse RACF PassTicket - more complex format
        // PassTickets are time-sensitive, encrypted tokens
        try {
            // Simplified parsing - real implementation would decrypt and validate
            String userId = new String(tokenBytes, 0, Math.min(8, tokenBytes.length)).trim();

            return builder()
                .userId(userId)
                .authority("IMS.TXN.*")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)) // 10 minute expiry
                .issuer("RACF_PASSTICKET")
                .tokenType(TokenType.RACF_PASSTICKET)
                .rawToken(tokenBytes)
                .build();

        } catch (Exception e) {
            throw new SecurityTokenException("Failed to parse RACF PassTicket", e);
        }
    }

    private static SecurityToken parseKerberosTicket(byte[] tokenBytes) {
        // Parse Kerberos ticket - would integrate with Kerberos libraries
        throw new SecurityTokenException("Kerberos ticket parsing not yet implemented");
    }

    private static SecurityToken parseCustomToken(byte[] tokenBytes) {
        // Parse custom security token format
        throw new SecurityTokenException("Custom token parsing not yet implemented");
    }

    private static SecurityToken parseCertificateToken(byte[] tokenBytes) {
        // Parse X.509 certificate
        throw new SecurityTokenException("Certificate token parsing not yet implemented");
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String userId;
        private String primaryGroup;
        private Set<String> groups = new java.util.HashSet<>();
        private Set<String> authorities = new java.util.HashSet<>();
        private Instant issuedAt;
        private Instant expiresAt;
        private String issuer;
        private byte[] rawToken;
        private TokenType tokenType = TokenType.CUSTOM_TOKEN;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder primaryGroup(String primaryGroup) {
            this.primaryGroup = primaryGroup;
            return this;
        }

        public Builder group(String group) {
            this.groups.add(group);
            return this;
        }

        public Builder groups(Set<String> groups) {
            this.groups.addAll(groups);
            return this;
        }

        public Builder authority(String authority) {
            this.authorities.add(authority);
            return this;
        }

        public Builder authorities(Set<String> authorities) {
            this.authorities.addAll(authorities);
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder rawToken(byte[] rawToken) {
            this.rawToken = rawToken;
            return this;
        }

        public Builder tokenType(TokenType tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public SecurityToken build() {
            if (userId == null || userId.trim().isEmpty()) {
                throw new SecurityTokenException("User ID is required");
            }
            return new SecurityToken(this);
        }
    }

    @Override
    public String toString() {
        return String.format("SecurityToken{userId='%s', groups=%s, authorities=%s, type=%s, expires=%s}",
                userId, groups, authorities, tokenType, expiresAt);
    }
}