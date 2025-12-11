# Task Implementation Documentation

## Task 1: View Gig Schedule

### Overview
Task 1 retrieves and displays the schedule of acts performing at a specific gig, showing when each act starts and ends.

### Function Signature
```java
public static String[][] task1(Connection conn, int gigID)
```

### Input Parameters
- `conn`: Database connection object
- `gigID`: Integer ID of the gig to view schedule for

### Output Format
Returns a 2D String array with the following structure:
- **Rows**: One row per act performing at the gig
- **Columns**: 
  1. Act name (String)
  2. On time (String, format: "HH:mm")
  3. Off time (String, format: "HH:mm")

### Example Output
For `gigID = 11`:
```java
{
    {"ViewBee 40", "18:00", "18:50"},
    {"The Where", "19:00", "20:10"},
    {"The Selecter", "20:25", "21:25"}
}
```

### SQL Query Implementation

The query performs the following operations:

1. **Joins Tables**: 
   - `ACT_GIG` (aliased as `ag`) - contains act performance details
   - `ACT` (aliased as `a`) - contains act names

2. **Selects Columns**:
   - `a.actname` - The name of the act
   - `TO_CHAR(ag.ontime, 'HH24:MI')` - Formats the start time to HH:mm format
   - `TO_CHAR(ag.ontime + (ag.duration || ' minutes')::INTERVAL, 'HH24:MI')` - Calculates end time by adding duration to start time, then formats to HH:mm

3. **Filters**: 
   - `WHERE ag.gigid = ?` - Only gets acts for the specified gig

4. **Orders**: 
   - `ORDER BY ag.ontime ASC` - Sorts acts by start time (earliest first)

### Key Implementation Details

#### Time Formatting
- Uses PostgreSQL's `TO_CHAR()` function to format timestamps
- `'HH24:MI'` format specifier:
  - `HH24`: 24-hour format (00-23)
  - `MI`: Minutes (00-59)
- Example: `2017-12-26 18:00:00` → `"18:00"`

#### Duration Calculation
- Converts duration (in minutes) to PostgreSQL INTERVAL type
- Syntax: `(ag.duration || ' minutes')::INTERVAL`
- Adds interval to ontime: `ag.ontime + interval`
- Formats result to HH:mm using `TO_CHAR()`
- Example: `18:00:00` + `50 minutes` = `18:50:00` → `"18:50"`

#### Resource Management
- Uses try-with-resources for automatic resource cleanup
- Nested try blocks:
  - Outer: Manages `PreparedStatement` lifecycle
  - Inner: Manages `ResultSet` lifecycle
- Ensures connections are properly closed even if exceptions occur

#### Security
- Uses parameterized queries (`PreparedStatement`) with `?` placeholder
- Prevents SQL injection attacks
- Sets parameter using `stmt.setInt(1, gigID)`

### Error Handling
- Catches `SQLException` and prints stack trace for debugging
- Returns `null` on error (could be changed to return empty array `new String[0][3]`)

### Testing
- Test data: Use `testbig.sql` for testing
- Test command: `./run.sh test 1`
- Expected test case: `gigID = 11` should return 3 acts in chronological order

### Dependencies
- Requires `ACT` and `ACT_GIG` tables to exist in database
- Uses helper method `convertResultToStrings(ResultSet rs)` to convert database results to String array

