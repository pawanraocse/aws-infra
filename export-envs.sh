#!/bin/bash
# export-envs.sh
# Usage: source ./export-envs.sh
# Exports all variables from project root .env, auth-service/.env, and terraform/cognito-config.env

set -a
if [ -f .env ]; then
  echo "[export-envs] Loading root .env"
  . ./.env
fi
if [ -f auth-service/.env ]; then
  echo "[export-envs] Loading auth-service/.env"
  . ./auth-service/.env
fi
if [ -f terraform/cognito-config.env ]; then
  echo "[export-envs] Loading terraform/cognito-config.env"
  . ./terraform/cognito-config.env
fi
set +a
echo "[export-envs] All environment variables exported."
