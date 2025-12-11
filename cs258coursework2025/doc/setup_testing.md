# Database Setup and Testing Guide

This guide walks you through setting up the database, activating it, and testing all functionalities of the GigSystem.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Database Setup](#database-setup)
3. [Project Setup](#project-setup)
4. [Database Activation](#database-activation)
5. [Testing Individual Tasks](#testing-individual-tasks)
6. [Interactive Testing via Menu](#interactive-testing-via-menu)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Before starting, ensure you have:
- PostgreSQL server installed and running
- Java 11 or higher
- Maven installed
- Access to the university server (for testing)

---

## Database Setup

### Step 1: Start PostgreSQL Server

On the university server, start the PostgreSQL service:
```bash
# The exact command depends on your system setup
# Typically, PostgreSQL should already be running for CS258 labs
```

### Step 2: Create the Database

Connect to PostgreSQL and create the `cwk` database:

```bash
# Connect to PostgreSQL (adjust path if needed)
/modules/cs258/bin/psql postgres

# In psql prompt, create the database:
CREATE DATABASE cwk;

# Exit psql
\q
```

**Important**: Do NOT include `CREATE DATABASE` in your `schema.sql` file. The marking system will create the database for you.

### Step 3: Verify Database Creation

```bash
# List all databases to confirm 'cwk' exists
/modules/cs258/bin/psql postgres -c "\l" | grep cwk
```

---

## Project Setup

### Step 1: Install Dependencies

Navigate to the project directory and install Maven dependencies:

```bash
cd /path/to/cs258coursework2025
mvn install
```

This downloads all required dependencies (PostgreSQL JDBC driver, OpenCSV, etc.).

### Step 2: Make run.sh Executable

```bash
chmod u+x run.sh
```

### Step 3: Create Database Schema

**Before testing, you must create the database schema in `schema.sql`.**

The schema should include:
- `ACT` table
- `VENUE` table
- `GIG` table
- `ACT_GIG` table
- `GIG_TICKET` table
- `TICKET` table
- All necessary sequences, primary keys, and foreign keys

Once your schema is ready, load it:

```bash
./run.sh reset -f tests/testbig.sql
```

This command:
1. Executes `schema.sql` to create/reset tables
2. Executes `reset-data.sql` to reset sequences to 10001
3. Loads test data from the specified file

---

## Database Activation

### Connection Methods

The system supports two connection methods:

#### Method 1: Unix Socket Connection (Default)
- Used on university servers
- Path: `~/cs258-postgres/postgres/tmp/.s.PGSQL.5432`
- Method: `getSocketConnection()`

#### Method 2: TCP/IP Port Connection
- Used for local development
- Host: `127.0.0.1:5432`
- Username: `postgres`
- Password: `password`
- Method: `getPortConnection()`

### Switching Connection Methods

In `GigSystem.java`, line 22, change:
```java
Connection conn = getSocketConnection();  // For university server
// OR
Connection conn = getPortConnection();     // For local development
```

### Verify Connection

Run the program to test the connection:
```bash
./run.sh
```

If connection fails, check:
- PostgreSQL server is running
- Database `cwk` exists
- Connection method matches your environment

---

## Testing Individual Tasks

### Automated Testing

The project includes a test suite (`GigTester.java`) that can be run via command line.

#### Test Data Files

- **`tests/testbig.sql`**: 50 gigs - Use for tasks 1-6
- **`tests/testsmall.sql`**: 5 gigs - Use for tasks 7-8

#### Running Tests

**Test Task 1:**
```bash
# Reset database with appropriate test data
./run.sh reset -f tests/testbig.sql

# Run test
./run.sh test 1
```

**Test Task 2:**
```bash
./run.sh reset -f tests/testbig.sql && ./run.sh test 2
```

**Test Task 3:**
```bash
./run.sh reset -f tests/testbig.sql && ./run.sh test 3
```

**Test Task 4:**
```bash
./run.sh reset -f tests/testbig.sql && ./run.sh test 4
```

**Test Task 5:**
```bash
./run.sh reset -f tests/testbig.sql && ./run.sh test 5
```

**Test Task 6:**
```bash
./run.sh reset -f tests/testbig.sql && ./run.sh test 6
```

**Test Task 7:**
```bash
./run.sh reset -f tests/testsmall.sql && ./run.sh test 7
```

**Test Task 8:**
```bash
./run.sh reset -f tests/testsmall.sql && ./run.sh test 8
```

#### Test All Tasks at Once

```bash
# Test tasks 1-6
for i in {1..6}; do
    echo "Testing Task $i..."
    ./run.sh reset -f tests/testbig.sql && ./run.sh test $i
done

# Test tasks 7-8
for i in {7..8}; do
    echo "Testing Task $i..."
    ./run.sh reset -f tests/testsmall.sql && ./run.sh test $i
done
```

### Expected Test Results

- **Task 1**: Should return `true` with correct schedule for gigID 11
- **Task 2**: Test implementation needed (currently returns `false`)
- **Task 3**: Test implementation needed (currently returns `false`)
- **Task 4**: Test implementation needed (currently returns `false`)
- **Task 5**: Should return `true` with correct ticket counts
- **Task 6**: Should return `true` with correct act statistics
- **Task 7**: Should return `true` with correct act-customer pairs
- **Task 8**: Should return `true` with correct venue-act-seat data

---

## Interactive Testing via Menu

### Running the Interactive Menu

```bash
./run.sh
```

This starts the GigSystem menu interface.

### Menu Options

Currently implemented:
- **1**: View Gig Schedule (Task 1)
- **q**: Quit

### Testing Task 1 via Menu

1. Run the program:
   ```bash
   ./run.sh
   ```

2. You'll see the menu:
   ```
   _________________________
   ________GigSystem________
   _________________________
   1: View Gig Schedule
   q: Quit
   Please choose an option:
   ```

3. Enter `1` to test Task 1

4. When prompted, enter a gig ID (e.g., `11`)

5. The schedule will be displayed:
   ```
   Gig Schedule for Gig ID 11:
   ViewBee 40          ,18:00              ,18:50              
   The Where           ,19:00              ,20:10              
   The Selecter        ,20:25              ,21:25              
   ```

6. Enter `q` to quit

### Testing Other Tasks (After Implementation)

Once other tasks are implemented, they will appear in the menu. Follow similar steps:
- Select the task number
- Enter required inputs when prompted
- View the results

---

## Advanced Testing

### Generate Random Test Data

Generate test data with a specific seed:
```bash
./run.sh reset -r 42
```

This:
- Generates random data based on seed 42
- Saves to `tmp/testData-YYYYMMDD-HHMMSS-42.sql`
- Loads the data into the database

Use seed `-1` for truly random data:
```bash
./run.sh reset -r -1
```

### Save Database State

After modifying data, save the current state:
```bash
/modules/cs258/bin/pg_dump cwk --data-only > databasestate.sql
```

Reload saved state:
```bash
./run.sh reset -f databasestate.sql
```

### Manual Database Inspection

Connect to the database to inspect data:
```bash
/modules/cs258/bin/psql cwk
```

Useful commands:
```sql
-- List all tables
\dt

-- View ACT table
SELECT * FROM ACT;

-- View GIG table
SELECT * FROM GIG;

-- View ACT_GIG table
SELECT * FROM ACT_GIG;

-- Count records
SELECT COUNT(*) FROM GIG;
SELECT COUNT(*) FROM ACT_GIG;
SELECT COUNT(*) FROM TICKET;

-- Exit
\q
```

---

## Troubleshooting

### Problem: "Database does not exist"

**Solution:**
```bash
/modules/cs258/bin/psql postgres -c "CREATE DATABASE cwk;"
```

### Problem: "Connection refused" or "Connection failed"

**Solutions:**
1. Check PostgreSQL is running
2. Verify connection method in `GigSystem.java` matches your environment
3. For socket connection, verify path exists:
   ```bash
   ls ~/cs258-postgres/postgres/tmp/.s.PGSQL.5432
   ```
4. For port connection, verify PostgreSQL is listening on port 5432

### Problem: "Table does not exist"

**Solution:**
- Ensure `schema.sql` contains all CREATE TABLE statements
- Reset the database:
  ```bash
  ./run.sh reset -f tests/testbig.sql
  ```

### Problem: "Sequence does not exist"

**Solution:**
- Ensure `schema.sql` creates all sequences
- Check `reset-data.sql` references correct sequence names

### Problem: "Test fails with wrong data"

**Solutions:**
1. Ensure you're using the correct test data file:
   - Tasks 1-6: `tests/testbig.sql`
   - Tasks 7-8: `tests/testsmall.sql`
2. Reset database before testing:
   ```bash
   ./run.sh reset -f tests/testbig.sql && ./run.sh test 1
   ```
3. Verify test data hasn't been modified

### Problem: "Permission denied" when running run.sh

**Solution:**
```bash
chmod u+x run.sh
```

### Problem: "Maven dependencies not found"

**Solution:**
```bash
mvn clean install
```

### Problem: "Compilation errors"

**Solutions:**
1. Check Java version (should be 11+):
   ```bash
   java -version
   ```
2. Clean and rebuild:
   ```bash
   mvn clean compile
   ```

### Problem: "No schedule found" for valid gig ID

**Solutions:**
1. Verify test data is loaded:
   ```bash
   /modules/cs258/bin/psql cwk -c "SELECT * FROM GIG WHERE gigid = 11;"
   ```
2. Check ACT_GIG has entries:
   ```bash
   /modules/cs258/bin/psql cwk -c "SELECT * FROM ACT_GIG WHERE gigid = 11;"
   ```
3. Reset database if needed

---

## Testing Checklist

Before submitting, verify:

- [ ] Database `cwk` exists and is accessible
- [ ] Schema is created (all tables exist)
- [ ] Test data loads successfully
- [ ] Task 1 passes automated test
- [ ] Task 1 works via interactive menu
- [ ] All other tasks are implemented
- [ ] All tasks pass their respective tests
- [ ] No compilation errors
- [ ] No runtime exceptions
- [ ] Connection works on university server

---

## Quick Reference Commands

```bash
# Setup
mvn install
chmod u+x run.sh

# Database operations
/modules/cs258/bin/psql postgres -c "CREATE DATABASE cwk;"
./run.sh reset -f tests/testbig.sql

# Testing
./run.sh test 1                    # Test task 1
./run.sh reset -f tests/testbig.sql && ./run.sh test 1  # Reset and test

# Interactive
./run.sh                           # Run menu system

# Data generation
./run.sh reset -r 42               # Generate with seed 42
```

---

## Next Steps

1. **Complete schema.sql**: Create all tables, sequences, and constraints
2. **Implement remaining tasks**: Tasks 2-8
3. **Wire tasks to menu**: Add menu options for each task
4. **Test thoroughly**: Run all automated tests
5. **Document**: Fill in README.md with design choices and implementations

For detailed task implementation documentation, see `/tests/README.md`.

