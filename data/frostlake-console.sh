#!/bin/bash

# Frostlake SQL Engine - Console Client Launcher
# Usage: frostlake-console.sh [-f <file>] [--file <file>] [--file=<file>]
# Lives in frostlake/data/; runs against the engine module (../engine).

# Locate the engine module relative to this script (works from any CWD).
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ENGINE_DIR="$SCRIPT_DIR/../engine"

# Parse arguments
FILE_ARG=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -f|--file)
            if [[ -z "$2" || "$2" == -* ]]; then
                echo "Error: --file requires a file path argument"
                exit 1
            fi
            FILE_ARG="$1 $2"
            shift 2
            ;;
        --file=*)
            FILE_ARG="$1"
            shift
            ;;
        -h|--help)
            echo "Usage: $(basename "$0") [-f <file>] [--file <file>]"
            echo ""
            echo "Options:"
            echo "  -f, --file <file>    Execute SQL file on startup before entering interactive mode"
            echo "  -h, --help           Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $(basename "$0") [-f <file>] [--file <file>]"
            exit 1
            ;;
    esac
done

# Check if maven is available
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

# Validate file argument if provided
if [[ -n "$FILE_ARG" ]]; then
    # Extract file path from FILE_ARG
    FILE_PATH="${FILE_ARG#* }"
    FILE_PATH="${FILE_PATH#*=}"
    if [[ ! -f "$FILE_PATH" ]]; then
        echo "Error: File not found: $FILE_PATH"
        exit 1
    fi
fi

# Compile the engine module if needed.
echo "Starting Frostlake SQL Engine Console..."
mvn -q -f "$ENGINE_DIR/pom.xml" compile

# Run the console client (direct JVM — better for an interactive REPL than exec:java).
java -cp "$ENGINE_DIR/target/classes:$(mvn -q -f "$ENGINE_DIR/pom.xml" dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" \
    dev.frostlake.console.ConsoleClient $FILE_ARG
