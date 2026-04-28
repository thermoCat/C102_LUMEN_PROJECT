#!/usr/bin/env bash

set -euo pipefail

LISTEN_HOST="${LISTEN_HOST:-0.0.0.0}"
POSTGRES_REMOTE_HOST="${POSTGRES_REMOTE_HOST:-rds-postgres-stg.ssafyapp.com}"
POSTGRES_REMOTE_PORT="${POSTGRES_REMOTE_PORT:-5432}"
POSTGRES_LISTEN_PORT="${POSTGRES_LISTEN_PORT:-15432}"

MYSQL_REMOTE_HOST="${MYSQL_REMOTE_HOST:-rds-stg.ssafyapp.com}"
MYSQL_REMOTE_PORT="${MYSQL_REMOTE_PORT:-3306}"
MYSQL_LISTEN_PORT="${MYSQL_LISTEN_PORT:-13306}"

REDIS_REMOTE_HOST="${REDIS_REMOTE_HOST:-redis-stg.ssafyapp.com}"
REDIS_REMOTE_PORT="${REDIS_REMOTE_PORT:-6379}"
REDIS_LISTEN_PORT="${REDIS_LISTEN_PORT:-16379}"

if ! command -v socat >/dev/null 2>&1; then
  echo "socat is required. Install it first, for example: sudo apt-get install -y socat" >&2
  exit 1
fi

start_relay() {
  local name="$1"
  local listen_port="$2"
  local remote_host="$3"
  local remote_port="$4"

  echo "Starting ${name} relay on ${LISTEN_HOST}:${listen_port} -> ${remote_host}:${remote_port}"
  socat "TCP-LISTEN:${listen_port},bind=${LISTEN_HOST},fork,reuseaddr" "TCP:${remote_host}:${remote_port}" &
  pids+=("$!")
}

cleanup() {
  if ((${#pids[@]} > 0)); then
    kill "${pids[@]}" 2>/dev/null || true
  fi
}

declare -a pids=()
trap cleanup EXIT INT TERM

start_relay "postgres" "${POSTGRES_LISTEN_PORT}" "${POSTGRES_REMOTE_HOST}" "${POSTGRES_REMOTE_PORT}"
start_relay "mysql" "${MYSQL_LISTEN_PORT}" "${MYSQL_REMOTE_HOST}" "${MYSQL_REMOTE_PORT}"
start_relay "redis" "${REDIS_LISTEN_PORT}" "${REDIS_REMOTE_HOST}" "${REDIS_REMOTE_PORT}"

wait -n
