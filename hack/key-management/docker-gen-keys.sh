#!/usr/bin/env bash
set -eo pipefail

# Generates all necessary keys for a node using Docker image
# Usage:
# ./docker-gen-keys.sh <directory where to put keys> [--test]
#   --test  tests if all the required keys have been created

if [[ "$1" == /* ]] || [[ "$1" == ~* ]]; then
    OUTPUT_DIR="$1"
else
    OUTPUT_DIR="$PWD/$1"
fi

if [[ ! -d "$OUTPUT_DIR" ]]; then
    echo "ERROR: output dir doesn't exist"
    echo "usage: ./docker-gen-keys.sh <dir>"
    exit "1"
fi

shift

docker pull casperlabs/key-generator:latest || true

if [[ -z "$DRONE_BUILD_NUMBER" ]]; then
    docker run --rm -it -v "$OUTPUT_DIR":/keys casperlabs/key-generator:latest /keys
else
    docker run --rm -v "$OUTPUT_DIR":/keys casperlabs/key-generator:DRONE-"$DRONE_BUILD_NUMBER" /keys
fi


if [[ "$1" == "--test" ]]; then
    if [[ -f "$OUTPUT_DIR/node-id" ]] && \
       [[ -f "$OUTPUT_DIR/validator-id" ]] && \
       [[ -f "$OUTPUT_DIR/node.key.pem" ]] && \
       [[ -f "$OUTPUT_DIR/node.certificate.pem" ]] && \
       [[ -f "$OUTPUT_DIR/validator-private.pem" ]] && \
       [[ -f "$OUTPUT_DIR/validator-public.pem" ]]; then
        exit "0"
    else
        echo "ERROR: Some of required files haven't created"
        exit "1"
    fi
fi