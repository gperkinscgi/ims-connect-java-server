package com.cgi.icbc.imsconnect.security;

import java.time.Instant;
import java.util.Set;

/**
 * Security context for an IMS Connect transaction containing authentication
 * and authorization information.
 */
public class SecurityContext {

    private final SecurityToken securityToken;
    private final String clientId;
    private final String transactionCode;
    private final String authenticationMethod;
    private final Instant createdAt;
    private final String sourceIp;

    private SecurityContext(Builder builder) {
        this.securityToken = builder.securityToken;
        this.clientId = builder.clientId;
        this.transactionCode = builder.transactionCode;
        this.authenticationMethod = builder.authenticationMethod;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.sourceIp = builder.sourceIp;
    }

    // Getters
    public SecurityToken getSecurityToken() { return securityToken; }
    public String getClientId() { return clientId; }
    public String getTransactionCode() { return transactionCode; }
    public String getAuthenticationMethod() { return authenticationMethod; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSourceIp() { return sourceIp; }

    // Convenience methods delegating to security token
    public String getUserId() {
        return securityToken != null ? securityToken.getUserId() : null;
    }

    public Set<String> getGroups() {
        return securityToken != null ? securityToken.getGroups() : Set.of();
    }

    public Set<String> getAuthorities() {
        return securityToken != null ? securityToken.getAuthorities() : Set.of();
    }

    public String getPrimaryGroup() {
        return securityToken != null ? securityToken.getPrimaryGroup() : null;
    }

    public boolean isExpired() {
        return securityToken == null || securityToken.isExpired();
    }

    public boolean hasAuthority(String authority) {
        return securityToken != null && securityToken.hasAuthority(authority);
    }

    public boolean isMemberOf(String group) {
        return securityToken != null && securityToken.isMemberOf(group);
    }

    // Wildcard authority checking
    public boolean hasAuthorityPattern(String pattern) {
        if (securityToken == null) return false;

        Set<String> authorities = securityToken.getAuthorities();

        // Check for exact match first
        if (authorities.contains(pattern)) {
            return true;
        }

        // Check for wildcard matches
        for (String authority : authorities) {
            if (authority.endsWith("*")) {
                String prefix = authority.substring(0, authority.length() - 1);
                if (pattern.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Transaction-specific authorization checks
    public boolean canExecuteTransaction(String txnCode) {
        if (txnCode == null || txnCode.trim().isEmpty()) {
            return false;
        }

        String normalizedTxnCode = txnCode.trim().toUpperCase();

        // Check specific transaction authority
        if (hasAuthorityPattern("IMS.TXN." + normalizedTxnCode)) {
            return true;
        }

        // Check wildcard transaction authority
        if (hasAuthorityPattern("IMS.TXN.*")) {
            return true;
        }

        // Check admin authority
        return hasAuthorityPattern("IMS.ADMIN.*");
    }

    public boolean canAccessResource(String resource) {
        if (resource == null || resource.trim().isEmpty()) {
            return false;
        }

        return hasAuthorityPattern(resource.trim());
    }

    // Group-based authorization
    public boolean isOperator() {
        return isMemberOf("BANKOPER") || isMemberOf("BANK_OPERATORS") || isMemberOf("OPERATORS");
    }

    public boolean isAdministrator() {
        return isMemberOf("BANKADMIN") || isMemberOf("BANK_ADMIN") || isMemberOf("ADMINS") ||
               hasAuthorityPattern("IMS.ADMIN.*");
    }

    public boolean isReadOnly() {
        return isMemberOf("READONLY") || isMemberOf("READ_ONLY");
    }

    // Validation
    public boolean isValid() {
        return securityToken != null && securityToken.isValid() && !isExpired();
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SecurityToken securityToken;
        private String clientId;
        private String transactionCode;
        private String authenticationMethod;
        private Instant createdAt;
        private String sourceIp;

        public Builder securityToken(SecurityToken securityToken) {
            this.securityToken = securityToken;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder transactionCode(String transactionCode) {
            this.transactionCode = transactionCode;
            return this;
        }

        public Builder authenticationMethod(String authenticationMethod) {
            this.authenticationMethod = authenticationMethod;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder sourceIp(String sourceIp) {
            this.sourceIp = sourceIp;
            return this;
        }

        public SecurityContext build() {
            if (securityToken == null) {
                throw new SecurityTokenException("Security token is required");
            }
            return new SecurityContext(this);
        }
    }

    @Override
    public String toString() {
        return String.format("SecurityContext{userId='%s', clientId='%s', txnCode='%s', authMethod='%s', groups=%s}",
                getUserId(), clientId, transactionCode, authenticationMethod, getGroups());
    }
}