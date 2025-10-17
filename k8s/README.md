# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying the IMS Connect Java Server.

## Prerequisites

- Kubernetes cluster (1.20+)
- kubectl configured to connect to your cluster
- Docker registry access for the application image

## Quick Start

1. **Create the namespace:**
   ```bash
   kubectl apply -f namespace.yaml
   ```

2. **Apply secrets (update with actual values first):**
   ```bash
   # Edit secret.yaml with base64 encoded values
   kubectl apply -f secret.yaml
   ```

3. **Apply configuration:**
   ```bash
   kubectl apply -f configmap.yaml
   ```

4. **Deploy the application:**
   ```bash
   kubectl apply -f serviceaccount.yaml
   kubectl apply -f deployment.yaml
   kubectl apply -f service.yaml
   ```

5. **Enable auto-scaling (optional):**
   ```bash
   kubectl apply -f hpa.yaml
   ```

## Configuration

### Environment Variables

The application supports these environment variables in the deployment:

- `SPRING_PROFILES_ACTIVE`: Set to "kubernetes"
- `JAVA_OPTS`: JVM options for memory and GC tuning
- `SSL_*_PASSWORD`: SSL certificate passwords from secrets

### SSL Certificates

Create a secret with your SSL certificates:

```bash
kubectl create secret generic ims-connect-ssl-certs \
  --from-file=keystore.p12=/path/to/keystore.p12 \
  --from-file=truststore.p12=/path/to/truststore.p12 \
  -n ims-connect
```

### Mainframe Backend Configuration

Update the ConfigMap to point to your mainframe systems:

```yaml
backends:
  - name: "mainframe-1"
    host: "your-mainframe-host"
    port: 9999
    ssl-enabled: true
    weight: 100
```

## Monitoring

The deployment includes:

- Prometheus annotations for metrics scraping
- Health check endpoints
- Resource limits and requests
- Horizontal Pod Autoscaler

## Security

The deployment follows security best practices:

- Non-root user execution
- Read-only root filesystem
- Dropped capabilities
- Network policies (add network-policy.yaml if needed)

## Scaling

The HPA will automatically scale based on:
- CPU utilization (target: 70%)
- Memory utilization (target: 80%)
- Min replicas: 3
- Max replicas: 10

## Troubleshooting

1. **Check pod status:**
   ```bash
   kubectl get pods -n ims-connect
   ```

2. **View logs:**
   ```bash
   kubectl logs -f deployment/ims-connect-server -n ims-connect
   ```

3. **Check health:**
   ```bash
   kubectl port-forward svc/ims-connect-server-service 8080:8080 -n ims-connect
   curl http://localhost:8080/api/v1/health
   ```