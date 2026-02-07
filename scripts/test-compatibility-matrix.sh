#!/bin/bash
#
# Test New Relic Android Agent against multiple AGP/Gradle/Java/Kotlin versions
# This script tests the agent-test-app sample against various version combinations
#
# Usage:
#   ./scripts/test-compatibility-matrix.sh [OPTIONS]
#
# Options:
#   --combination <name>    Test only a specific combination by name
#   --json <path>           Path to compatibility matrix JSON (default: .github/workflows/compatibility-matrix.json)
#   --test-app <path>       Path to test app (default: samples/agent-test-app)
#   --clean                 Clean build between tests
#   --verbose               Enable verbose output
#   --help                  Show this help message
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MATRIX_JSON="$PROJECT_ROOT/.github/workflows/compatibility-matrix.json"
TEST_APP_DIR="$PROJECT_ROOT/samples/agent-test-app"
SPECIFIC_COMBINATION=""
CLEAN_BUILD=false
VERBOSE=false

# Results tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
declare -a FAILED_COMBINATIONS=()

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --combination)
            SPECIFIC_COMBINATION="$2"
            shift 2
            ;;
        --json)
            MATRIX_JSON="$2"
            shift 2
            ;;
        --test-app)
            TEST_APP_DIR="$2"
            shift 2
            ;;
        --clean)
            CLEAN_BUILD=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            head -n 20 "$0" | tail -n 16
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Function to print colored output
log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

log_section() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# Check prerequisites
check_prerequisites() {
    log_section "Checking Prerequisites"

    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed. Install with: brew install jq (macOS) or apt-get install jq (Linux)"
        exit 1
    fi
    log_success "jq is installed"

    if [[ ! -f "$MATRIX_JSON" ]]; then
        log_error "Matrix JSON not found at: $MATRIX_JSON"
        exit 1
    fi
    log_success "Matrix JSON found: $MATRIX_JSON"

    if [[ ! -d "$TEST_APP_DIR" ]]; then
        log_error "Test app directory not found at: $TEST_APP_DIR"
        exit 1
    fi
    log_success "Test app directory found: $TEST_APP_DIR"

    # Check if agent artifacts are published locally
    if [[ ! -d "$PROJECT_ROOT/build/.m2/repository/com/newrelic/agent/android" ]]; then
        log_warning "Agent artifacts not found in local Maven repo"
        log_info "Publishing agent to local Maven repository..."
        cd "$PROJECT_ROOT"
        ./gradlew publish -x test -x functionalTests -x integrationTests > /dev/null 2>&1 || {
            log_error "Failed to publish agent artifacts"
            exit 1
        }
        log_success "Agent artifacts published"
    else
        log_success "Agent artifacts found in local Maven repository"
    fi
}

# Function to set Java version
set_java_version() {
    local java_version=$1

    # Check if Java is already configured (e.g., in CI environments)
    if [[ -n "$JAVA_HOME" ]] && [[ -x "$JAVA_HOME/bin/java" ]]; then
        # Extract major version from current Java
        local current_version=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')

        if [[ "$current_version" == "$java_version" ]]; then
            if $VERBOSE; then
                log_info "Java $java_version already configured at: $JAVA_HOME"
                log_info "Java version: $(java -version 2>&1 | head -n 1)"
            fi
            return 0
        fi
    fi

    # Java not configured or wrong version - try to set it up
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS - use java_home
        export JAVA_HOME=$(/usr/libexec/java_home -v "$java_version" 2>/dev/null) || {
            log_error "Java $java_version not found. Install with: brew install openjdk@$java_version"
            return 1
        }
    else
        # Linux - check common paths
        if [[ -d "/usr/lib/jvm/java-${java_version}-openjdk" ]]; then
            export JAVA_HOME="/usr/lib/jvm/java-${java_version}-openjdk"
        elif [[ -d "/usr/lib/jvm/java-${java_version}-openjdk-amd64" ]]; then
            export JAVA_HOME="/usr/lib/jvm/java-${java_version}-openjdk-amd64"
        elif [[ -d "/opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/${java_version}"* ]]; then
            # GitHub Actions runner path
            export JAVA_HOME=$(ls -d /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/${java_version}*/x64 2>/dev/null | head -n 1)
        else
            log_error "Java $java_version not found at /usr/lib/jvm/ or /opt/hostedtoolcache/"
            return 1
        fi
    fi

    export PATH="$JAVA_HOME/bin:$PATH"

    if $VERBOSE; then
        log_info "Set JAVA_HOME to: $JAVA_HOME"
        log_info "Java version: $(java -version 2>&1 | head -n 1)"
    fi

    return 0
}

# Function to test a single combination
test_combination() {
    local name=$1
    local agp=$2
    local gradle=$3
    local java=$4
    local kotlin=$5

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    log_section "Testing: $name"
    log_info "AGP: $agp | Gradle: $gradle | Java: $java | Kotlin: $kotlin"

    # Set Java version
    if ! set_java_version "$java"; then
        log_error "Failed to set Java version $java"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_COMBINATIONS+=("$name - Java $java not available")
        return 1
    fi

    # Update Gradle wrapper to required version
    cd "$TEST_APP_DIR"
    log_info "Updating Gradle wrapper to $gradle..."

    # Try using gradle command if available, otherwise use gradlew
    if command -v gradle &> /dev/null; then
        gradle wrapper --gradle-version "$gradle" > /dev/null 2>&1 || {
            log_warning "Using installed Gradle to update wrapper"
        }
    else
        # Fallback: update the wrapper properties file directly
        local wrapper_props="$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties"
        if [[ -f "$wrapper_props" ]]; then
            sed -i.bak "s|gradle-[0-9.]*-bin.zip|gradle-$gradle-bin.zip|g" "$wrapper_props"
            log_info "Updated wrapper properties file"
        fi
    fi

    # Backup and update gradle.properties with version parameters
    # This is needed because settings.gradle can't access -P command-line properties
    local props_file="$TEST_APP_DIR/gradle.properties"
    local backup_file="$TEST_APP_DIR/gradle.properties.backup"
    cp "$props_file" "$backup_file"

    # Update versions in gradle.properties
    sed -i.tmp "s/^newrelic.agp.version=.*/newrelic.agp.version=$agp/" "$props_file"
    sed -i.tmp "s/^newrelic.kotlin.version=.*/newrelic.kotlin.version=$kotlin/" "$props_file"
    sed -i.tmp "s/^newrelic.gradle.version=.*/newrelic.gradle.version=$gradle/" "$props_file"
    rm -f "$props_file.tmp"

    # Clean if requested
    if $CLEAN_BUILD; then
        log_info "Cleaning test app..."
        ./gradlew clean > /dev/null 2>&1 || true
    fi

    # Build the test app with specified versions
    log_info "Building test app..."

    local build_output
    local build_cmd="./gradlew -PagentRepo=local clean assembleRelease"

    if $VERBOSE; then
        log_info "Running: $build_cmd"
        # Run with output visible in real-time
        $build_cmd 2>&1 | tee /tmp/build-output.log
        local build_result=${PIPESTATUS[0]}
        build_output=$(cat /tmp/build-output.log)
    else
        build_output=$($build_cmd 2>&1)
        local build_result=$?
    fi

    # Restore original gradle.properties
    mv "$backup_file" "$props_file"

    if [[ $build_result -eq 0 ]]; then
        log_success "Build PASSED: $name"
        PASSED_TESTS=$((PASSED_TESTS + 1))

        # Check if APK was generated
        local apk_path="$TEST_APP_DIR/build/outputs/apk/release/agent-test-app-release-unsigned.apk"
        if [[ -f "$apk_path" ]]; then
            local apk_size=$(du -h "$apk_path" | cut -f1)
            log_info "APK generated: $apk_size"
        fi

        return 0
    else
        log_error "Build FAILED: $name"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_COMBINATIONS+=("$name")

        # Print last 50 lines of build output to help with debugging
        if ! $VERBOSE; then
            echo ""
            echo "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            echo "${RED}  Build Error Output (last 50 lines):${NC}"
            echo "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            echo "$build_output" | tail -50
            echo "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
            echo ""
        fi

        # Save full error output
        local error_file="$PROJECT_ROOT/build/test-results/compatibility-${name// /-}.log"
        mkdir -p "$PROJECT_ROOT/build/test-results"
        echo "$build_output" > "$error_file"
        log_error "Full error log saved to: $error_file"

        if $VERBOSE; then
            echo "$build_output" | tail -n 50
        fi

        return 1
    fi
}

# Main execution
main() {
    log_section "New Relic Android Agent - Compatibility Matrix Testing"

    check_prerequisites

    # Read combinations from JSON
    local combinations_count=$(jq '.combinations | length' "$MATRIX_JSON")
    log_info "Found $combinations_count combinations in matrix"

    # Test each combination
    for i in $(seq 0 $((combinations_count - 1))); do
        local name=$(jq -r ".combinations[$i].name" "$MATRIX_JSON")

        # Skip if specific combination requested and this isn't it
        if [[ -n "$SPECIFIC_COMBINATION" ]] && [[ "$name" != "$SPECIFIC_COMBINATION" ]]; then
            continue
        fi

        local agp=$(jq -r ".combinations[$i].agp" "$MATRIX_JSON")
        local gradle=$(jq -r ".combinations[$i].gradle" "$MATRIX_JSON")
        local java=$(jq -r ".combinations[$i].java" "$MATRIX_JSON")
        local kotlin=$(jq -r ".combinations[$i].kotlin" "$MATRIX_JSON")

        test_combination "$name" "$agp" "$gradle" "$java" "$kotlin"

        echo ""
    done

    # Print summary
    log_section "Test Summary"
    echo ""
    echo -e "  Total Tests:   ${BLUE}$TOTAL_TESTS${NC}"
    echo -e "  Passed:        ${GREEN}$PASSED_TESTS${NC}"
    echo -e "  Failed:        ${RED}$FAILED_TESTS${NC}"
    echo ""

    if [[ $FAILED_TESTS -gt 0 ]]; then
        log_error "Failed Combinations:"
        for combo in "${FAILED_COMBINATIONS[@]}"; do
            echo -e "  ${RED}✗${NC} $combo"
        done
        echo ""
        exit 1
    else
        log_success "All tests passed!"
        exit 0
    fi
}

# Run main function
main