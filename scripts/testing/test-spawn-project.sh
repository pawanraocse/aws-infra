#!/bin/bash
# =============================================================================
# test-spawn-project.sh - Test Suite for spawn-project.sh
# =============================================================================
# Comprehensive tests to validate the project spawning script.
#
# Usage:
#   ./scripts/test-spawn-project.sh
#   ./scripts/test-spawn-project.sh --verbose
#   ./scripts/test-spawn-project.sh --test <test-name>
#
# Author: SaaS Factory Template
# Version: 1.0.0
# =============================================================================

set -euo pipefail

# =============================================================================
# CONFIGURATION
# =============================================================================

readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SPAWN_SCRIPT="$SCRIPT_DIR/spawn-project.sh"
TEST_DIR="/tmp/saas-factory-tests"
VERBOSE=false
SPECIFIC_TEST=""

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# =============================================================================
# HELPER FUNCTIONS
# =============================================================================

log_test() {
    echo -e "${BLUE}[TEST]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++)) || true
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++)) || true
}

log_info() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${CYAN}[INFO]${NC} $1"
    fi
}

cleanup() {
    if [[ -d "$TEST_DIR" ]]; then
        rm -rf "$TEST_DIR"
    fi
}

setup() {
    cleanup
    mkdir -p "$TEST_DIR"
}

assert_file_exists() {
    local file="$1"
    local msg="${2:-File should exist: $file}"
    
    if [[ -f "$file" ]]; then
        log_info "  ✓ File exists: $file"
        return 0
    else
        log_fail "$msg"
        return 1
    fi
}

assert_dir_exists() {
    local dir="$1"
    local msg="${2:-Directory should exist: $dir}"
    
    if [[ -d "$dir" ]]; then
        log_info "  ✓ Directory exists: $dir"
        return 0
    else
        log_fail "$msg"
        return 1
    fi
}

assert_file_contains() {
    local file="$1"
    local pattern="$2"
    local msg="${3:-File should contain pattern}"
    
    if grep -q "$pattern" "$file" 2>/dev/null; then
        log_info "  ✓ Found pattern: $pattern"
        return 0
    else
        log_fail "$msg: '$pattern' not found in $file"
        return 1
    fi
}

assert_file_not_contains() {
    local file="$1"
    local pattern="$2"
    local msg="${3:-File should not contain pattern}"
    
    if ! grep -q "$pattern" "$file" 2>/dev/null; then
        log_info "  ✓ Pattern not found (correct): $pattern"
        return 0
    else
        log_fail "$msg: '$pattern' should not be in $file"
        return 1
    fi
}

assert_command_fails() {
    local cmd="$1"
    local msg="${2:-Command should fail}"
    
    if ! eval "$cmd" > /dev/null 2>&1; then
        log_info "  ✓ Command failed as expected"
        return 0
    else
        log_fail "$msg"
        return 1
    fi
}

# =============================================================================
# TEST CASES
# =============================================================================

test_help_flag() {
    log_test "Help flag displays usage"
    ((TESTS_RUN++)) || true
    
    local output
    output=$("$SPAWN_SCRIPT" --help 2>&1) || true
    
    if [[ "$output" == *"Usage:"* ]] && [[ "$output" == *"project-name"* ]]; then
        log_pass "Help flag works correctly"
    else
        log_fail "Help output missing expected content"
    fi
}

test_missing_arguments() {
    log_test "Missing arguments shows error"
    ((TESTS_RUN++)) || true
    
    if assert_command_fails "'$SPAWN_SCRIPT' 2>&1" "Should fail without arguments"; then
        log_pass "Missing arguments handled correctly"
    fi
}

test_invalid_project_name_uppercase() {
    log_test "Uppercase project name rejected"
    ((TESTS_RUN++)) || true
    
    if assert_command_fails "'$SPAWN_SCRIPT' MyProject '$TEST_DIR/test1' 2>&1" "Should reject uppercase"; then
        log_pass "Uppercase project name rejected"
    fi
}

test_invalid_project_name_special_chars() {
    log_test "Special characters in name rejected"
    ((TESTS_RUN++)) || true
    
    if assert_command_fails "'$SPAWN_SCRIPT' 'my_project!' '$TEST_DIR/test2' 2>&1" "Should reject special chars"; then
        log_pass "Special characters rejected"
    fi
}

test_invalid_project_name_starts_with_number() {
    log_test "Project name starting with number rejected"
    ((TESTS_RUN++)) || true
    
    if assert_command_fails "'$SPAWN_SCRIPT' '123project' '$TEST_DIR/test3' 2>&1" "Should reject starting number"; then
        log_pass "Number-starting name rejected"
    fi
}

test_valid_project_name_simple() {
    log_test "Simple valid project name accepted"
    ((TESTS_RUN++)) || true
    
    # Ensure test directory exists for dry-run validation
    mkdir -p "$TEST_DIR"
    
    # Use dry-run to test validation without copying
    local output
    if output=$("$SPAWN_SCRIPT" imagekit "$TEST_DIR/imagekit-test" --dry-run 2>&1); then
        if [[ "$output" == *"Project:"*"imagekit"* ]]; then
            log_pass "Simple project name accepted"
        else
            log_fail "Output doesn't contain expected project name"
        fi
    else
        log_fail "Valid project name was rejected"
    fi
}

test_valid_project_name_with_hyphens() {
    log_test "Hyphenated project name accepted"
    ((TESTS_RUN++)) || true
    
    # Ensure test directory exists for dry-run validation
    mkdir -p "$TEST_DIR"
    
    local output
    if output=$("$SPAWN_SCRIPT" my-cool-project "$TEST_DIR/hyphen-test" --dry-run 2>&1); then
        if [[ "$output" == *"Project:"*"my-cool-project"* ]]; then
            log_pass "Hyphenated name accepted"
        else
            log_fail "Project name not in output"
        fi
    else
        log_fail "Hyphenated project name was rejected"
    fi
}

test_existing_destination_rejected() {
    log_test "Existing destination rejected"
    ((TESTS_RUN++)) || true
    
    # Create the destination directory first
    mkdir -p "$TEST_DIR/existing-project"
    
    if assert_command_fails "'$SPAWN_SCRIPT' newproject '$TEST_DIR/existing-project' 2>&1" "Should reject existing dir"; then
        log_pass "Existing destination rejected"
    fi
}

test_dry_run_no_files_created() {
    log_test "Dry-run creates no files"
    ((TESTS_RUN++)) || true
    
    "$SPAWN_SCRIPT" testproject "$TEST_DIR/dryrun-test" --dry-run > /dev/null 2>&1 || true
    
    if [[ ! -d "$TEST_DIR/dryrun-test" ]]; then
        log_pass "Dry-run created no files"
    else
        log_fail "Dry-run created files when it shouldn't"
    fi
}

test_full_spawn_creates_structure() {
    log_test "Full spawn creates expected structure"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/full-spawn-test"
    
    # Run actual spawn (not dry-run)
    if "$SPAWN_SCRIPT" testapp "$dest" --skip-git > /dev/null 2>&1; then
        local all_pass=true
        
        # Check key directories exist
        assert_dir_exists "$dest/auth-service" || all_pass=false
        assert_dir_exists "$dest/gateway-service" || all_pass=false
        assert_dir_exists "$dest/platform-service" || all_pass=false
        assert_dir_exists "$dest/frontend" || all_pass=false
        assert_dir_exists "$dest/terraform" || all_pass=false
        
        # Check key files exist
        assert_file_exists "$dest/pom.xml" || all_pass=false
        assert_file_exists "$dest/docker-compose.yml" || all_pass=false
        
        if [[ "$all_pass" == "true" ]]; then
            log_pass "Full spawn creates expected structure"
        fi
    else
        log_fail "Full spawn command failed"
    fi
}

test_pom_files_updated() {
    log_test "POM files updated with new project name"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/pom-test"
    
    if "$SPAWN_SCRIPT" mycrm "$dest" --skip-git > /dev/null 2>&1; then
        local all_pass=true
        
        # Check root pom.xml has new artifactId (Mycrm - PascalCase)
        assert_file_contains "$dest/pom.xml" "Mycrm" "Root pom should have new artifact name (PascalCase)" || all_pass=false
        
        # groupId should remain as com.learning (not changed)
        assert_file_contains "$dest/pom.xml" "com.learning" "groupId should remain as com.learning" || all_pass=false
        
        # AWS-Infra should be replaced
        assert_file_not_contains "$dest/pom.xml" "AWS-Infra" "AWS-Infra should be replaced" || all_pass=false
        
        if [[ "$all_pass" == "true" ]]; then
            log_pass "POM files correctly updated"
        fi
    else
        log_fail "Spawn for POM test failed"
    fi
}

test_java_packages_unchanged() {
    log_test "Java packages correctly kept as com.learning"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/java-pkg-test"
    
    if "$SPAWN_SCRIPT" myapp "$dest" --skip-git > /dev/null 2>&1; then
        local all_pass=true
        
        # Check that original package directory still exists (com.learning not renamed)
        assert_dir_exists "$dest/auth-service/src/main/java/com/learning/authservice" "Original package dir should exist" || all_pass=false
        
        # Verify Java files still have com.learning package
        local sample_file="$dest/auth-service/src/main/java/com/learning/authservice/AuthServiceApplication.java"
        if [[ -f "$sample_file" ]]; then
            assert_file_contains "$sample_file" "com.learning" "Java file should have com.learning package" || all_pass=false
        fi
        
        if [[ "$all_pass" == "true" ]]; then
            log_pass "Java packages correctly kept unchanged"
        fi
    else
        log_fail "Spawn for Java package test failed"
    fi
}

test_terraform_config_updated() {
    log_test "Terraform configuration updated and .env created"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/tf-test"
    local proj="myplatform"
    
    if "$SPAWN_SCRIPT" "$proj" "$dest" --skip-git > /dev/null 2>&1; then
        local all_pass=true
        
        # Check .env created
        if [[ -f "$dest/.env" ]]; then
            log_info "  ✓ .env file created"
            assert_file_contains "$dest/.env" "PROJECT_NAME=$proj" ".env should have PROJECT_NAME=$proj" || all_pass=false
        else
            log_fail ".env file should be created"
            all_pass=false
        fi
        
        # Check variables.tf does NOT have hardcoded default active
        # (This assumes the template variables.tf has the default commented out)
        
        # Check terraform state files are removed
        if [[ ! -f "$dest/terraform/terraform.tfstate" ]]; then
            log_info "  ✓ Terraform state files cleaned"
        else
            log_fail "Terraform state files should be removed"
            all_pass=false
        fi
        
        if [[ "$all_pass" == "true" ]]; then
            log_pass "Terraform configuration updated"
        fi
    else
        log_fail "Spawn for Terraform test failed"
    fi
}

test_git_initialized_by_default() {
    log_test "Git repository initialized by default"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/git-test"
    
    if "$SPAWN_SCRIPT" gitproject "$dest" > /dev/null 2>&1; then
        if [[ -d "$dest/.git" ]]; then
            log_pass "Git repository initialized"
        else
            log_fail "Git repository not initialized"
        fi
    else
        log_fail "Spawn for Git test failed"
    fi
}

test_skip_git_flag() {
    log_test "Skip-git flag prevents git init"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/no-git-test"
    
    if "$SPAWN_SCRIPT" nogitproject "$dest" --skip-git > /dev/null 2>&1; then
        if [[ ! -d "$dest/.git" ]]; then
            log_pass "Git repository skipped correctly"
        else
            log_fail "Git repository created when skip-git specified"
        fi
    else
        log_fail "Spawn with skip-git failed"
    fi
}

test_excludes_build_artifacts() {
    log_test "Build artifacts excluded from copy"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/exclude-test"
    
    if "$SPAWN_SCRIPT" excludetest "$dest" --skip-git > /dev/null 2>&1; then
        local all_pass=true
        
        # Check that target directories are NOT copied
        if [[ -d "$dest/auth-service/target" ]]; then
            log_fail "Target directory should not be copied"
            all_pass=false
        else
            log_info "  ✓ Target directory excluded"
        fi
        
        # Check that node_modules is NOT copied
        if [[ -d "$dest/frontend/node_modules" ]]; then
            log_fail "node_modules should not be copied"
            all_pass=false
        else
            log_info "  ✓ node_modules excluded"
        fi
        
        if [[ "$all_pass" == "true" ]]; then
            log_pass "Build artifacts excluded correctly"
        fi
    else
        log_fail "Spawn for exclude test failed"
    fi
}

test_angular_config_updated() {
    log_test "Angular configuration updated"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/angular-test"
    
    if "$SPAWN_SCRIPT" myangular "$dest" --skip-git > /dev/null 2>&1; then
        local all_pass=true
        
        # Check package.json updated
        if [[ -f "$dest/frontend/package.json" ]]; then
            assert_file_contains "$dest/frontend/package.json" "myangular" "Should have new project name" || all_pass=false
        fi
        
        if [[ "$all_pass" == "true" ]]; then
            log_pass "Angular configuration updated"
        fi
    else
        log_fail "Spawn for Angular test failed"
    fi
}

test_documentation_updated() {
    log_test "Documentation updated with project name"
    ((TESTS_RUN++)) || true
    
    local dest="$TEST_DIR/docs-test"
    
    if "$SPAWN_SCRIPT" mydocs "$dest" --skip-git > /dev/null 2>&1; then
        local all_pass=true
        
        # Check terraform README has project name  
        if [[ -f "$dest/terraform/README.md" ]]; then
            assert_file_contains "$dest/terraform/README.md" "mydocs" "Should have new project name" || all_pass=false
            assert_file_not_contains "$dest/terraform/README.md" "cloud-infra" "Should not have old name" || all_pass=false
        fi
        
        # Check delete-ssm.sh has project name
        if [[ -f "$dest/scripts/terraform/delete-ssm.sh" ]]; then
            assert_file_contains "$dest/scripts/terraform/delete-ssm.sh" "mydocs" "delete-ssm.sh should have new name" || all_pass=false
        fi
        
        if [[ "$all_pass" == "true" ]]; then
            log_pass "Documentation updated correctly"
        fi
    else
        log_fail "Spawn for documentation test failed"
    fi
}

# =============================================================================
# TEST RUNNER
# =============================================================================

run_all_tests() {
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}  SaaS Factory Template - Spawn Script Test Suite${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
    
    setup
    
    # Validation tests
    test_help_flag
    test_missing_arguments
    test_invalid_project_name_uppercase
    test_invalid_project_name_special_chars
    test_invalid_project_name_starts_with_number
    test_valid_project_name_simple
    test_valid_project_name_with_hyphens
    test_existing_destination_rejected
    test_dry_run_no_files_created
    
    # Functional tests
    test_full_spawn_creates_structure
    test_pom_files_updated
    test_java_packages_unchanged
    test_terraform_config_updated
    test_git_initialized_by_default
    test_skip_git_flag
    test_excludes_build_artifacts
    test_angular_config_updated
    test_documentation_updated
    
    cleanup
    
    # Summary
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "  ${CYAN}Test Summary${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  Total:   $TESTS_RUN"
    echo -e "  ${GREEN}Passed:  $TESTS_PASSED${NC}"
    echo -e "  ${RED}Failed:  $TESTS_FAILED${NC}"
    echo ""
    
    if [[ $TESTS_FAILED -eq 0 ]]; then
        echo -e "${GREEN}  ✅ All tests passed!${NC}"
        echo ""
        return 0
    else
        echo -e "${RED}  ❌ Some tests failed${NC}"
        echo ""
        return 1
    fi
}

run_specific_test() {
    local test_name="$1"
    
    setup
    
    if declare -f "$test_name" > /dev/null; then
        "$test_name"
    else
        echo -e "${RED}Unknown test: $test_name${NC}"
        echo ""
        echo "Available tests:"
        declare -F | grep "test_" | awk '{print "  " $3}'
        return 1
    fi
    
    cleanup
}

# =============================================================================
# MAIN
# =============================================================================

main() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            --test|-t)
                SPECIFIC_TEST="$2"
                shift 2
                ;;
            --help|-h)
                echo "Usage: $0 [--verbose] [--test <test-name>]"
                echo ""
                echo "Options:"
                echo "  --verbose, -v    Show detailed output"
                echo "  --test, -t       Run specific test"
                echo "  --help, -h       Show this help"
                exit 0
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    # Check spawn script exists
    if [[ ! -x "$SPAWN_SCRIPT" ]]; then
        echo -e "${RED}Error: spawn-project.sh not found or not executable${NC}"
        echo "Expected at: $SPAWN_SCRIPT"
        exit 1
    fi
    
    if [[ -n "$SPECIFIC_TEST" ]]; then
        run_specific_test "$SPECIFIC_TEST"
    else
        run_all_tests
    fi
}

main "$@"
