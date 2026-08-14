#!/usr/bin/env bash
# Spin Transaction API — startup (Linux/macOS/Git Bash)
set -euo pipefail

API_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$API_ROOT/.." && pwd)"
DOCKER_DIR="$API_ROOT/docker"

if [[ -d "$REPO_ROOT/provider-mock" ]]; then
  PROVIDER_ROOT="$REPO_ROOT/provider-mock"
elif [[ -d "$REPO_ROOT/spin-provider-mock" ]]; then
  PROVIDER_ROOT="$REPO_ROOT/spin-provider-mock"
else
  echo "provider-mock not found under $REPO_ROOT" >&2
  exit 1
fi

SKIP_DOCKER=false
SKIP_BUILD=false
SKIP_APPS=false
API_PROFILES="${API_PROFILES:-observability}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-docker) SKIP_DOCKER=true ;;
    --skip-build) SKIP_BUILD=true ;;
    --skip-apps) SKIP_APPS=true ;;
    --profiles) API_PROFILES="$2"; shift ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

port_open() {
  (echo >/dev/tcp/127.0.0.1/"$1") >/dev/null 2>&1
}

wait_port() {
  local name="$1" port="$2" timeout="${3:-90}" elapsed=0
  echo "Waiting for $name on :$port..."
  until port_open "$port"; do
    sleep 2
    elapsed=$((elapsed + 2))
    if [[ $elapsed -ge $timeout ]]; then
      echo "Timeout waiting for $name on :$port" >&2
      exit 1
    fi
  done
  echo "OK $name :$port"
}

if [[ "$SKIP_DOCKER" == false ]]; then
  if command -v docker >/dev/null 2>&1; then
    if ! port_open 5433; then
      echo "==> docker compose up"
      (cd "$DOCKER_DIR" && docker compose up -d)
      wait_port "PostgreSQL" 5433 90
    fi
    wait_port "Elasticsearch" 9200 120 || true
  else
    echo "Docker not available; assuming infra is already running"
  fi
fi

if [[ "$SKIP_BUILD" == false ]]; then
  echo "==> mvn package provider-mock"
  (cd "$PROVIDER_ROOT" && mvn -q package -DskipTests)
  echo "==> mvn package transaction-api"
  (cd "$API_ROOT" && mvn -q package -DskipTests)
fi

if [[ "$SKIP_APPS" == false ]]; then
  echo "==> Starting provider-mock :8084 (background)"
  (cd "$PROVIDER_ROOT" && mvn spring-boot:run) &
  PROVIDER_PID=$!
  wait_port "provider-mock" 8084 90

  echo "==> Starting transaction-api :8083 (background, profiles=$API_PROFILES)"
  if [[ -n "$API_PROFILES" ]]; then
    (cd "$API_ROOT" && mvn spring-boot:run -Dspring-boot.run.profiles="$API_PROFILES") &
  else
    (cd "$API_ROOT" && mvn spring-boot:run) &
  fi
  API_PID=$!

  trap 'kill $PROVIDER_PID $API_PID 2>/dev/null || true' EXIT

  echo ""
  echo "Stack running. API: http://localhost:8083/swagger-ui.html"
  echo "Press Ctrl+C to stop."
  wait
fi
