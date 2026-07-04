#!/bin/bash

# Simple hot-reload script for lms-rust mock server
# Works out-of-the-box on Linux without installing external tools like cargo-watch.

PID=""

cleanup() {
    if [ ! -z "$PID" ]; then
        echo -e "\nStopping mock server..."
        kill $PID 2>/dev/null
    fi
    exit 0
}

# Trap ctrl+c (SIGINT) and SIGTERM to kill the background cargo process
trap cleanup SIGINT SIGTERM

# Function to get a hash of the modification times of all Rust files in src/
get_src_hash() {
    find src/ -name "*.rs" -type f 2>/dev/null | sort | xargs stat -c "%Y %n" 2>/dev/null | md5sum
}

run_server() {
    if [ ! -z "$PID" ]; then
        echo -e "\n[Watch] Changes detected! Recompiling and restarting mock_server..."
        kill $PID 2>/dev/null
        wait $PID 2>/dev/null
    else
        echo -e "\n[Watch] Starting mock_server..."
    fi
    
    # Run in background
    cargo run --bin mock_server &
    PID=$!
}

LAST_HASH=""

# Main loop
while true; do
    CURRENT_HASH=$(get_src_hash)
    if [ "$CURRENT_HASH" != "$LAST_HASH" ]; then
        run_server
        LAST_HASH=$CURRENT_HASH
    fi
    sleep 0.1
done
