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

Here's how to implement a typical banking transaction service using the IMS Connect server:

### 1. Account Balance Inquiry Service

```java
@Service
@Component
public class AccountBalanceService {

    private static final Logger logger = LoggerFactory.getLogger(AccountBalanceService.class);
    private final AuditLogger auditLogger;

    @Autowired
    public AccountBalanceService(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * Inquire account balance from mainframe IMS system
     */
    public AccountBalanceResponse getAccountBalance(String accountNumber, String clientId) {
        try {
            // Build IMS transaction request
            AccountBalanceRequest request = AccountBalanceRequest.builder()
                .accountNumber(accountNumber)
                .transactionCode("BALINQ")
                .clientId(clientId)
                .build();

            // Send to mainframe via IMS Connect
            IMSResponse response = sendIMSTransaction(request);

            // Parse response
            AccountBalanceResponse balanceResponse = parseBalanceResponse(response);

            // Audit successful transaction
            auditLogger.logTransaction("ACCOUNT_BALANCE_INQUIRY", clientId,
                "BALINQ", null, true, "Account: " + accountNumber);

            return balanceResponse;

        } catch (Exception e) {
            logger.error("Failed to get account balance for account: {}", accountNumber, e);
            auditLogger.logTransaction("ACCOUNT_BALANCE_INQUIRY", clientId,
                "BALINQ", null, false, "Error: " + e.getMessage());
            throw new AccountServiceException("Balance inquiry failed", e);
        }
    }

    private IMSResponse sendIMSTransaction(AccountBalanceRequest request) {
        // Convert request to EBCDIC format for mainframe
        String ebcdicRequest = buildEbcdicMessage(request);

        // Create IRM header
        IRMHeader header = IRMHeader.builder()
            .length(ebcdicRequest.length() + 64) // Message + header length
            .architecture(IRMHeader.IRMARCH1)
            .flags(IRMHeader.IRMSYNCH)
            .transactionCode(request.getTransactionCode())
            .clientId(request.getClientId())
            .build();

        // This would integrate with your IMS Connect client
        // For demo purposes, showing structure
        return imsConnectClient.sendSyncTransaction(header, ebcdicRequest);
    }

    private String buildEbcdicMessage(AccountBalanceRequest request) {
        StringBuilder message = new StringBuilder();

        // Standard IMS message format
        message.append(String.format("%-8s", request.getTransactionCode()));  // Transaction code
        message.append(String.format("%-16s", request.getAccountNumber()));    // Account number
        message.append(String.format("%-8s", "INQUIRY"));                      // Operation type
        message.append(String.format("%-32s", " "));                           // Reserved space

        // Convert to EBCDIC for mainframe processing
        return EbcdicConverter.asciiToEbcdic(message.toString());
    }

    private AccountBalanceResponse parseBalanceResponse(IMSResponse response) {
        // Convert EBCDIC response back to ASCII
        String asciiResponse = EbcdicConverter.ebcdicToAscii(response.getData());

        // Parse fixed-format response (typical mainframe format)
        String responseCode = asciiResponse.substring(0, 4).trim();
        String balance = asciiResponse.substring(4, 20).trim();
        String accountStatus = asciiResponse.substring(20, 28).trim();
        String currency = asciiResponse.substring(28, 31).trim();

        if (!"0000".equals(responseCode)) {
            throw new AccountServiceException("Mainframe error: " + responseCode);
        }

        return AccountBalanceResponse.builder()
            .accountNumber(request.getAccountNumber())
            .balance(new BigDecimal(balance))
            .currency(currency)
            .accountStatus(accountStatus)
            .responseTime(Instant.now())
            .build();
    }
}
```

### 2. Request/Response DTOs

```java
@Data
@Builder
public class AccountBalanceRequest {
    private String accountNumber;
    private String transactionCode;
    private String clientId;
    private Instant requestTime;
}

@Data
@Builder
public class AccountBalanceResponse {
    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    private String accountStatus;
    private Instant responseTime;
}
```

### 3. REST Controller for External Access

```java
@RestController
@RequestMapping("/api/v1/banking")
@Validated
public class BankingController {

    private final AccountBalanceService accountBalanceService;
    private final AuditLogger auditLogger;

    @Autowired
    public BankingController(AccountBalanceService accountBalanceService,
                           AuditLogger auditLogger) {
        this.accountBalanceService = accountBalanceService;
        this.auditLogger = auditLogger;
    }

    @GetMapping("/accounts/{accountNumber}/balance")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance(
            @PathVariable @Pattern(regexp = "\\d{10,16}") String accountNumber,
            @RequestHeader("Client-Id") String clientId,
            HttpServletRequest request) {

        try {
            // Log API access
            auditLogger.logSecurityEvent("API_ACCESS", clientId, null,
                getClientIp(request), true, "Balance inquiry API");

            AccountBalanceResponse response = accountBalanceService
                .getAccountBalance(accountNumber, clientId);

            return ResponseEntity.ok(response);

        } catch (AccountServiceException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### 4. Integration Test Example

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "ims-connect.backends[0].host=localhost",
    "ims-connect.backends[0].port=9999",
    "ims-connect.security.enabled=false"
})
class AccountBalanceServiceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountBalanceService accountBalanceService;

    @Test
    void testAccountBalanceInquiry() {
        // Arrange
        String accountNumber = "1234567890123456";
        String clientId = "TEST_CLIENT";

        // Act
        AccountBalanceResponse response = accountBalanceService
            .getAccountBalance(accountNumber, clientId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(response.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void testBalanceApiEndpoint() {
        // Arrange
        String accountNumber = "1234567890123456";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Client-Id", "TEST_CLIENT");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Act
        ResponseEntity<AccountBalanceResponse> response = restTemplate.exchange(
            "/api/v1/banking/accounts/{accountNumber}/balance",
            HttpMethod.GET,
            entity,
            AccountBalanceResponse.class,
            accountNumber
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
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