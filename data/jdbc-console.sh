#!/bin/bash
# JDBC Console Client launcher for Frostlake SQL Engine
#
# Usage: ./jdbc-console.sh [jdbc_url]
#
# Examples:
#   ./jdbc-console.sh
#   ./jdbc-console.sh jdbc:frostlake://localhost:18082
#   ./jdbc-console.sh jdbc:frostlake://myhost:9090
#
# Note: This script only starts the console client. Make sure the HTTP server
# is already running before connecting (use start-http-server.sh).

# This script lives in frostlake/data/; run Maven against the engine module (../engine).
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/../engine"

# Default JDBC URL
JDBC_URL="${JDBC_URL:-jdbc:frostlake://localhost:18082/test_db}"

# Check if custom URL provided as argument
if [ $# -gt 0 ]; then
    JDBC_URL="$1"
fi

echo "╔════════════════════════════════════════════════════════════╗"
echo "║     Frostlake SQL Engine - JDBC Console Launcher           ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "Connecting to: $JDBC_URL"
echo ""

# Build the engine if needed
if [ ! -d "target/classes" ]; then
    echo "Building project..."
    mvn clean compile -q
    if [ $? -ne 0 ]; then
        echo "Build failed"
        exit 1
    fi
    echo "Build successful"
    echo ""
fi

# Set JVM memory options
export MAVEN_OPTS="-Xmx2g"

# Launch JDBC console client
mvn exec:java \
    -Dexec.mainClass="dev.frostlake.console.JdbcConsoleClient" \
    -Dexec.args="$JDBC_URL" \
    -Dexec.cleanupDaemonThreads=false \
    -q

echo ""
echo "JDBC console client stopped."
