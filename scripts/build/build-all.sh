#!/bin/bash

set -e

# Use PROJECT_NAME if set, otherwise default to "awsinfra"
PROJECT_NAME=${PROJECT_NAME:-cloudinfra}

echo "Building all services for project: ${PROJECT_NAME}..."

# Build with Maven
mvn clean package -DskipTests

echo "Building Docker images..."

# Build Eureka Server
docker build -t ${PROJECT_NAME}-eureka-server:1.0.0 ./eureka-server

# Build Gateway Service (when ready)
# docker build -t ${PROJECT_NAME}-gateway-service:1.0.0 ./gateway-service

# Build Auth Service
docker build -t ${PROJECT_NAME}-auth-service:1.0.0 ./auth-service

# Build Backend Service
docker build -t ${PROJECT_NAME}-backend-service:1.0.0 ./backend-service

echo "All services built successfully for ${PROJECT_NAME}!"
