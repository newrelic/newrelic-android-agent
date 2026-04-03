#!/bin/bash
# Test run - 1 iteration of each configuration to verify setup

set -e

RESULTS_DIR="results"
mkdir -p "$RESULTS_DIR"

echo "=========================================="
echo "Test Run - 1 iteration of each config"
echo "=========================================="
echo ""

# Function to run a test and save results
run_test() {
    local config=$1
    local test_name=$2
    local output_file=$3

    echo "[$test_name] Starting..."

    # Run the test
    npx wdio "$config"

    # Move the result file
    if [ -f "$output_file" ]; then
        mv "$output_file" "$RESULTS_DIR/${output_file%.json}_run1.json"
        echo "[$test_name] ✓ Completed"
    else
        echo "[$test_name] ✗ Failed - result file not found"
    fi

    echo ""
    sleep 5
}

# Run one of each
run_test "wdio-config-android-no-agent.js" "NO AGENT" "no_agent_results.json"
run_test "wdio-config-android-baseline.js" "BASELINE" "baseline_results.json"
run_test "wdio-config-android-session-replay.js" "SESSION REPLAY" "session_replay_results.json"

echo "=========================================="
echo "✓ Test run completed!"
echo ""
echo "Verify results:"
echo "  ls -lh $RESULTS_DIR/"
echo ""
echo "If everything looks good, run full test:"
echo "  ./run-all-tests.sh"
echo "=========================================="
