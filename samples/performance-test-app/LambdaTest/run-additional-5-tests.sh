#!/bin/bash
# Run 5 additional iterations (runs 6-10) of each performance test configuration
# Each test will retry up to 3 times on failure

RESULTS_DIR="results"
mkdir -p "$RESULTS_DIR"

echo "=========================================="
echo "Android Performance Test Runner"
echo "Running 5 additional iterations (6-10)"
echo "Total tests: 15 (this will take ~40 minutes)"
echo "=========================================="
echo ""

# Function to run a test and save results (with retry logic)
run_test() {
    local config=$1
    local run_number=$2
    local test_name=$3
    local max_retries=3
    local retry=0

    while [ $retry -lt $max_retries ]; do
        if [ $retry -gt 0 ]; then
            echo "[$test_name - Run $run_number/10] Retry attempt $retry/$((max_retries-1))..."
            sleep 15  # Extra cooldown before retry
        else
            echo "[$test_name - Run $run_number/10] Starting..."
        fi

        # Run the test
        if npx wdio "$config"; then
            # Move the result file to results directory with run number
            local moved=false
            if [ -f "no_agent_results.json" ]; then
                mv no_agent_results.json "$RESULTS_DIR/no_agent_run${run_number}.json"
                moved=true
            elif [ -f "baseline_results.json" ]; then
                mv baseline_results.json "$RESULTS_DIR/baseline_run${run_number}.json"
                moved=true
            elif [ -f "session_replay_results.json" ]; then
                mv session_replay_results.json "$RESULTS_DIR/session_replay_run${run_number}.json"
                moved=true
            fi

            if [ "$moved" = true ]; then
                echo "[$test_name - Run $run_number/10] ✓ Completed"
                echo ""

                # Wait 10 seconds between tests to let device cool down
                if [ $run_number -lt 10 ]; then
                    echo "Waiting 10 seconds before next run..."
                    sleep 10
                fi
                return 0  # Success
            else
                echo "[$test_name - Run $run_number/10] ✗ Result file not found"
            fi
        else
            echo "[$test_name - Run $run_number/10] ✗ Test execution failed"
        fi

        retry=$((retry + 1))

        if [ $retry -eq $max_retries ]; then
            echo "[$test_name - Run $run_number/10] ✗✗✗ FAILED after $max_retries attempts"
            exit 1
        fi
    done
}

# Run No Agent tests (iterations 6-10)
echo "=========================================="
echo "Testing: NO AGENT (Pure Baseline)"
echo "=========================================="
for i in {6..10}; do
    run_test "wdio-config-android-no-agent.js" $i "NO AGENT"
done

echo ""
echo "=========================================="
echo "Testing: BASELINE (Agent ON, SR OFF)"
echo "=========================================="
for i in {6..10}; do
    run_test "wdio-config-android-baseline.js" $i "BASELINE"
done

echo ""
echo "=========================================="
echo "Testing: SESSION REPLAY (Agent ON, SR ON)"
echo "=========================================="
for i in {6..10}; do
    run_test "wdio-config-android-session-replay.js" $i "SESSION REPLAY"
done

echo ""
echo "=========================================="
echo "✓ All additional tests completed!"
echo "Results saved in: $RESULTS_DIR/"
echo ""
echo "Run aggregate script with 10 runs:"
echo "  node aggregate-results.js 10"
echo "=========================================="
