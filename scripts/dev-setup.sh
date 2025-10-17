#!/bin/bash

# Development setup script for IMS Connect Java Server

set -e

echo "🚀 Setting up IMS Connect Java Server for development..."

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "📝 Creating .env file from template..."
    cp .env.example .env
    echo "✅ Created .env file. Please update it with your actual values."
else
    echo "✅ .env file already exists"
fi

# Create SSL config directory
echo "📁 Creating SSL config directory..."
mkdir -p config/ssl

# Create logs directory
echo "📁 Creating logs directory..."
mkdir -p logs

# Generate development SSL certificates (self-signed)
if [ ! -f "config/ssl/dev-keystore.p12" ]; then
    echo "🔐 Generating development SSL certificates..."

    # Generate private key and certificate
    openssl req -x509 -newkey rsa:4096 -keyout config/ssl/dev-key.pem \
        -out config/ssl/dev-cert.pem -days 365 -nodes \
        -subj "/C=CA/ST=Ontario/L=Toronto/O=CGI/OU=ICBC/CN=localhost"

    # Create PKCS12 keystore
    openssl pkcs12 -export -out config/ssl/dev-keystore.p12 \
        -inkey config/ssl/dev-key.pem \
        -in config/ssl/dev-cert.pem \
        -name "ims-connect-dev" \
        -passout pass:devpassword

    # Create truststore (copy of keystore for development)
    cp config/ssl/dev-keystore.p12 config/ssl/dev-truststore.p12

    echo "✅ Generated development SSL certificates"
else
    echo "✅ Development SSL certificates already exist"
fi

# Check Java version
echo "☕ Checking Java version..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    echo "Java version: $JAVA_VERSION"

    # Check if Java 11 or higher
    if [[ "$JAVA_VERSION" < "11" ]]; then
        echo "⚠️  Warning: Java 11 or higher is recommended"
    fi
else
    echo "❌ Java not found. Please install Java 11 or higher."
    exit 1
fi

# Check Maven
echo "📦 Checking Maven..."
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -n 1)
    echo "$MVN_VERSION"
else
    echo "❌ Maven not found. Please install Maven 3.6 or higher."
    exit 1
fi

# Build the project
echo "🔨 Building the project..."
mvn clean compile

echo ""
echo "🎉 Development setup complete!"
echo ""
echo "Next steps:"
echo "1. Update .env file with your actual configuration"
echo "2. Start the application with: mvn spring-boot:run -Dspring-boot.run.profiles=development"
echo "3. Or use Docker: docker-compose up"
echo ""
echo "Endpoints:"
echo "- Application: http://localhost:9999"
echo "- Health Check: http://localhost:8080/api/v1/health"
echo "- Metrics: http://localhost:8080/actuator/prometheus"
echo "- H2 Console: http://localhost:8080/h2-console (development profile)"
echo ""
echo "Authentication (if security enabled):"
echo "- Admin: admin / [ADMIN_PASSWORD from .env]"
echo "- Monitor: monitor / [MONITOR_PASSWORD from .env]"