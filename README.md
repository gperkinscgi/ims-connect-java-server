# IMS Connect Java Server

A high-performance, enterprise-grade Java server implementation for IBM IMS Connect protocol communication. This server acts as a gateway between modern applications and IBM mainframe IMS systems, providing secure, scalable, and monitored connectivity.

## Features

- **High Performance**: Built on Netty NIO framework for maximum throughput
- **Enterprise Security**: SSL/TLS with mutual authentication, audit logging
- **Connection Pooling**: Intelligent load balancing across multiple mainframe backends
- **Monitoring**: Prometheus metrics, health checks, and structured logging
- **Spring Boot Integration**: Enterprise-ready with auto-configuration
- **Kubernetes Ready**: Production deployment manifests included
- **Protocol Compliance**: Full IMS Connect protocol support with HWSSMPL1 exit routine

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Client Apps   │───▶│  IMS Connect     │───▶│   Mainframe     │
│   (COBOL, Java, │    │  Java Server     │    │   IMS Systems   │
│    .NET, etc.)  │    │                  │    │                 │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌──────────────────┐
                       │   Monitoring     │
                       │ (Prometheus,     │
                       │  Grafana, Logs)  │
                       └──────────────────┘
```

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (optional)
- Kubernetes cluster (optional)

### Development Setup

```bash
git clone https://github.com/gperkinscgi/ims-connect-java-server.git
cd ims-connect-java-server

# Automated development setup
./scripts/dev-setup.sh

# Edit your configuration
vi .env

# Start development server
./scripts/run-dev.sh
```

The setup script will:
- Create `.env` file from template
- Generate self-signed SSL certificates for development
- Verify Java and Maven requirements
- Build the project

### Manual Setup

```bash
# Copy environment template
cp .env.example .env

# Build the project
mvn clean package

# Run with development profile
mvn spring-boot:run -Dspring-boot.run.profiles=development

# Or using JAR
java -jar target/ims-connect-java-server-1.0.0.jar --spring.profiles.active=development
```

### Docker Deployment

```bash
# Build image
docker build -t ims-connect-server .

# Run with Docker Compose (includes monitoring)
docker-compose up -d

# View logs
docker-compose logs -f ims-connect-server
```

### Kubernetes Deployment

```bash
# Deploy to Kubernetes
kubectl apply -f k8s/

# Check status
kubectl get pods -n ims-connect

# View logs
kubectl logs -f deployment/ims-connect-server -n ims-connect
```

## Configuration

### Development Configuration (.env file)

For development, all secrets and configuration are managed through a `.env` file:

```bash
# SSL Certificate Passwords
SSL_KEYSTORE_PASSWORD=changeme
SSL_KEY_PASSWORD=changeme
SSL_TRUSTSTORE_PASSWORD=changeme

# Mainframe Backend Configuration
MAINFRAME_HOST_1=localhost
MAINFRAME_PORT_1=9999
MAINFRAME_HOST_2=localhost
MAINFRAME_PORT_2=9998

# Security Configuration
ADMIN_PASSWORD=admin123
MONITOR_PASSWORD=monitor123

# Application Settings
LOG_LEVEL=DEBUG
JVM_OPTS=-Xmx1g -Xms512m
```

**Security Features:**
- ✅ `.env` files are Git-ignored
- ✅ Only loaded in development profiles
- ✅ Fallback to safe defaults if missing
- ✅ Template provided (`.env.example`)

### Application Properties

The server supports multiple configuration profiles:

**Development Profile (`application-development.yml`):**
```yaml
ims-connect:
  server:
    port: 9999
    datastore-name: "DEV-IMS"
    worker-threads: 4            # Reduced for development

  backends:
    - name: "dev-mainframe-1"
      host: "${MAINFRAME_HOST_1:localhost}"
      port: ${MAINFRAME_PORT_1:9999}
      ssl-enabled: false         # Disabled for local dev

  security:
    ssl:
      enabled: false             # SSL disabled for local development
      keystore-password: "${SSL_KEYSTORE_PASSWORD:devpassword}"
```

**Production Profile (`application.yml`):**
```yaml
ims-connect:
  server:
    port: 9999
    boss-threads: 2
    worker-threads: 8

  security:
    enabled: true
    ssl:
      enabled: true
      keystore-path: "config/ssl/server.p12"
      keystore-password: "${SSL_KEYSTORE_PASSWORD}"
```

### Environment Variables Reference

| Variable | Description | Development Default |
|----------|-------------|-------------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `development` |
| `SSL_KEYSTORE_PASSWORD` | SSL keystore password | `devpassword` |
| `SSL_KEY_PASSWORD` | SSL key password | `devpassword` |
| `SSL_TRUSTSTORE_PASSWORD` | SSL truststore password | `devpassword` |
| `MAINFRAME_HOST_1` | Primary mainframe host | `localhost` |
| `MAINFRAME_PORT_1` | Primary mainframe port | `9999` |
| `MAINFRAME_HOST_2` | Secondary mainframe host | `localhost` |
| `MAINFRAME_PORT_2` | Secondary mainframe port | `9998` |
| `ADMIN_PASSWORD` | Admin user password | `admin` |
| `MONITOR_PASSWORD` | Monitor user password | `monitor` |
| `LOG_LEVEL` | Application log level | `DEBUG` |
| `JVM_OPTS` | JVM options | `-Xmx1g -Xms512m` |

## Sample Service Implementation

Here's how to implement a custom IMS transaction handler that processes incoming IMS Connect protocol messages:

### 1. Custom Transaction Handler

```java
@Component
public class BankingTransactionHandler implements IMSTransactionHandler {

    private static final Logger logger = LoggerFactory.getLogger(BankingTransactionHandler.class);
    private final AuditLogger auditLogger;
    private final AccountService accountService;

    @Autowired
    public BankingTransactionHandler(AuditLogger auditLogger, AccountService accountService) {
        this.auditLogger = auditLogger;
        this.accountService = accountService;
    }

    @Override
    public boolean canHandle(String transactionCode) {
        // Handle banking transaction codes
        return "BALINQ".equals(transactionCode) || "TRANSFER".equals(transactionCode);
    }

    @Override
    public IMSResponse handleTransaction(IRMHeader header, String messageData) {
        String clientId = header.getClientId();
        String transactionCode = header.getTransactionCode();

        try {
            // Convert EBCDIC message from mainframe client to ASCII
            String asciiMessage = EbcdicConverter.ebcdicToAscii(messageData);

            // Route to appropriate handler based on transaction code
            IMSResponse response = switch (transactionCode) {
                case "BALINQ" -> handleBalanceInquiry(header, asciiMessage);
                case "TRANSFER" -> handleFundsTransfer(header, asciiMessage);
                default -> createErrorResponse("Unknown transaction code: " + transactionCode);
            };

            // Audit successful transaction
            auditLogger.logTransaction("TRANSACTION_PROCESSED", clientId,
                transactionCode, null, true, "Message length: " + messageData.length());

            return response;

        } catch (Exception e) {
            logger.error("Failed to process transaction {} for client {}", transactionCode, clientId, e);
            auditLogger.logTransaction("TRANSACTION_FAILED", clientId,
                transactionCode, null, false, "Error: " + e.getMessage());
            return createErrorResponse("Transaction processing failed: " + e.getMessage());
        }
    }

    private IMSResponse handleBalanceInquiry(IRMHeader header, String messageData) {
        // Parse incoming message (fixed-format from mainframe client)
        String accountNumber = messageData.substring(8, 24).trim();   // Account number
        String customerNumber = messageData.substring(24, 36).trim(); // Customer number

        logger.info("Processing balance inquiry for account: {}, customer: {}",
                   accountNumber, customerNumber);

        // Call business logic
        AccountBalance balance = accountService.getAccountBalance(accountNumber);

        // Build response message in fixed format expected by mainframe client
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "BALINQ"));                    // Echo transaction code
        response.append(String.format("%-4s", "0000"));                      // Response code (0000 = success)
        response.append(String.format("%-16s", accountNumber));              // Account number
        response.append(String.format("%015d", balance.getAmountCents()));   // Balance in cents (15 digits)
        response.append(String.format("%-3s", balance.getCurrencyCode()));   // Currency code
        response.append(String.format("%-1s", balance.getAccountStatus()));  // Account status
        response.append(String.format("%-50s", " "));                        // Reserved space

        // Convert response back to EBCDIC for mainframe client
        String ebcdicResponse = EbcdicConverter.asciiToEbcdic(response.toString());

        return IMSResponse.success(ebcdicResponse);
    }

    private IMSResponse handleFundsTransfer(IRMHeader header, String messageData) {
        // Parse transfer request
        String fromAccount = messageData.substring(8, 24).trim();
        String toAccount = messageData.substring(24, 40).trim();
        long amountCents = Long.parseLong(messageData.substring(40, 55).trim());

        logger.info("Processing funds transfer: {} -> {}, amount: {}",
                   fromAccount, toAccount, amountCents);

        // Call business logic
        TransferResult result = accountService.transferFunds(fromAccount, toAccount, amountCents);

        // Build response
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "TRANSFER"));
        response.append(String.format("%-4s", result.isSuccess() ? "0000" : "1001"));
        response.append(String.format("%-16s", fromAccount));
        response.append(String.format("%-16s", toAccount));
        response.append(String.format("%-20s", result.getTransactionId()));
        response.append(String.format("%-50s", result.getMessage()));

        String ebcdicResponse = EbcdicConverter.asciiToEbcdic(response.toString());
        return result.isSuccess() ?
            IMSResponse.success(ebcdicResponse) :
            IMSResponse.error(ebcdicResponse);
    }

    private IMSResponse createErrorResponse(String errorMessage) {
        StringBuilder response = new StringBuilder();
        response.append(String.format("%-8s", "ERROR"));
        response.append(String.format("%-4s", "9999"));  // General error code
        response.append(String.format("%-100s", errorMessage));

        String ebcdicResponse = EbcdicConverter.asciiToEbcdic(response.toString());
        return IMSResponse.error(ebcdicResponse);
    }
}

### 2. Business Service Layer

```java
@Service
public class AccountService {

    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);

    // Mock data - in real implementation, this would call database/external services
    private final Map<String, AccountBalance> accounts = new HashMap<>();

    @PostConstruct
    public void initializeTestData() {
        accounts.put("1234567890123456", new AccountBalance(
            "1234567890123456", 150000L, "CAD", "A")); // $1,500.00 CAD
        accounts.put("9876543210987654", new AccountBalance(
            "9876543210987654", 250000L, "USD", "A")); // $2,500.00 USD
    }

    public AccountBalance getAccountBalance(String accountNumber) {
        AccountBalance balance = accounts.get(accountNumber);
        if (balance == null) {
            throw new AccountNotFoundException("Account not found: " + accountNumber);
        }
        return balance;
    }

    public TransferResult transferFunds(String fromAccount, String toAccount, long amountCents) {
        try {
            AccountBalance fromBalance = getAccountBalance(fromAccount);
            AccountBalance toBalance = getAccountBalance(toAccount);

            if (fromBalance.getAmountCents() < amountCents) {
                return TransferResult.failure("Insufficient funds");
            }

            // Perform transfer
            fromBalance.setAmountCents(fromBalance.getAmountCents() - amountCents);
            toBalance.setAmountCents(toBalance.getAmountCents() + amountCents);

            String transactionId = "TXN" + System.currentTimeMillis();
            logger.info("Transfer completed: {} -> {}, amount: {}, txnId: {}",
                       fromAccount, toAccount, amountCents, transactionId);

            return TransferResult.success(transactionId, "Transfer completed successfully");

        } catch (Exception e) {
            logger.error("Transfer failed: {} -> {}", fromAccount, toAccount, e);
            return TransferResult.failure("Transfer failed: " + e.getMessage());
        }
    }
}
```

### 3. Data Models

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountBalance {
    private String accountNumber;
    private Long amountCents;      // Store money as cents to avoid decimal precision issues
    private String currencyCode;   // "CAD", "USD", etc.
    private String accountStatus;  // "A" = Active, "C" = Closed, "F" = Frozen

    public BigDecimal getAmount() {
        return BigDecimal.valueOf(amountCents).divide(BigDecimal.valueOf(100));
    }
}

@Data
@AllArgsConstructor
public class TransferResult {
    private boolean success;
    private String transactionId;
    private String message;

    public static TransferResult success(String transactionId, String message) {
        return new TransferResult(true, transactionId, message);
    }

    public static TransferResult failure(String message) {
        return new TransferResult(false, null, message);
    }
}

// Custom exceptions
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
```

### 4. Handler Registration

```java
@Configuration
public class TransactionHandlerConfiguration {

    @Bean
    public IMSTransactionHandlerRegistry handlerRegistry(
            List<IMSTransactionHandler> handlers,
            DefaultIMSServerHandler serverHandler) {

        IMSTransactionHandlerRegistry registry = new IMSTransactionHandlerRegistry();

        // Register all transaction handlers
        handlers.forEach(registry::registerHandler);

        // Set the registry in the server handler
        serverHandler.setTransactionHandlerRegistry(registry);

        return registry;
    }
}
```

### 5. Testing the Server

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "ims-connect.backends[0].host=localhost",
    "ims-connect.backends[0].port=9999",
    "ims-connect.security.enabled=false"
})
class BankingTransactionHandlerTest {

    @Autowired
    private BankingTransactionHandler handler;

    @Test
    void testBalanceInquiryHandler() {
        // Arrange - create IRM header
        IRMHeader header = IRMHeader.builder()
            .transactionCode("BALINQ")
            .clientId("TEST_CLIENT")
            .architecture(IRMHeader.IRMARCH1)
            .flags(IRMHeader.IRMSYNCH)
            .build();

        // Create message in EBCDIC format (as it would come from mainframe client)
        String message = String.format("%-8s%-16s%-12s%-50s",
            "BALINQ", "1234567890123456", "CUST123", " ");
        String ebcdicMessage = EbcdicConverter.asciiToEbcdic(message);

        // Act
        IMSResponse response = handler.handleTransaction(header, ebcdicMessage);

        // Assert
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotEmpty();

        // Verify response format
        String asciiResponse = EbcdicConverter.ebcdicToAscii(response.getData());
        assertThat(asciiResponse.substring(0, 8).trim()).isEqualTo("BALINQ");
        assertThat(asciiResponse.substring(8, 12).trim()).isEqualTo("0000"); // Success code
    }

    @Test
    void testCanHandleTransactionCodes() {
        assertThat(handler.canHandle("BALINQ")).isTrue();
        assertThat(handler.canHandle("TRANSFER")).isTrue();
        assertThat(handler.canHandle("UNKNOWN")).isFalse();
    }
}
```

### 6. Client Connection Example

Here's how a mainframe COBOL program would connect to this server:

```cobol
      * COBOL client connecting to IMS Connect Java Server
       IDENTIFICATION DIVISION.
       PROGRAM-ID. ACCOUNT-INQUIRY.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-IMS-HEADER.
           05  WS-IRM-LENGTH        PIC 9(8) COMP.
           05  WS-IRM-ARCH          PIC X(4) VALUE 'IRM1'.
           05  WS-IRM-FLAGS         PIC 9(4) COMP VALUE 1.
           05  WS-TXN-CODE          PIC X(8) VALUE 'BALINQ  '.
           05  WS-CLIENT-ID         PIC X(8) VALUE 'COBOL001'.

       01  WS-BALANCE-REQUEST.
           05  WS-REQ-TXN-CODE      PIC X(8) VALUE 'BALINQ  '.
           05  WS-REQ-ACCOUNT       PIC X(16).
           05  WS-REQ-CUSTOMER      PIC X(12).
           05  FILLER               PIC X(50) VALUE SPACES.

       01  WS-BALANCE-RESPONSE.
           05  WS-RESP-TXN-CODE     PIC X(8).
           05  WS-RESP-CODE         PIC X(4).
           05  WS-RESP-ACCOUNT      PIC X(16).
           05  WS-RESP-BALANCE      PIC 9(15).
           05  WS-RESP-CURRENCY     PIC X(3).
           05  WS-RESP-STATUS       PIC X(1).
           05  FILLER               PIC X(50).

       PROCEDURE DIVISION.
       MAIN-LOGIC.
           MOVE '1234567890123456' TO WS-REQ-ACCOUNT
           MOVE 'CUST123     '     TO WS-REQ-CUSTOMER

      *    Connect to IMS Connect Java Server
           CALL 'HWSSMPL1' USING
               WS-IMS-HEADER
               WS-BALANCE-REQUEST
               WS-BALANCE-RESPONSE

           IF WS-RESP-CODE = '0000'
               DISPLAY 'Account Balance: ' WS-RESP-BALANCE
               DISPLAY 'Currency: ' WS-RESP-CURRENCY
           ELSE
               DISPLAY 'Error: ' WS-RESP-CODE
           END-IF

           STOP RUN.
```

## How It Works

This IMS Connect Java Server acts as a **gateway** that:

1. **Receives** IMS Connect protocol messages from mainframe clients (COBOL programs, etc.)
2. **Parses** the EBCDIC-encoded messages and IRM headers
3. **Routes** transactions to appropriate business logic handlers based on transaction codes
4. **Processes** the business logic (account lookups, transfers, etc.)
5. **Returns** properly formatted EBCDIC responses back to the mainframe clients

### Message Flow

```
Mainframe Client (COBOL)
    ↓ (EBCDIC message via IMS Connect protocol)
IMS Connect Java Server
    ↓ (Parse & Convert to ASCII)
Transaction Handler (Java business logic)
    ↓ (Call business services)
Database/External Systems
    ↓ (Return results)
Transaction Handler
    ↓ (Format & Convert to EBCDIC)
IMS Connect Java Server
    ↓ (IMS Connect protocol response)
Mainframe Client receives response
```

## Monitoring and Metrics

### Health Checks

```bash
# Basic health check
curl http://localhost:8080/api/v1/health

# Detailed health check (requires authentication)
curl -u admin:admin http://localhost:8080/api/v1/health/detailed

# Readiness check
curl http://localhost:8080/api/v1/ready

# Liveness check
curl http://localhost:8080/api/v1/live
```

### Prometheus Metrics

Key metrics exposed:

- `ims_connect_active_connections` - Current active connections
- `ims_connect_pool_size` - Connection pool size by backend
- `ims_connect_transactions_total` - Total transactions processed
- `ims_connect_transaction_duration_seconds` - Transaction response times
- `ims_connect_backend_health_status` - Backend health status

### Logging

Structured JSON logs are written to multiple files:

- `logs/ims-connect-server.log` - Application logs
- `logs/ims-connect-audit.log` - Audit trail (JSON format)
- `logs/ims-connect-metrics.log` - Performance metrics
- `logs/ims-connect-errors.log` - Error-only logs

## Security

### SSL/TLS Configuration

```yaml
ims-connect:
  security:
    ssl:
      enabled: true
      keystore-path: "config/ssl/server.p12"
      keystore-password: "${SSL_KEYSTORE_PASSWORD}"
      truststore-path: "config/ssl/truststore.p12"  # For mutual auth
      truststore-password: "${SSL_TRUSTSTORE_PASSWORD}"
      enabled-protocols: ["TLSv1.3", "TLSv1.2"]
```

### Authentication

HTTP Basic authentication for management endpoints:
- Username: `admin` / Password: `admin` (change in production)
- Username: `monitor` / Password: `monitor`

## Troubleshooting

### Development Issues

1. **Missing .env file**
   ```bash
   # Run setup script
   ./scripts/dev-setup.sh

   # Or manually copy template
   cp .env.example .env
   ```

2. **Environment variables not loading**
   ```bash
   # Check if development profile is active
   grep "Active profiles" logs/ims-connect-server.log

   # Verify .env file exists and has correct values
   cat .env | grep -v PASSWORD

   # Start with explicit development profile
   mvn spring-boot:run -Dspring-boot.run.profiles=development
   ```

3. **SSL Certificate Issues (Development)**
   ```bash
   # Regenerate development certificates
   rm -rf config/ssl/dev-*.p12
   ./scripts/dev-setup.sh

   # Verify certificate
   keytool -list -keystore config/ssl/dev-keystore.p12 -storepass devpassword
   ```

4. **Authentication Failed**
   ```bash
   # Check if passwords are loaded from .env
   grep ADMIN_PASSWORD .env

   # Test with default credentials
   curl -u admin:admin123 http://localhost:8080/actuator/health
   ```

### Production Issues

1. **Connection Refused**
   ```bash
   # Check if backends are reachable
   telnet mainframe-host 9999

   # Verify configuration
   kubectl get configmap ims-connect-config -o yaml
   ```

2. **SSL Handshake Failures**
   ```bash
   # Verify certificates
   openssl s_client -connect mainframe-host:9999 -cert client.pem

   # Check Java keystore
   keytool -list -keystore config/ssl/keystore.p12
   ```

3. **High Memory Usage**
   ```bash
   # Check JVM settings
   kubectl describe pod ims-connect-server-xxx

   # Monitor heap usage
   curl http://localhost:8080/actuator/metrics/jvm.memory.used
   ```

### Log Analysis

```bash
# View audit logs
tail -f logs/ims-connect-audit.log | jq .

# Search for errors
grep "ERROR" logs/ims-connect-server.log

# Monitor transactions
grep "TRANSACTION" logs/ims-connect-audit.log | jq '.eventType, .success, .transactionCode'
```

## Performance Tuning

### Development Performance

For development, performance settings are optimized for quick startup:

```bash
# In .env file
JVM_OPTS=-Xmx1g -Xms512m -XX:+UseG1GC

# Or via environment variable
export JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC"
./scripts/run-dev.sh
```

### Production JVM Tuning

```bash
export JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Netty Tuning

```yaml
ims-connect:
  server:
    boss-threads: 2      # Number of CPU cores
    worker-threads: 16   # 2x CPU cores
    backlog-size: 2048   # Increase for high load
```

### Connection Pool Tuning

```yaml
ims-connect:
  pool:
    min-connections-per-backend: 5
    max-connections-per-backend: 50
    connection-timeout-ms: 3000
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For enterprise support and consulting:
- Email: support@cgi.com
- Documentation: [Wiki](https://github.com/gperkinscgi/ims-connect-java-server/wiki)
- Issues: [GitHub Issues](https://github.com/gperkinscgi/ims-connect-java-server/issues)