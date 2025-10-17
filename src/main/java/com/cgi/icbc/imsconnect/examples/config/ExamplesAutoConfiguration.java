package com.cgi.icbc.imsconnect.examples.config;

import com.cgi.icbc.imsconnect.examples.handlers.OTMABankingTransactionHandler;
import com.cgi.icbc.imsconnect.examples.handlers.SecureBankingTransactionHandler;
import com.cgi.icbc.imsconnect.examples.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for IMS Connect example components.
 * This configuration is separate from the main framework to clearly distinguish
 * between framework code and example implementations.
 *
 * To enable examples, set: ims-connect.examples.enabled=true
 */
@Configuration
@ConditionalOnProperty(name = "ims-connect.examples.enabled", havingValue = "true", matchIfMissing = false)
public class ExamplesAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ExamplesAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AccountService exampleAccountService() {
        logger.info("Creating example AccountService - NOTE: This is for demonstration only!");
        return new AccountService();
    }

    @Bean
    @ConditionalOnProperty(name = "ims-connect.otma.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public OTMABankingTransactionHandler exampleOTMABankingTransactionHandler(AccountService accountService) {
        logger.info("Creating example OTMA Banking Transaction Handler - NOTE: This is for demonstration only!");
        return new OTMABankingTransactionHandler(accountService);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecureBankingTransactionHandler exampleSecureBankingTransactionHandler(AccountService accountService) {
        logger.info("Creating example Secure Banking Transaction Handler - NOTE: This is for demonstration only!");
        return new SecureBankingTransactionHandler(accountService);
    }
}