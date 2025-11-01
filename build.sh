#!/bin/bash

set -e

echo "Building all services..."

# Build with Maven
mvn clean package -DskipTests

echo "Building Docker images..."

# Build Eureka Server
docker build -t awsinfra-eureka-server:1.0.0 ./eureka-server

# Build Gateway Service (when ready)
# docker build -t awsinfra-gateway-service:1.0.0 ./gateway-service

# Build Auth Service
docker build -t awsinfra-auth-service:1.0.0 ./auth-service

# Build Backend Service
docker build -t awsinfra-backend-service:1.0.0 ./backend-service

echo "All services built successfully!"
