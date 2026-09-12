#!/bin/bash

# Frostlake SQL Engine - HTTP Server Startup Script
#
# Usage: ./start-http-server.sh [PORT] [-f <sql_file>] [--file <sql_file>]
#
# Options:
#   PORT              Port to listen on (default: 18082)
#   -f, --file FILE   SQL file to execute on startup before accepting connections

set -e

# This script lives in frostlake/data/; run Maven against the engine module (../engine).
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/../engine"

PORT="18082"
INIT_FILE=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -f|--file)
            if [[ -z "$2" ]]; then
                echo "Error: -f/--file requires a file path argument"
                exit 1
            fi
            INIT_FILE="$2"
            shift 2
            ;;
        -*)
            echo "Unknown option: $1"
            echo "Usage: $0 [PORT] [-f <sql_file>]"
            exit 1
            ;;
        *)
            PORT="$1"
            shift
            ;;
    esac
done

# Validate init file if specified
if [[ -n "$INIT_FILE" ]]; then
    if [[ ! -f "$INIT_FILE" ]]; then
        echo "Error: SQL file not found: $INIT_FILE"
        exit 1
    fi
fi

echo "==============================================="
echo "Frostlake SQL Engine - HTTP Server"
echo "==============================================="
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed"
    exit 1
fi

# Build the engine if needed
if [ ! -d "target/classes" ]; then
    echo "Building project..."
    mvn compile -q
    if [ $? -ne 0 ]; then
        echo "Build failed"
        exit 1
    fi
    echo ""
fi

# Check port availability before starting
if lsof -Pi ":$PORT" -sTCP:LISTEN -t >/dev/null 2>&1 || ss -tlnp "sport = :$PORT" 2>/dev/null | grep -q LISTEN; then
    echo "Error: port $PORT is already in use"
    echo "Stop the existing process first, or specify a different port:"
    echo "  $0 <port> [options]"
    exit 1
fi

echo "Starting server on port $PORT..."
if [[ -n "$INIT_FILE" ]]; then
    echo "Init file: $INIT_FILE"
fi
echo ""
echo "API Endpoints:"
echo "  POST http://localhost:$PORT/api/execute  - Execute SQL"
echo "  GET  http://localhost:$PORT/api/health   - Health check"
echo "  GET  http://localhost:$PORT/api/sessions - Session info"
echo "  POST http://localhost:$PORT/api/sessions - Start a session"
echo "  DELETE http://localhost:$PORT/api/sessions/{id} - Release a session"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

# Set JVM memory limit to 8GB; TCP no-delay keeps kept-alive clients from waiting ~40 ms per response
# (the server also sets it itself when the launcher has not)
export MAVEN_OPTS="-Xmx8g -Dsun.net.httpserver.nodelay=true"

# Build exec args
EXEC_ARGS="$PORT"
if [[ -n "$INIT_FILE" ]]; then
    EXEC_ARGS="$EXEC_ARGS --file $INIT_FILE"
fi

# Start the server
mvn exec:java \
    -Dexec.mainClass="dev.frostlake.http.DatabaseHttpServer" \
    -Dexec.args="$EXEC_ARGS" \
    -Dexec.cleanupDaemonThreads=false
