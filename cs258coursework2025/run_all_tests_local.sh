#!/bin/bash

# Script to run all task tests in local environment
# Usage: ./run_all_tests_local.sh

echo "=========================================="
echo "Running All Task Tests (Local Environment)"
echo "=========================================="
echo ""

# Check if database exists
echo "Checking database connection..."
/opt/homebrew/opt/postgresql@16/bin/psql -d cwk -c "SELECT 1;" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "Error: Database 'cwk' does not exist or connection failed."
    echo "Please create the database first:"
    echo "  createdb cwk"
    echo "Or connect to postgres and run: CREATE DATABASE cwk;"
    exit 1
fi

# Test tasks 1-6 (using testbig.sql)
echo "=== Testing Tasks 1-6 (using testbig.sql) ==="
echo "Resetting database with testbig.sql..."
./run_local.sh reset -f tests/testbig.sql > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo ""
    PASSED=0
    FAILED=0
    for i in {1..6}; do
        echo "----------------------------------------"
        echo "Testing Task $i"
        echo "----------------------------------------"
        OUTPUT=$(./run_local.sh test $i 2>&1)
        TEST_RESULT=$?
        if echo "$OUTPUT" | grep -q "Test $i status: true\|Test passed\|All tests passed"; then
            echo "✓ Task $i: PASSED"
            ((PASSED++))
        else
            echo "✗ Task $i: FAILED"
            echo "$OUTPUT" | grep -E "Test failed|Exception|ERROR" | head -3
            ((FAILED++))
        fi
        echo ""
    done
    echo "Tasks 1-6 Summary: $PASSED passed, $FAILED failed"
else
    echo "Failed to reset database with testbig.sql"
    exit 1
fi

# Test tasks 7-8 (using testsmall.sql)
echo ""
echo "=== Testing Tasks 7-8 (using testsmall.sql) ==="
echo "Resetting database with testsmall.sql..."
./run_local.sh reset -f tests/testsmall.sql > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo ""
    for i in {7..8}; do
        echo "----------------------------------------"
        echo "Testing Task $i"
        echo "----------------------------------------"
        OUTPUT=$(./run_local.sh test $i 2>&1)
        TEST_RESULT=$?
        if echo "$OUTPUT" | grep -q "Test $i status: true\|Test passed\|All tests passed"; then
            echo "✓ Task $i: PASSED"
            ((PASSED++))
        else
            echo "✗ Task $i: FAILED"
            echo "$OUTPUT" | grep -E "Test failed|Exception|ERROR" | head -3
            ((FAILED++))
        fi
        echo ""
    done
else
    echo "Failed to reset database with testsmall.sql"
    exit 1
fi

echo "=========================================="
echo "Final Summary: $PASSED passed, $FAILED failed out of 8 tasks"
echo "=========================================="

