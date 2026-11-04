#!/usr/bin/env bash
# ==============================================================================
# TravelEasy - Export environment variables from .env for local development
# ==============================================================================
# Usage:
#   source ./scripts/export-env.sh
# ==============================================================================

ENV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Warning: .env file not found at $ENV_FILE"
    echo "Please copy .env.example to .env and configure your credentials."
    return 1 2>/dev/null || exit 1
fi

set -a
# Read .env ignoring comments, carriage returns, and blank lines
# shellcheck disable=SC1090
source <(grep -v '^#' "$ENV_FILE" | grep -v '^$' | sed -e 's/\r$//')
set +a

echo "TravelEasy: Successfully exported environment variables from .env."
