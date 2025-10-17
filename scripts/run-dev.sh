#!/bin/bash

# Development run script for IMS Connect Java Server

set -e

echo "🚀 Starting IMS Connect Java Server in development mode..."

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "❌ .env file not found. Run ./scripts/dev-setup.sh first."
    exit 1
fi

# Load environment variables from .env for display
if command -v grep &> /dev/null; then
    echo "📋 Environment configuration:"
    echo "   SSL Keystore: $(grep SSL_KEYSTORE_PASSWORD .env 2>/dev/null | cut -d'=' -f1)=***"
    echo "   Mainframe Host 1: $(grep MAINFRAME_HOST_1 .env 2>/dev/null || echo 'MAINFRAME_HOST_1=localhost')"
    echo "   Log Level: $(grep LOG_LEVEL .env 2>/dev/null || echo 'LOG_LEVEL=DEBUG')"
fi

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven."
    exit 1
fi

echo ""
echo "Starting application with development profile..."
echo "Press Ctrl+C to stop"
echo ""

# Start the application with development profile
exec mvn spring-boot:run \
    -Dspring-boot.run.profiles=development \
    -Dspring-boot.run.jvmArguments="${JVM_OPTS:--Xmx1g -Xms512m}"