#!/usr/bin/env bash
set -e

# Detect or set JAVA_HOME if not defined
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
    elif [ -d "/Library/Java/JavaVirtualMachines/jdk1.8.0_144.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk1.8.0_144.jdk/Contents/Home"
    elif command -v /usr/libexec/java_home &> /dev/null; then
        export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
    fi
fi

if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v javac &> /dev/null; then
    echo "Error: javac not found. Please install Java 17+ or set JAVA_HOME." >&2
    exit 1
fi

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

# Source environment variables if present
if [ -f ".env" ]; then
    # Export non-comment lines
    set -a
    [ -f .env ] && . .env
    set +a
fi

CLASSES_DIR="$PROJECT_DIR/target/classes"
mkdir -p "$CLASSES_DIR"

# Find all java sources
SOURCES_LIST="$PROJECT_DIR/target/sources.txt"
find src/main/java -name "*.java" > "$SOURCES_LIST"

# Incremental compile: recompile if target is empty or source is newer
NEED_COMPILE=0
if [ ! -f "$CLASSES_DIR/com/bjak/assistant/Main.class" ]; then
    NEED_COMPILE=1
else
    NEWEST_SRC=$(find src/main/java -name "*.java" -newer "$CLASSES_DIR/com/bjak/assistant/Main.class" | head -n 1)
    if [ -n "$NEWEST_SRC" ]; then
        NEED_COMPILE=1
    fi
fi

if [ "$NEED_COMPILE" -eq 1 ]; then
    javac -encoding UTF-8 -d "$CLASSES_DIR" @"$SOURCES_LIST"
fi

# Run the CLI application
exec java -cp "$CLASSES_DIR" com.bjak.assistant.Main "$@"
