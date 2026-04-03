#!/bin/bash
set -e

RESULTS_DIR="results"
mkdir -p "$RESULTS_DIR"

echo "Testing 2 consecutive runs..."

# Run 1
echo "[Run 1] Starting..."
npx wdio wdio-config-android-no-agent.js
mv no_agent_results.json "$RESULTS_DIR/no_agent_run1_test.json"
echo "[Run 1] Completed"
echo ""

# Wait
echo "Waiting 10 seconds..."
sleep 10

# Run 2
echo "[Run 2] Starting..."
npx wdio wdio-config-android-no-agent.js
mv no_agent_results.json "$RESULTS_DIR/no_agent_run2_test.json"
echo "[Run 2] Completed"

echo "Success!"
