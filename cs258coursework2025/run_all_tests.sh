#!/bin/bash

# Script to run all task tests
# Usage: ./run_all_tests.sh

echo "=========================================="
echo "Running All Task Tests"
echo "=========================================="
echo ""

# Test tasks 1-6 (using testbig.sql)
echo "=== Testing Tasks 1-6 (using testbig.sql) ==="
echo "Resetting database with testbig.sql..."
./run.sh reset -f tests/testbig.sql

if [ $? -eq 0 ]; then
    echo ""
    for i in {1..6}; do
        echo "----------------------------------------"
        echo "Testing Task $i"
        echo "----------------------------------------"
        ./run.sh test $i
        echo ""
    done
else
    echo "Failed to reset database with testbig.sql"
    exit 1
fi

# Test tasks 7-8 (using testsmall.sql)
echo "=== Testing Tasks 7-8 (using testsmall.sql) ==="
echo "Resetting database with testsmall.sql..."
./run.sh reset -f tests/testsmall.sql

if [ $? -eq 0 ]; then
    echo ""
    for i in {7..8}; do
        echo "----------------------------------------"
        echo "Testing Task $i"
        echo "----------------------------------------"
        ./run.sh test $i
        echo ""
    done
else
    echo "Failed to reset database with testsmall.sql"
    exit 1
fi

echo "=========================================="
echo "All tests completed!"
echo "=========================================="

