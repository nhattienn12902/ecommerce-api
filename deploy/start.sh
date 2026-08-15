#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.prod.yml"

echo "Fetching secrets from SSM..."

export APP_JWT_SECRET=$(aws ssm get-parameter \
  --name "/ecommerce/jwt-secret" \
  --with-decryption \
  --query "Parameter.Value" \
  --output text \
  --region us-east-1)

export SPRING_DATASOURCE_PASSWORD=$(aws ssm get-parameter \
  --name "/ecommerce/db-password" \
  --with-decryption \
  --query "Parameter.Value" \
  --output text \
  --region us-east-1)

export APP_AWS_S3_ACCESS_KEY=$(aws ssm get-parameter \
  --name "/ecommerce/s3-access-key" \
  --with-decryption \
  --query "Parameter.Value" \
  --output text \
  --region us-east-1)

export APP_AWS_S3_SECRET_KEY=$(aws ssm get-parameter \
  --name "/ecommerce/s3-secret-key" \
  --with-decryption \
  --query "Parameter.Value" \
  --output text \
  --region us-east-1)

echo "Pulling latest image..."
docker compose -f "$COMPOSE_FILE" pull

echo "Starting Docker Compose..."
docker compose -f "$COMPOSE_FILE" up -d

echo "Cleaning up old images..."
docker image prune -f

echo "Done."