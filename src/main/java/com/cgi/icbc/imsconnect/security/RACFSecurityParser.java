package com.cgi.icbc.imsconnect.security;

import com.cgi.icbc.imsconnect.protocol.IRMHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses and validates RACF security tokens from IMS Connect headers.
 */
@Component
public class RACFSecurityParser {

    private static final Logger logger = LoggerFactory.getLogger(RACFSecurityParser.class);

    /**
     * Parse security context from IRM header containing RACF credentials.
     */
    public SecurityContext parseSecurityContext(IRMHeader header) {
        if (header == null) {
            throw new SecurityTokenException("IRM header cannot be null");
        }

        try {
            SecurityToken token = parseSecurityToken(header);

            return SecurityContext.builder()
                .securityToken(token)
                .clientId(header.getClientIdAsString())
                .transactionCode(header.getTransactionCodeAsString())
                .authenticationMethod(header.getAuthenticationMethodName())
                .build();

        } catch (Exception e) {
            logger.error("Failed to parse security context from IRM header", e);
            throw new SecurityTokenException("Security context parsing failed", e);
        }
    }

    private SecurityToken parseSecurityToken(IRMHeader header) {
        // Determine authentication method and parse accordingly
        if (header.isPasswordAuthentication()) {
            return parsePasswordAuthentication(header);
        } else if (header.isTokenAuthentication()) {
            return parseTokenAuthentication(header);
        } else {
            throw new SecurityTokenException("Unsupported authentication method: " +
                header.getAuthenticationMethodName());
        }
    }

    private SecurityToken parsePasswordAuthentication(IRMHeader header) {
        String userId = header.getUserIdAsString();
        String groupId = header.getGroupIdAsString();
        String password = header.getPasswordAsString();

        // Validate required fields
        if (userId == null || userId.trim().isEmpty()) {
            throw new SecurityTokenException("User ID is required for password authentication");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new SecurityTokenException("Password is required for password authentication");
        }

        logger.debug("Parsing password authentication for user: {}", userId);

        // Create basic security token for password authentication
        SecurityToken.Builder tokenBuilder = SecurityToken.builder()
            .userId(userId.trim())
            .tokenType(SecurityToken.TokenType.RACF_PASSWORD)
            .issuer("RACF")
            .issuedAt(java.time.Instant.now());

        // Add primary group if specified
        if (groupId != null && !groupId.trim().isEmpty()) {
            String group = groupId.trim();
            tokenBuilder.primaryGroup(group)
                       .group(group);
        }

        // Add default authorities based on user/group
        addDefaultAuthorities(tokenBuilder, userId.trim(), groupId);

        // Set token expiration (default 8 hours for password auth)
        if (header.getTokenExpiration() > 0) {
            tokenBuilder.expiresAt(java.time.Instant.ofEpochSecond(header.getTokenExpiration()));
        } else {
            tokenBuilder.expiresAt(java.time.Instant.now().plusSeconds(8 * 3600)); // 8 hours
        }

        return tokenBuilder.build();
    }

    private SecurityToken parseTokenAuthentication(IRMHeader header) {
        byte[] tokenBytes = header.getSecurityToken();

        if (tokenBytes == null || tokenBytes.length == 0) {
            throw new SecurityTokenException("Security token is required for token authentication");
        }

        SecurityToken.TokenType tokenType = determineTokenType(header.getAuthenticationMethod());

        logger.debug("Parsing {} token for user: {}", tokenType, header.getUserIdAsString());

        try {
            return SecurityToken.fromBytes(tokenBytes, tokenType);
        } catch (SecurityTokenException e) {
            logger.error("Failed to parse security token", e);
            throw e;
        }
    }

    private SecurityToken.TokenType determineTokenType(byte authMethod) {
        return switch (authMethod) {
            case IRMHeader.AUTH_TOKEN -> SecurityToken.TokenType.CUSTOM_TOKEN;
            case IRMHeader.AUTH_PASSTICKET -> SecurityToken.TokenType.RACF_PASSTICKET;
            case IRMHeader.AUTH_KERBEROS -> SecurityToken.TokenType.KERBEROS_TICKET;
            case IRMHeader.AUTH_CERTIFICATE -> SecurityToken.TokenType.CERTIFICATE;
            default -> throw new SecurityTokenException("Unknown token type: " + authMethod);
        };
    }

    private void addDefaultAuthorities(SecurityToken.Builder tokenBuilder, String userId, String groupId) {
        // Add basic IMS transaction authority
        tokenBuilder.authority("IMS.CONNECT");

        // Add authorities based on group membership
        if (groupId != null && !groupId.trim().isEmpty()) {
            String group = groupId.trim().toUpperCase();

            switch (group) {
                case "BANKOPER", "BANK_OPERATORS" -> {
                    tokenBuilder.authority("IMS.TXN.BALINQ")
                               .authority("IMS.TXN.TRANSFER")
                               .authority("IMS.TXN.DEPOSIT")
                               .authority("IMS.TXN.WITHDRAW");
                }
                case "BANKADMIN", "BANK_ADMIN" -> {
                    tokenBuilder.authority("IMS.TXN.*")
                               .authority("IMS.ADMIN.*");
                }
                case "READONLY", "READ_ONLY" -> {
                    tokenBuilder.authority("IMS.TXN.BALINQ")
                               .authority("IMS.TXN.INQUIRY");
                }
                default -> {
                    // Default minimal authorities
                    tokenBuilder.authority("IMS.TXN.BALINQ");
                }
            }
        }

        // Add user-specific authorities (could be from external source)
        addUserSpecificAuthorities(tokenBuilder, userId);
    }

    private void addUserSpecificAuthorities(SecurityToken.Builder tokenBuilder, String userId) {
        // In a real implementation, this would query RACF or a user database
        // For demo purposes, add some example authorities based on user ID patterns

        if (userId.startsWith("ADMIN")) {
            tokenBuilder.authority("IMS.ADMIN.*")
                       .authority("IMS.TXN.*");
        } else if (userId.startsWith("OPER")) {
            tokenBuilder.authority("IMS.TXN.BALINQ")
                       .authority("IMS.TXN.TRANSFER");
        } else if (userId.startsWith("READ")) {
            tokenBuilder.authority("IMS.TXN.BALINQ");
        }

        logger.debug("Added authorities for user: {}", userId);
    }

    /**
     * Validate that the security context is still valid.
     */
    public void validateSecurityContext(SecurityContext securityContext) {
        if (securityContext == null) {
            throw new SecurityTokenException("Security context cannot be null");
        }

        SecurityToken token = securityContext.getSecurityToken();
        if (token == null) {
            throw new SecurityTokenException("Security token cannot be null");
        }

        if (!token.isValid()) {
            throw new SecurityTokenException("Security token is invalid or expired");
        }

        if (token.isExpired()) {
            throw new SecurityTokenException("Security token has expired");
        }

        logger.debug("Security context validation passed for user: {}", token.getUserId());
    }
}