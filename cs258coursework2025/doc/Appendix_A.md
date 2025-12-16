# Appendix A: Use of Large Language Models and Generative AI Tools

## AI Tool Used
- **Name and Version**: Claude Sonnet 4.5 / Grok
- **Purpose**: Code generation, debugging, and documentation assistance for database schema design, SQL query optimization, Java JDBC integration, and technical documentation

## Prompts and Responses

### Prompt 1
**Date and Time**: 2025-12-01 14:30:00
**Task**: Database trigger debugging for Task 2
**Prompt**:
```
You are an expert PostgreSQL developer working on a gig management system. I have a trigger `validate_final_act_duration()` that's failing when inserting the first act into a gig. The error shows "Final act must finish at least 60 minutes after gig start, but only 30 minutes". Help me analyze why the trigger is validating the first act instead of waiting for all acts to be inserted.

The trigger code:
```sql
CREATE OR REPLACE FUNCTION validate_final_act_duration()
RETURNS TRIGGER AS $$
DECLARE
    gig_start_time TIMESTAMP;
    final_act_end_time TIMESTAMP;
    duration_minutes NUMERIC;
BEGIN
    SELECT gigdatetime INTO gig_start_time FROM GIG WHERE gigid = NEW.gigid;

    SELECT MAX(ontime + (duration || ' minutes')::INTERVAL) INTO final_act_end_time
    FROM ACT_GIG WHERE gigid = NEW.gigid;

    IF final_act_end_time IS NULL OR (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) >= final_act_end_time THEN
        duration_minutes := EXTRACT(EPOCH FROM ((NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) - gig_start_time)) / 60;
        IF duration_minutes < 60 THEN
            RAISE EXCEPTION 'Final act must finish at least 60 minutes after gig start, but only % minutes', duration_minutes;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```
```
**Response**: You're encountering a classic issue with BEFORE triggers and sequential data insertion. Here's the detailed analysis:

The problem occurs because PostgreSQL BEFORE triggers execute before the row is actually inserted into the table. When inserting the first act:

1. `SELECT MAX(ontime + (duration || ' minutes')::INTERVAL) INTO final_act_end_time FROM ACT_GIG WHERE gigid = NEW.gigid;` returns NULL because no acts exist yet for this gig.

2. The condition `final_act_end_time IS NULL OR (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) >= final_act_end_time` evaluates to TRUE (NULL check passes).

3. The trigger then calculates duration as 30 minutes and rejects it.

**Solution Options:**

**Option 1: Restructure the validation logic**
```sql
IF existing_act_count > 0 AND final_act_end_time IS NOT NULL
   AND new_act_end_time >= final_act_end_time THEN
    -- Only validate when this act is definitely the final act
    duration_minutes := EXTRACT(EPOCH FROM (new_act_end_time - gig_start_time)) / 60;
    IF duration_minutes < 60 THEN
        RAISE EXCEPTION 'Final act must finish at least 60 minutes after gig start, but only % minutes', duration_minutes;
    END IF;
END IF;
```

**Option 2: Use AFTER triggers with table-level validation**
Consider moving to AFTER INSERT triggers that validate the entire gig state after all acts are inserted.

**Option 3: Application-level validation**
Move the final act duration check to Java code after all ACT_GIG inserts complete.

I recommend Option 1 as it maintains database-level enforcement while fixing the logic flaw.

### Prompt 2
**Date and Time**: 2025-12-01 15:45:00
**Task**: SQL query optimization for Task 5
**Prompt**:
```
As a PostgreSQL specialist, help me optimize this Task 5 query that calculates break-even ticket sales. The current CTE approach works but I want to ensure it's efficient for large datasets.

Current query structure:
```sql
WITH act_fees_per_gig AS (
    SELECT gigid, actid, MAX(actgigfee) as act_fee
    FROM ACT_GIG GROUP BY gigid, actid
),
gig_costs AS (
    SELECT g.gigid, COALESCE(SUM(af.act_fee), 0) + v.hirecost as total_cost
    FROM GIG g LEFT JOIN VENUE v ON g.venueid = v.venueid
    LEFT JOIN act_fees_per_gig af ON g.gigid = af.gigid
    GROUP BY g.gigid, v.hirecost
)
SELECT gc.gigid, CEIL((gc.total_cost - COALESCE(gr.total_revenue, 0))::NUMERIC / gcp.cheapest_price)
FROM gig_costs gc
LEFT JOIN gig_revenue gr ON gc.gigid = gr.gigid
LEFT JOIN gig_cheapest_price gcp ON gc.gigid = gcp.gigid
ORDER BY gc.gigid ASC;
```

What indexing recommendations do you have for this query pattern?
```
**Response**: Your Task 5 query is well-architected with CTEs, but let's optimize it for production performance. Here's a comprehensive indexing strategy:

**Recommended Indexes:**
```sql
-- Primary composite indexes for JOIN performance
CREATE INDEX idx_act_gig_gigid_actid ON ACT_GIG(gigid, actid);
CREATE INDEX idx_gig_venueid ON GIG(venueid);
CREATE INDEX idx_ticket_gigid ON TICKET(gigid);
CREATE INDEX idx_gig_ticket_gigid_pricetype ON GIG_TICKET(gigid, pricetype);

-- Covering indexes for CTE queries
CREATE INDEX idx_act_gig_gigid_actid_fee ON ACT_GIG(gigid, actid, actgigfee);
CREATE INDEX idx_ticket_gigid_cost ON TICKET(gigid, cost);
CREATE INDEX idx_gig_ticket_gigid_price ON GIG_TICKET(gigid, MIN(price));
```

**Performance Analysis:**
1. **act_fees_per_gig CTE**: Benefits from `ACT_GIG(gigid, actid, actgigfee)` composite index
2. **gig_costs CTE**: `GIG(venueid)` and the act_fees JOIN will be optimized
3. **Final query**: The multiple LEFT JOINs will perform well with proper indexing

**Additional Optimizations:**
- Consider `VACUUM ANALYZE` after bulk data loads
- Use `EXPLAIN ANALYZE` to verify query plans
- For very large datasets, consider partitioning the GIG table by date

**Query Execution Tips:**
- PostgreSQL CTEs are optimization fences - they prevent some query rewrites
- Consider rewriting as subqueries if performance issues arise
- Monitor with `pg_stat_statements` for slow query identification

### Prompt 3
**Date and Time**: 2025-12-02 09:15:00
**Task**: Java transaction management for Task 4
**Prompt**:
```
I'm implementing Task 4 (act cancellation) in Java using JDBC. I need to handle two scenarios: cancel just the act or cancel the entire gig. How should I structure the transaction management?

Current approach:
```java
boolean originalAutoCommit = conn.getAutoCommit();
try {
    conn.setAutoCommit(false);
    // ... validation and operations ...
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
} finally {
    conn.setAutoCommit(originalAutoCommit);
}
```

Is this pattern correct for handling multiple database operations that must be atomic?
```
**Response**: Your transaction management pattern is solid, but let's enhance it for robustness and error recovery:

**Complete Transaction Pattern:**
```java
public void task4(Connection conn, int gigID, String actName) {
    boolean originalAutoCommit = true;
    Savepoint savepoint = null;

    try {
        // Store original auto-commit setting
        originalAutoCommit = conn.getAutoCommit();

        // Disable auto-commit for manual transaction control
        conn.setAutoCommit(false);

        // Optional: Create savepoint for partial rollback capability
        savepoint = conn.setSavepoint("before_cancellation");

        // ... validation logic ...

        if (isHeadline || wouldViolate) {
            // Situation B: Cancel entire gig
            cancelEntireGig(conn, gigID);
        } else {
            // Situation A: Cancel act only
            cancelActAndAdjustSchedule(conn, gigID, actId, totalCancelledDuration);
        }

        // All operations successful - commit the transaction
        conn.commit();
        return result;

    } catch (SQLException e) {
        // Log the error with context
        logger.error("Task 4 failed for gig {} act {}", gigID, actName, e);

        try {
            // Rollback to savepoint if available, otherwise full rollback
            if (savepoint != null) {
                conn.rollback(savepoint);
            } else {
                conn.rollback();
            }
        } catch (SQLException rollbackEx) {
            logger.error("Failed to rollback transaction", rollbackEx);
        }

        return null; // Indicate failure

    } finally {
        // Always restore original auto-commit setting
        try {
            conn.setAutoCommit(originalAutoCommit);
        } catch (SQLException e) {
            logger.error("Failed to restore auto-commit setting", e);
        }
    }
}
```

**Best Practices:**
1. **Savepoints**: Use for complex operations where you might want partial rollback
2. **Resource Management**: Ensure connections are properly closed
3. **Error Logging**: Log errors with sufficient context for debugging
4. **Connection State**: Always restore original connection settings
5. **Exception Handling**: Catch specific exceptions where possible

### Prompt 4
**Date and Time**: 2025-12-02 11:30:00
**Task**: Business rule enforcement strategy
**Prompt**:
```
For our gig management system, we have 17 business rules. Should we enforce them at the database level (triggers/constraints) or application level (Java validation)? What's the best practice for each type of rule?

Examples:
- Venue capacity limits
- Act simultaneous performance prevention
- Fee consistency per act per gig
- Gig start time validation
```
**Response**: Excellent question about enforcement strategy. Here's a comprehensive breakdown:

**Database-Level Enforcement (Recommended for most rules):**
- **Data Integrity Rules**: Capacity limits, overlapping performances, fee consistency, foreign key constraints
- **Always-Applicable Rules**: Rules that must be enforced regardless of application
- **Performance**: Database-level validation is faster for bulk operations
- **Implementation**: CHECK constraints, triggers, unique constraints

**Application-Level Enforcement:**
- **User Experience**: Immediate feedback, custom error messages
- **Complex Logic**: Rules requiring external API calls or complex calculations
- **Conditional Rules**: Rules that vary by user role or business context
- **Testing**: Easier to unit test application logic

**Your Specific Examples:**

**Venue Capacity (Database):**
```sql
CREATE TRIGGER check_venue_capacity
    BEFORE INSERT ON TICKET
    FOR EACH ROW EXECUTE FUNCTION validate_venue_capacity();
```
*Why database?* Must be enforced even for direct SQL inserts or other applications.

**Act Simultaneous Performance (Database):**
```sql
CREATE TRIGGER prevent_simultaneous_gigs
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW EXECUTE FUNCTION prevent_act_simultaneous_gigs();
```
*Why database?* Prevents invalid states regardless of how data is inserted.

**Fee Consistency (Database):**
```sql
CREATE TRIGGER validate_act_fee_per_gig
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW EXECUTE FUNCTION validate_act_fee_per_gig();
```
*Why database?* Business rule that ensures data consistency.

**Gig Start Time (Application):**
```java
if (hour < 9 || hour > 23 || (hour == 23 && minute > 59)) {
    throw new IllegalArgumentException("Gig must start between 9am and 11:59pm");
}
```
*Why application?* User input validation with immediate feedback.

**Hybrid Approach (Recommended):**
Use both layers: application for UX, database for integrity. This provides defense in depth and ensures data consistency regardless of access method.

### Prompt 5
**Date and Time**: 2025-12-02 14:20:00
**Task**: PostgreSQL interval arithmetic for time calculations
**Prompt**:
```
In our PostgreSQL schema, we need to calculate act end times using:
```sql
ontime + (duration || ' minutes')::INTERVAL
```

Is this the most efficient way to add minutes to a timestamp? Are there performance implications for large datasets? What are the alternatives?
```
**Response**: Your interval arithmetic approach is correct and efficient. Let's break down the options:

**Your Current Approach:**
```sql
ontime + (duration || ' minutes')::INTERVAL
```
- ✅ **Correct**: Properly converts duration to interval
- ✅ **Readable**: Clear intent
- ✅ **Efficient**: PostgreSQL optimizes interval operations well

**Alternative Approaches:**

**Option 1: Explicit INTERVAL multiplication**
```sql
ontime + (INTERVAL '1 minute' * duration)
```
- **Pros**: More explicit, better type safety
- **Cons**: Slightly more verbose
- **Performance**: Equivalent to your approach

**Option 2: Make interval first**
```sql
ontime + make_interval(mins => duration)
```
- **Pros**: Very explicit, self-documenting
- **Cons**: PostgreSQL 9.4+ only
- **Performance**: Good, but function call overhead

**Performance Considerations:**
1. **Indexing**: Ensure timestamp columns are indexed for range queries
2. **Type Consistency**: All duration values should be same unit (minutes)
3. **Edge Cases**: Handle NULL durations gracefully

**Recommended Enhancements:**
```sql
-- Add constraints to ensure data quality
ALTER TABLE ACT_GIG ADD CONSTRAINT duration_positive
    CHECK (duration >= 15 AND duration <= 90);

-- Index for performance
CREATE INDEX idx_act_gig_ontime ON ACT_GIG(ontime);
CREATE INDEX idx_act_gig_end_time ON ACT_GIG(ontime + (duration || ' minutes')::INTERVAL);
```

**Query Optimization:**
For complex time-based queries, consider generated columns:
```sql
ALTER TABLE ACT_GIG ADD COLUMN end_time GENERATED ALWAYS AS
    (ontime + (duration || ' minutes')::INTERVAL) STORED;
CREATE INDEX idx_act_gig_end_time ON ACT_GIG(end_time);
```

### Prompt 6
**Date and Time**: 2025-12-03 10:00:00
**Task**: CTE optimization for complex queries
**Prompt**:
```
I'm using multiple CTEs in Task 5 for break-even calculations. Is there a performance difference between CTEs and subqueries? When should I prefer one over the other?

The query has 4 CTEs chained together:
act_fees_per_gig -> gig_costs -> final calculation with revenue and price joins
```
**Response**: CTEs vs subqueries is a nuanced performance question in PostgreSQL. Here's the detailed analysis:

**Performance Characteristics:**

**CTEs (Common Table Expressions):**
```sql
WITH act_fees AS (
    SELECT gigid, actid, MAX(actgigfee) as fee
    FROM ACT_GIG
    GROUP BY gigid, actid
)
SELECT * FROM act_fees WHERE fee > 1000;
```

**Equivalent Subquery:**
```sql
SELECT * FROM (
    SELECT gigid, actid, MAX(actgigfee) as fee
    FROM ACT_GIG
    GROUP BY gigid, actid
) act_fees WHERE fee > 1000;
```

**Performance Differences:**
1. **Materialization**: CTEs can be materialized (written to temp storage) for complex cases
2. **Optimization**: PostgreSQL can optimize CTEs differently than subqueries
3. **Recursion**: Only CTEs support recursive queries
4. **Readability**: CTEs are generally more readable for complex logic

**When to Use CTEs:**
- ✅ Complex multi-step transformations
- ✅ Recursive queries (hierarchies, graphs)
- ✅ When you need to reference the same subquery multiple times
- ✅ Debugging complex logic (easier to isolate steps)
- ✅ Documentation (self-documenting query structure)

**When to Use Subqueries:**
- ✅ Simple single-use cases
- ✅ When CTE would force materialization
- ✅ Performance-critical sections where subquery optimization is better

**Your Specific Case:**
Your 4-CTE chain is perfect for CTEs because:
- Each step builds on the previous
- Complex aggregations and joins
- Multiple references to intermediate results
- Easier debugging and maintenance

**Optimization Tips:**
```sql
-- Force materialization if needed
WITH act_fees AS MATERIALIZED (
    SELECT gigid, actid, MAX(actgigfee) as fee
    FROM ACT_GIG
    GROUP BY gigid, actid
)
SELECT * FROM act_fees;

-- Use EXPLAIN ANALYZE to compare
EXPLAIN ANALYZE WITH ... SELECT ...;
EXPLAIN ANALYZE SELECT ... FROM (subquery) ...;
```

For your Task 5 query, CTEs are the right choice for maintainability and performance.

### Prompt 7
**Date and Time**: 2025-12-03 13:45:00
**Task**: Error handling in JDBC transactions
**Prompt**:
```
In Task 2, we have complex validation before database inserts. If validation fails, should we rollback immediately or continue to validate all rules first?

Current pattern:
```java
if (!venueExists) {
    conn.rollback();
    return;
}
if (!timeValid) {
    conn.rollback();
    return;
}
// ... more validations
```

Vs. collecting all errors first, then rollback once.
```
**Response**: This is an important design decision. Let's analyze the trade-offs:

**Fail-Fast Approach (Recommended for Data Integrity):**
```java
try {
    conn.setAutoCommit(false);

    // Check gig exists
    if (!gigExists(conn, gigID)) {
        conn.rollback();
        return null; // Fail immediately
    }

    // Check act exists
    if (!actExists(conn, actName)) {
        conn.rollback();
        return null; // Fail immediately
    }

    // ... more validation ...

    // All checks passed - proceed with operations
    performCancellation(conn, gigID, actId);
    conn.commit();

} catch (SQLException e) {
    conn.rollback();
    return null;
}
```

**Validate-All-First Approach (Better UX):**
```java
List<String> errors = new ArrayList<>();

if (!gigExists(conn, gigID)) {
    errors.add("Gig does not exist");
}
if (!actExists(conn, actName)) {
    errors.add("Act does not exist");
}
// ... collect all errors ...

if (!errors.isEmpty()) {
    throw new ValidationException("Validation failed: " + String.join(", ", errors));
}

// Proceed with operations...
```

**Recommendation for Your Use Case:**

**Use Fail-Fast for Database Operations:**
- **Why?** Database transactions should maintain atomicity
- **Why?** Prevents partial invalid states
- **Why?** Simpler error handling and rollback logic
- **Why?** Consistent with database constraints approach

**Use Validate-All-First for User Interfaces:**
- **Why?** Better user experience with comprehensive error messages
- **Why?** Allows users to fix multiple issues at once
- **Why?** More informative feedback

**Your Task 2 Implementation:**
Your current approach is correct for database integrity. The fail-fast pattern ensures that if any validation fails, the transaction is rolled back immediately, preventing any partial changes to the database.

**Enhanced Error Handling:**
```java
public String[][] task2(Connection conn, /* params */) {
    try {
        conn.setAutoCommit(false);

        // Validation phase - fail fast
        validateVenue(conn, venue);
        validateGigStartTime(gigStart);
        validateActs(conn, actDetails);
        // ... more validations ...

        // Execution phase
        insertGig(conn, venueId, gigTitle, gigStart);
        insertActGigs(conn, gigId, actDetails);
        insertGigTicket(conn, gigId, adultTicketPrice);

        conn.commit();
        return new String[0][0]; // Success

    } catch (ValidationException e) {
        conn.rollback();
        // Log validation error
        return null;
    } catch (SQLException e) {
        conn.rollback();
        // Log database error
        return null;
    }
}
```

### Prompt 8
**Date and Time**: 2025-12-03 16:30:00
**Task**: Database normalization assessment
**Prompt**:
```
Our current schema has these tables: ACT, VENUE, GIG, ACT_GIG, GIG_TICKET, TICKET. Is this in proper normal form? Should we normalize further?

For example:
- ACT.genre could be normalized to a separate GENRE table
- VENUE.capacity could have historical tracking
- ACT_GIG could be further normalized

What are the trade-offs?
```
**Response**: Let's analyze your current schema's normalization level and potential improvements:

**Current Schema Analysis:**

**Third Normal Form (3NF) Compliance:**
✅ **ACT table**: `actid` → `actname, genre, standardfee` (no transitive dependencies)
✅ **VENUE table**: `venueid` → `venuename, hirecost, capacity`
✅ **GIG table**: `gigid` → `venueid, gigtitle, gigdatetime, gigstatus`
✅ **ACT_GIG table**: `(actid, gigid, ontime)` → `actgigfee, duration`
✅ **GIG_TICKET table**: `(gigid, pricetype)` → `price`
✅ **TICKET table**: `ticketid` → `gigid, customername, customeremail, pricetype, cost`

**Potential Normalization Improvements:**

**1. Genre Normalization:**
```sql
-- Current: ACT.genre VARCHAR(100)
-- Proposed normalization:
CREATE TABLE GENRE (
    genreid SERIAL PRIMARY KEY,
    genrename VARCHAR(50) UNIQUE NOT NULL,
    noise_level CHAR(1) CHECK (noise_level IN ('L', 'M', 'H'))
);

ALTER TABLE ACT ADD COLUMN genreid INTEGER REFERENCES GENRE(genreid);
ALTER TABLE ACT DROP COLUMN genre; -- After data migration
```

**Benefits:**
- Eliminates genre string duplication
- Enables genre-based analytics
- Automates noise regulations (Business Rule 14)
- Supports genre hierarchies

**2. Venue Capacity History:**
```sql
CREATE TABLE VENUE_CAPACITY_HISTORY (
    venueid INTEGER REFERENCES VENUE(venueid),
    effective_date DATE NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    reason VARCHAR(255),
    PRIMARY KEY (venueid, effective_date)
);
```

**Benefits:**
- Tracks capacity changes over time
- Audit trail for venue modifications
- Historical reporting accuracy

**3. Enhanced Ticket Pricing:**
```sql
ALTER TABLE GIG_TICKET ADD COLUMN early_bird_deadline TIMESTAMP;
ALTER TABLE GIG_TICKET ADD COLUMN early_bird_price INTEGER;
ALTER TABLE GIG_TICKET ADD COLUMN pricing_tier VARCHAR(20) DEFAULT 'Standard';
```

**Benefits:**
- Supports dynamic pricing strategies
- Early bird discount automation
- Tiered seating support

**Denormalization Considerations:**

**When to Denormalize:**
- **Read-Heavy Workloads**: If genre lookups are extremely frequent
- **Performance Critical**: Sub-millisecond response requirements
- **Legacy System Integration**: Matching existing schemas

**Your Schema Decision:**
Keep current structure for coursework. Your schema demonstrates proper normalization principles while remaining practical. The suggested improvements would be valuable in production systems with:
- High transaction volumes
- Complex reporting requirements
- Long-term data retention needs

**Recommendation:**
For coursework, your current normalized schema is excellent. It shows understanding of relational design principles while maintaining practicality for the required operations.

### Prompt 9
**Date and Time**: 2025-12-04 09:15:00
**Task**: Index strategy for query optimization
**Prompt**:
```
For Task 1 (gig schedule retrieval), we query:
```sql
SELECT a.actname, TO_CHAR(ag.ontime, 'HH24:MI'), TO_CHAR(end_time, 'HH24:MI')
FROM ACT_GIG ag JOIN ACT a ON ag.actid = a.actid
WHERE ag.gigid = ?
ORDER BY ag.ontime ASC
```

What indexes should I create? Should I consider covering indexes or partial indexes?
```
**Response**: Let's design optimal indexes for your Task 1 query performance:

**Query Analysis:**
```sql
SELECT a.actname,
       TO_CHAR(ag.ontime, 'HH24:MI'),
       TO_CHAR(ag.ontime + (ag.duration || ' minutes')::INTERVAL, 'HH24:MI')
FROM ACT_GIG ag
JOIN ACT a ON ag.actid = a.actid
WHERE ag.gigid = ?
ORDER BY ag.ontime ASC
```

**Recommended Indexes:**

**1. Primary Composite Index:**
```sql
CREATE INDEX idx_act_gig_gigid_ontime_actid
ON ACT_GIG(gigid, ontime, actid);
```
- **Why?** Covers WHERE clause (`gigid = ?`) and ORDER BY (`ontime ASC`)
- **Why?** Includes `actid` for JOIN efficiency
- **Impact:** Transforms table scan to index-only scan

**2. Covering Index (Alternative):**
```sql
CREATE INDEX idx_act_gig_gigid_ontime_actname
ON ACT_GIG(gigid, ontime, actid)
INCLUDE (duration); -- PostgreSQL 11+
```
- **Why?** Includes all columns needed for the query
- **Why?** Eliminates table access after index lookup
- **Impact:** Fastest possible execution

**3. ACT Table Index:**
```sql
CREATE INDEX idx_act_actid_name ON ACT(actid, actname);
```
- **Why?** Optimizes the JOIN operation
- **Why?** Covers both join key and selected column

**4. Partial Index for Active Gigs:**
```sql
CREATE INDEX idx_act_gig_active_gigs
ON ACT_GIG(gigid, ontime, actid)
WHERE EXISTS (
    SELECT 1 FROM GIG g WHERE g.gigid = ACT_GIG.gigid AND g.gigstatus = 'G'
);
```
- **Why?** Smaller index for active gigs only
- **Why?** Reduces maintenance overhead
- **When to use:** If most queries filter by active gigs

**Performance Verification:**
```sql
-- Check index usage
EXPLAIN ANALYZE
SELECT a.actname,
       TO_CHAR(ag.ontime, 'HH24:MI') as start_time,
       TO_CHAR(ag.ontime + (ag.duration || ' minutes')::INTERVAL, 'HH24:MI') as end_time
FROM ACT_GIG ag
JOIN ACT a ON ag.actid = a.actid
WHERE ag.gigid = 1
ORDER BY ag.ontime ASC;

-- Expected: Index Only Scan or Index Scan (not Seq Scan)
```

**Additional Optimizations:**
1. **Statistics:** Run `ANALYZE ACT_GIG;` after index creation
2. **Fill Factor:** For write-heavy tables, consider `FILLFACTOR 90`
3. **Monitoring:** Use `pg_stat_user_indexes` to track usage
4. **Maintenance:** Regular `REINDEX` for performance

**Index Maintenance Considerations:**
- Indexes speed reads but slow writes
- Monitor for unused indexes: `pg_stat_user_indexes`
- Consider index-only scans for covering indexes
- Balance between read and write performance

### Prompt 10
**Date and Time**: 2025-12-04 12:00:00
**Task**: Trigger execution order and conflicts
**Prompt**:
```
We have multiple triggers on ACT_GIG table:
- validate_first_act_start (BEFORE INSERT)
- prevent_overlapping_performances (BEFORE INSERT)
- validate_interval_duration (BEFORE INSERT)
- validate_final_act_duration (BEFORE INSERT)

Do trigger execution order matter? Can triggers conflict with each other? How to debug trigger issues?
```
**Response**: Trigger execution order is critical in PostgreSQL. Here's the comprehensive guide:

**Execution Order Rules:**
1. **Alphabetical by name** within the same timing (BEFORE/AFTER) and event (INSERT/UPDATE/DELETE)
2. **Timing precedence**: BEFORE triggers execute before AFTER triggers
3. **Event specificity**: More specific events (INSERT OR UPDATE) before general ones

**Your Trigger Order:**
```sql
-- BEFORE INSERT triggers (alphabetical):
1. trigger_prevent_overlapping_performances
2. trigger_validate_act_gap
3. trigger_validate_act_travel_gap
4. trigger_validate_final_act_duration
5. trigger_validate_first_act_start
6. trigger_validate_gig_finish_time

-- AFTER INSERT triggers:
7. trigger_validate_venue_gap
8. trigger_validate_act_fee_per_gig
```

**Conflict Prevention Strategies:**

**1. Consistent Naming Convention:**
```sql
-- Use prefixes to control execution order
CREATE TRIGGER trigger_01_validate_first_act_start
CREATE TRIGGER trigger_02_prevent_overlapping_performances
CREATE TRIGGER trigger_03_validate_interval_duration
```

**2. Minimize Data Modification in Triggers:**
- Avoid UPDATE operations in triggers that modify the same table
- Use NEW.* assignments instead of UPDATE statements where possible
- Be aware of cascading trigger effects

**3. Trigger Isolation:**
- Each trigger should validate only its specific concern
- Avoid overlapping validation logic
- Use different error codes for different validation failures

**Debugging Techniques:**

**1. Add Debug Logging:**
```sql
CREATE OR REPLACE FUNCTION validate_first_act_start()
RETURNS TRIGGER AS $$
BEGIN
    RAISE NOTICE 'Trigger: validate_first_act_start fired for gig %', NEW.gigid;

    -- ... validation logic ...

    RAISE NOTICE 'Trigger: validate_first_act_start completed';
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**2. Check Execution Order:**
```sql
-- View trigger definitions
SELECT trigger_name, event_manipulation, action_timing, action_order
FROM information_schema.triggers
WHERE event_object_table = 'act_gig'
ORDER BY action_timing, event_manipulation, action_order;

-- Check PostgreSQL logs for RAISE NOTICE output
```

**3. Test Individual Triggers:**
```sql
-- Temporarily disable other triggers for testing
ALTER TABLE ACT_GIG DISABLE TRIGGER trigger_prevent_overlapping_performances;

-- Test specific trigger
INSERT INTO ACT_GIG VALUES (...);

-- Re-enable
ALTER TABLE ACT_GIG ENABLE TRIGGER trigger_prevent_overlapping_performances;
```

**Conflict Resolution:**
If triggers conflict, consider:
- **Consolidation**: Combine related validations into fewer triggers
- **Restructuring**: Move some validations to application layer
- **Deferrable Constraints**: Use DEFERRABLE INITIALLY DEFERRED for complex interdependencies

**Best Practices:**
1. Keep triggers simple and focused
2. Use consistent naming for predictable execution
3. Document trigger dependencies
4. Test trigger interactions thoroughly
5. Monitor performance impact of multiple triggers

### Prompt 11
**Date and Time**: 2025-12-04 15:30:00
**Task**: Java ResultSet processing optimization
**Prompt**:
```
In our task implementations, we collect all ResultSet data into ArrayList first, then convert to arrays. Is this efficient for large datasets?

Current pattern:
```java
List<String[]> results = new ArrayList<>();
while (rs.next()) {
    String[] row = new String[cols];
    // populate row
    results.add(row);
}
// then convert to String[][]
```

Alternatives?
```
**Response**: Your ResultSet processing pattern is appropriate for typical JDBC workloads. Let's analyze memory and performance implications:

**Your Current Pattern:**
```java
List<String[]> results = new ArrayList<>();
while (rs.next()) {
    String[] row = new String[columnCount];
    for (int i = 0; i < columnCount; i++) {
        row[i] = rs.getString(i + 1);
    }
    results.add(row);
}
// Convert to array
String[][] result = results.toArray(new String[0][]);
```

**Memory Considerations:**

**Pros of Current Approach:**
- ✅ **Simple and reliable** for typical result sets (< 10,000 rows)
- ✅ **Full control** over data conversion
- ✅ **Type safety** with String arrays
- ✅ **Easy debugging** - can inspect intermediate results

**Memory Usage Calculation:**
- **Base overhead**: ArrayList structure (~32 bytes)
- **Per row**: String[] array header (16 bytes) + String references
- **Per string**: String object (40 bytes) + char[] array + actual characters
- **Example**: 1,000 rows × 3 columns × average 20 chars = ~200KB

**Optimization Strategies:**

**1. For Large Datasets (100,000+ rows):**
```java
// Streaming approach
try (ResultSet rs = stmt.executeQuery()) {
    // Process one row at a time
    while (rs.next()) {
        processRow(rs.getString(1), rs.getString(2), rs.getString(3));
        // Don't accumulate in memory
    }
}
```

**2. Memory-Efficient Collection:**
```java
// Pre-size ArrayList for better performance
List<String[]> results = new ArrayList<>(estimatedSize);

// Or use a more memory-efficient structure
List<Object[]> results = new ArrayList<>();
while (rs.next()) {
    results.add(new Object[]{rs.getString(1), rs.getString(2), rs.getString(3)});
}
```

**3. Streaming with Pagination:**
```java
// For very large result sets
PreparedStatement stmt = conn.prepareStatement(
    "SELECT * FROM large_table LIMIT ? OFFSET ?");
stmt.setInt(1, pageSize);
stmt.setInt(2, offset);

// Process in chunks
```

**Performance Tips:**

**1. Connection Configuration:**
```java
// Optimize for read operations
Properties props = new Properties();
props.setProperty("defaultRowFetchSize", "100"); // Fetch 100 rows at a time
conn = DriverManager.getConnection(url, props);
```

**2. Statement Configuration:**
```java
stmt = conn.prepareStatement(sql);
stmt.setFetchSize(100); // JDBC fetch size
```

**3. ResultSet Optimization:**
```java
// Close ResultSet immediately after use
try (ResultSet rs = stmt.executeQuery()) {
    // Process results
} // Auto-closed
```

**When to Optimize:**
- **Small datasets (< 1,000 rows)**: Your current approach is perfect
- **Medium datasets (1,000 - 100,000 rows)**: Consider pre-sizing collections
- **Large datasets (> 100,000 rows)**: Implement streaming or pagination

**Alternative: Use Libraries**
For production systems, consider:
- **Spring JDBC**: `JdbcTemplate.queryForList()`
- **jOOQ**: Type-safe SQL with automatic conversion
- **Apache Commons DbUtils**: `QueryRunner` with `ResultSetHandler`

Your current implementation is solid for a coursework project. The ArrayList approach provides good balance between simplicity and performance for typical database operations.

### Prompt 12
**Date and Time**: 2025-12-05 10:45:00
**Task**: PreparedStatement parameter binding
**Prompt**:
```
Should I use ? placeholders or named parameters in JDBC prepared statements? Are there security or performance differences?

For queries like:
```java
SELECT * FROM ACT_GIG WHERE gigid = ? AND actid = ?
```

Vs. using named parameters if supported.
```
**Response**: Let's compare JDBC parameter binding approaches:

**Positional Parameters (Recommended):**
```java
PreparedStatement stmt = conn.prepareStatement(
    "SELECT * FROM ACT_GIG WHERE gigid = ? AND actid = ?");
stmt.setInt(1, gigId);
stmt.setInt(2, actId);
```

**Named Parameters (PostgreSQL-specific):**
```java
PreparedStatement stmt = conn.prepareStatement(
    "SELECT * FROM ACT_GIG WHERE gigid = :gigId AND actid = :actId");
// PostgreSQL syntax
((org.postgresql.PgPreparedStatement)stmt).setInt("gigId", gigId);
((org.postgresql.PgPreparedStatement)stmt).setInt("actId", actId);
```

**Security Analysis:**

**SQL Injection Prevention:**
- **Positional**: Parameters are bound separately from SQL text
- **Named**: Same security mechanism
- **Both**: Completely prevent SQL injection attacks

**Performance Comparison:**
- **Compilation**: Both use server-side prepared statements
- **Execution**: Identical performance
- **Caching**: Query plans cached by parameter signature

**Portability Considerations:**
- **Positional**: Works with all JDBC drivers
- **Named**: PostgreSQL-specific, not portable
- **Recommendation**: Use positional for cross-database compatibility

**Advanced Usage Patterns:**

**1. Dynamic Queries with Positional Parameters:**
```java
StringBuilder sql = new StringBuilder("SELECT * FROM ACT_GIG WHERE 1=1");
List<Object> params = new ArrayList<>();

if (gigId != null) {
    sql.append(" AND gigid = ?");
    params.add(gigId);
}
if (actId != null) {
    sql.append(" AND actid = ?");
    params.add(actId);
}

PreparedStatement stmt = conn.prepareStatement(sql.toString());
for (int i = 0; i < params.size(); i++) {
    stmt.setObject(i + 1, params.get(i));
}
```

**2. Batch Operations:**
```java
PreparedStatement stmt = conn.prepareStatement(
    "INSERT INTO TICKET (gigid, customername, cost) VALUES (?, ?, ?)");

for (Ticket ticket : tickets) {
    stmt.setInt(1, ticket.getGigId());
    stmt.setString(2, ticket.getCustomerName());
    stmt.setInt(3, ticket.getCost());
    stmt.addBatch();
}
stmt.executeBatch();
```

**3. Type Safety:**
```java
// Use setObject for automatic type conversion
stmt.setObject(1, gigId); // Automatically handles int conversion

// Or be explicit for clarity
stmt.setInt(1, gigId);
stmt.setString(2, actName);
stmt.setTimestamp(3, ontime);
```

**Best Practices:**
1. **Always use prepared statements** - never concatenate user input into SQL
2. **Set parameters in order** - positional parameters are 1-indexed
3. **Close statements** - use try-with-resources
4. **Reuse prepared statements** when possible for better performance
5. **Validate parameter types** before setting to catch errors early

**Your specific example** is well-implemented. The positional parameter approach is standard, secure, and portable.

### Prompt 13
**Date and Time**: 2025-12-05 14:15:00
**Task**: Database connection management
**Prompt**:
```
In our GigSystem class, we have multiple getConnection() methods:
- getSocketConnection() for university servers
- getPortConnection() for local development
- getConnection() that tries socket first, then port

Is this a good pattern? Should we use connection pooling? What are the best practices for JDBC connection management in a coursework context?
```
**Response**: Your connection management strategy has good development flexibility. Let's analyze production-ready approaches:

**Your Current Pattern:**
```java
public Connection getConnection() {
    try {
        // Try socket connection (university servers)
        Connection conn = getSocketConnection();
        if (conn != null && !conn.isClosed()) {
            return conn;
        }
    } catch (Exception e) {
        // Socket failed, try port
    }

    // Fallback to port connection (local development)
    return getPortConnection();
}
```

**Production Connection Management:**

**1. Connection Pooling (Recommended):**
```java
// HikariCP example
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost:5432/cwk");
config.setUsername("postgres");
config.setPassword("password");
config.setMaximumPoolSize(10);
config.setMinimumIdle(5);
config.setConnectionTimeout(30000);

HikariDataSource dataSource = new HikariDataSource(config);

// Usage
try (Connection conn = dataSource.getConnection()) {
    // Use connection
} // Automatically returned to pool
```

**2. Spring Framework Integration:**
```java
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/cwk");
        dataSource.setUsername("postgres");
        dataSource.setPassword("password");

        // Pool configuration
        dataSource.setMaximumPoolSize(20);
        dataSource.setMinimumIdle(5);
        dataSource.setIdleTimeout(300000);
        dataSource.setMaxLifetime(600000);

        return dataSource;
    }
}
```

**Connection Pool Benefits:**
- ✅ **Performance**: Reuses connections, avoids overhead
- ✅ **Resource Management**: Automatic cleanup and limits
- ✅ **Monitoring**: Built-in metrics and health checks
- ✅ **Configuration**: Connection limits, timeouts, validation

**Your Development Pattern Analysis:**

**Strengths:**
- ✅ **Environment Flexibility**: Adapts to different deployment scenarios
- ✅ **Graceful Degradation**: Falls back when primary connection fails
- ✅ **Simple Implementation**: Easy to understand and modify

**Areas for Improvement:**
- ⚠️ **Resource Leaks**: Manual connection management requires careful cleanup
- ⚠️ **Connection Limits**: No built-in limits on concurrent connections
- ⚠️ **Connection Validation**: No automatic health checking

**Enhanced Development Pattern:**
```java
public class ConnectionManager {

    private static final String SOCKET_URL = "jdbc:postgresql://localhost/cwk";
    private static final String PORT_URL = "jdbc:postgresql://127.0.0.1:5432/cwk";

    private static Connection createConnection(String url, Properties props) throws SQLException {
        return DriverManager.getConnection(url, props);
    }

    public static Connection getConnection() throws SQLException {
        // Try socket connection first (for university environment)
        try {
            Properties socketProps = new Properties();
            socketProps.setProperty("socketFactory", "org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg");
            socketProps.setProperty("socketFactoryArg", System.getenv("HOME") + "/cs258-postgres/postgres/tmp/.s.PGSQL.5432");

            Connection conn = createConnection(SOCKET_URL, socketProps);
            if (conn != null && !conn.isClosed()) {
                // Test connection with simple query
                try (PreparedStatement testStmt = conn.prepareStatement("SELECT 1")) {
                    testStmt.executeQuery();
                    return conn;
                }
            }
        } catch (Exception e) {
            // Socket connection failed, try port connection
            System.out.println("Socket connection failed, trying port connection...");
        }

        // Fallback to port connection
        Properties portProps = new Properties();
        portProps.setProperty("user", "postgres");
        portProps.setProperty("password", "password");

        Connection conn = createConnection(PORT_URL, portProps);
        if (conn != null && !conn.isClosed()) {
            return conn;
        }

        throw new SQLException("Failed to establish database connection");
    }
}
```

**Coursework vs Production:**
- **Coursework**: Your manual approach is acceptable and demonstrates understanding
- **Production**: Always use connection pooling for scalability and reliability

Your implementation shows good understanding of environment-specific configuration, which is valuable for real-world deployments.

### Prompt 14
**Date and Time**: 2025-12-06 09:30:00
**Task**: SQL injection prevention
**Prompt**:
```
How do prepared statements prevent SQL injection? Can you explain the mechanism?

For dynamic queries where we build SQL strings, what's the safe way to include user data?
```
**Response**: Let's break down how prepared statements prevent SQL injection attacks:

**The SQL Injection Problem:**
```java
// VULNERABLE - String concatenation
String sql = "SELECT * FROM ACT WHERE actname = '" + userInput + "'";
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery(sql);

// If userInput = "'; DROP TABLE ACT; --"
// Result: SELECT * FROM ACT WHERE actname = ''; DROP TABLE ACT; --'
```

**Prepared Statement Solution:**
```java
// SECURE - Parameter binding
String sql = "SELECT * FROM ACT WHERE actname = ?";
PreparedStatement stmt = conn.prepareStatement(sql);
stmt.setString(1, userInput); // Safe parameter binding
ResultSet rs = stmt.executeQuery();
```

**How It Works:**

**1. Parse Phase (Compilation):**
- SQL structure is parsed and compiled without parameter values
- Database creates execution plan for the parameterized query
- Malicious SQL cannot alter the query structure at this stage

**2. Bind Phase (Execution):**
- Parameter placeholders (?) are replaced with actual values
- Values are treated as literal data, not executable code
- Type checking ensures data integrity

**Technical Implementation:**
- **Server-side**: Prepared statements are pre-compiled on database server
- **Client-side**: JDBC driver handles parameter type conversion and escaping
- **Network**: Parameters sent separately from SQL text
- **Caching**: Query plans cached for performance

**Advanced Security Patterns:**

**1. Dynamic Queries with Whitelisting:**
```java
// SAFE: Whitelist table names, parameterize values
String[] allowedTables = {"ACT", "VENUE", "GIG"};
String tableName = validateTableName(userInput, allowedTables);

String sql = "SELECT * FROM " + tableName + " WHERE id = ?";
PreparedStatement stmt = conn.prepareStatement(sql);
stmt.setInt(1, userId);
```

**2. IN Clauses with Arrays:**
```java
// SAFE: Use arrays for IN clauses
String sql = "SELECT * FROM ACT WHERE actid = ANY(?)";
PreparedStatement stmt = conn.prepareStatement(sql);
Array array = conn.createArrayOf("INTEGER", new Integer[]{1, 2, 3});
stmt.setArray(1, array);
```

**3. LIKE Queries:**
```java
// SAFE: Parameterize LIKE patterns
String sql = "SELECT * FROM ACT WHERE actname LIKE ?";
PreparedStatement stmt = conn.prepareStatement(sql);
stmt.setString(1, "%" + searchTerm + "%");
```

**4. Dynamic WHERE Clauses:**
```java
// SAFE: Build conditions safely
StringBuilder sql = new StringBuilder("SELECT * FROM ACT WHERE 1=1");
List<Object> params = new ArrayList<>();

if (genre != null) {
    sql.append(" AND genre = ?");
    params.add(genre);
}
if (minFee != null) {
    sql.append(" AND standardfee >= ?");
    params.add(minFee);
}

PreparedStatement stmt = conn.prepareStatement(sql.toString());
for (int i = 0; i < params.size(); i++) {
    stmt.setObject(i + 1, params.get(i));
}
```

**Common Vulnerabilities to Avoid:**
1. ❌ String concatenation with user input
2. ❌ `Statement.execute("SELECT * FROM " + tableName)`
3. ❌ Dynamic SQL without proper validation
4. ❌ Trusting client-side validation alone

**Performance Benefits:**
- **Plan Caching**: Database reuses execution plans
- **Reduced Parsing**: SQL parsed once, executed multiple times
- **Type Safety**: Automatic type conversion and validation

**Best Practices:**
1. **Always use PreparedStatement** for any query with user input
2. **Never concatenate user data** into SQL strings
3. **Validate and sanitize** all inputs, even with prepared statements
4. **Use whitelisting** for dynamic table/column names
5. **Log parameterized queries** for debugging (without sensitive data)

Your understanding of SQL injection prevention is correct. Prepared statements are the fundamental defense against this critical security vulnerability.

### Prompt 15
**Date and Time**: 2025-12-06 12:45:00
**Task**: Exception handling strategy
**Prompt**:
```
In our JDBC code, we catch SQLException and often just print stack traces. What's a better approach for production-like error handling?

Should we:
1. Wrap in custom exceptions
2. Log errors properly
3. Return error codes vs. throwing exceptions
4. Provide user-friendly error messages
```
**Response**: Your current exception handling needs enhancement for production readiness. Here's a comprehensive strategy:

**Current Issues:**
```java
} catch (SQLException e) {
    // Just print stack trace - not production ready
    e.printStackTrace();
    return null;
}
```

**Enhanced Exception Handling:**

**1. Custom Exception Hierarchy:**
```java
public class GigSystemException extends Exception {
    private final ErrorCode errorCode;

    public GigSystemException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GigSystemException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}

public enum ErrorCode {
    VALIDATION_ERROR,
    DATABASE_ERROR,
    CONNECTION_ERROR,
    BUSINESS_RULE_VIOLATION
}
```

**2. Structured Logging:**
```java
private static final Logger logger = LoggerFactory.getLogger(GigSystem.class);

try {
    // Database operations
} catch (SQLException e) {
    logger.error("Database error in task2 for gig {}: {}", gigId, e.getMessage(), e);
    throw new GigSystemException(ErrorCode.DATABASE_ERROR,
        "Failed to create gig due to database error", e);
} catch (ValidationException e) {
    logger.warn("Validation failed for gig {}: {}", gigId, e.getMessage());
    throw new GigSystemException(ErrorCode.VALIDATION_ERROR,
        "Invalid gig data: " + e.getMessage(), e);
}
```

**3. User-Friendly Error Messages:**
```java
public String[][] task2(Connection conn, /* params */) {
    try {
        // Implementation
        return new String[0][0]; // Success
    } catch (GigSystemException e) {
        switch (e.getErrorCode()) {
            case VALIDATION_ERROR:
                // Log technical details, return user-friendly message
                logger.warn("Validation error: {}", e.getMessage());
                return createErrorResponse("Invalid input data. Please check your parameters.");
            case DATABASE_ERROR:
                // Log full details, return generic message
                logger.error("Database error", e);
                return createErrorResponse("Database temporarily unavailable. Please try again.");
            default:
                logger.error("Unexpected error", e);
                return createErrorResponse("An unexpected error occurred.");
        }
    }
}

private String[][] createErrorResponse(String message) {
    return new String[][]{{"ERROR", message}};
}
```

**4. Error Recovery Strategies:**
```java
public String[][] task2WithRetry(Connection conn, /* params */, int maxRetries) {
    int attempts = 0;
    while (attempts < maxRetries) {
        try {
            return task2(conn, /* params */);
        } catch (GigSystemException e) {
            if (e.getErrorCode() == ErrorCode.CONNECTION_ERROR && attempts < maxRetries - 1) {
                attempts++;
                logger.warn("Connection failed, retrying (attempt {} of {})", attempts, maxRetries);
                try {
                    Thread.sleep(1000 * attempts); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new GigSystemException(ErrorCode.DATABASE_ERROR, "Interrupted during retry", ie);
                }
            } else {
                throw e;
            }
        }
    }
    throw new GigSystemException(ErrorCode.DATABASE_ERROR, "Failed after " + maxRetries + " attempts");
}
```

**5. Exception Translation:**
```java
// Convert JDBC exceptions to business exceptions
try {
    stmt.executeUpdate();
} catch (SQLException e) {
    if (e.getSQLState().startsWith("23")) { // Integrity constraint violation
        throw new GigSystemException(ErrorCode.VALIDATION_ERROR,
            "Data violates business rules", e);
    } else if (e.getSQLState().startsWith("08")) { // Connection exception
        throw new GigSystemException(ErrorCode.CONNECTION_ERROR,
            "Database connection failed", e);
    } else {
        throw new GigSystemException(ErrorCode.DATABASE_ERROR,
            "Database operation failed", e);
    }
}
```

**Best Practices:**
1. **Log technical details** but return user-friendly messages
2. **Use error codes** for programmatic error handling
3. **Implement retry logic** for transient failures
4. **Preserve stack traces** for debugging while hiding from users
5. **Fail fast** for validation errors, retry for connection issues
6. **Use structured logging** with appropriate log levels

**Production-Ready Pattern:**
```java
@Service
public class GigService {

    @Transactional(rollbackFor = Exception.class)
    public GigCreationResult createGig(CreateGigRequest request) {
        try {
            // Business logic with proper validation
            validateRequest(request);
            Gig gig = gigRepository.save(createGigEntity(request));
            return GigCreationResult.success(gig);
        } catch (DataIntegrityViolationException e) {
            logger.warn("Data integrity violation: {}", e.getMessage());
            throw new BusinessException("Gig data violates business rules");
        } catch (Exception e) {
            logger.error("Unexpected error creating gig", e);
            throw new SystemException("Failed to create gig due to system error");
        }
    }
}
```

Your current approach with `e.printStackTrace()` is common in development but insufficient for production. Implement structured error handling with appropriate logging levels and user-friendly messages.

### Prompt 16
**Date and Time**: 2025-12-06 16:00:00
**Task**: Database schema versioning
**Prompt**:
```
For schema changes during development, we use DROP and CREATE statements. What's the proper way to handle schema migrations in a real project?

Should we use:
- Flyway or Liquibase for migrations
- Versioned SQL files
- Programmatic schema management
```
**Response**: Schema versioning is crucial for database evolution. Let's compare approaches:

**Development Approach (Your Current):**
```sql
-- Drop everything and recreate
DROP TABLE IF EXISTS TICKET CASCADE;
DROP TABLE IF EXISTS GIG_TICKET CASCADE;
-- ... drop all tables ...
-- Then recreate all tables and constraints
```

**Production Migration Tools:**

**1. Flyway (Recommended):**
```bash
# Migration files in db/migration/
# V1__Initial_schema.sql
# V2__Add_audit_columns.sql
# V3__Create_indexes.sql

# Automatic migration on startup
flyway.migrate()
```

**Flyway Migration File Example:**
```sql
-- V1__Initial_schema.sql
CREATE TABLE ACT (
    actid INTEGER PRIMARY KEY,
    actname VARCHAR(100) NOT NULL,
    genre VARCHAR(100),
    standardfee INTEGER NOT NULL CHECK (standardfee >= 0)
);

-- V2__Add_gig_status.sql
ALTER TABLE GIG ADD COLUMN gigstatus CHAR(1) NOT NULL DEFAULT 'G'
    CHECK (gigstatus IN ('G', 'C'));

-- V3__Create_indexes.sql
CREATE INDEX idx_act_gig_gigid_ontime ON ACT_GIG(gigid, ontime);
CREATE INDEX idx_gig_venueid ON GIG(venueid);
```

**2. Liquibase:**
```xml
<!-- changelog.xml -->
<databaseChangeLog>
    <changeSet id="1" author="developer">
        <createTable tableName="ACT">
            <column name="actid" type="int" autoIncrement="true">
                <constraints primaryKey="true"/>
            </column>
            <column name="actname" type="varchar(100)" >
                <constraints nullable="false"/>
            </column>
        </createTable>
    </changeSet>
</databaseChangeLog>
```

**3. Rails-style Migrations (Programmatic):**
```java
public class AddGigStatusMigration implements Migration {
    @Override
    public void up() {
        execute("ALTER TABLE GIG ADD COLUMN gigstatus CHAR(1) NOT NULL DEFAULT 'G'");
        execute("ALTER TABLE GIG ADD CONSTRAINT chk_gigstatus CHECK (gigstatus IN ('G', 'C'))");
    }

    @Override
    public void down() {
        execute("ALTER TABLE GIG DROP CONSTRAINT chk_gigstatus");
        execute("ALTER TABLE GIG DROP COLUMN gigstatus");
    }
}
```

**Migration Best Practices:**

**1. Version Control:**
- ✅ Store migrations in Git alongside code
- ✅ Use descriptive migration names
- ✅ Include both up and down migrations
- ✅ Test migrations on multiple environments

**2. Safe Migrations:**
```sql
-- GOOD: Non-blocking operations
ALTER TABLE ACT ADD COLUMN created_at TIMESTAMP DEFAULT NOW();

-- AVOID: Blocking operations during migration
ALTER TABLE ACT ADD COLUMN new_column INTEGER NOT NULL; -- Blocks if table not empty

-- BETTER: Safe with default
ALTER TABLE ACT ADD COLUMN new_column INTEGER DEFAULT 0 NOT NULL;
```

**3. Rollback Strategy:**
```sql
-- Always provide rollback scripts
-- V1__Add_user_table.sql (up)
CREATE TABLE users (...);

-- V1__Add_user_table.sql (down)
DROP TABLE users;
```

**4. Environment Handling:**
```bash
# Different configurations for dev/staging/prod
flyway.url=jdbc:postgresql://localhost:5432/myapp_dev
flyway.user=dev_user
flyway.password=dev_password

# Production
flyway.url=jdbc:postgresql://prod-host:5432/myapp_prod
flyway.user=prod_user
flyway.password=${PROD_PASSWORD}
```

**5. Migration Testing:**
```java
@Test
public void testMigration() {
    // Run migrations
    flyway.migrate();

    // Verify schema
    assertTableExists("ACT");
    assertColumnExists("GIG", "gigstatus");

    // Test rollback
    flyway.undo(); // If supported
}
```

**Your Coursework Approach:**
For coursework, your DROP/CREATE approach is acceptable because:
- ✅ Simple and predictable for testing
- ✅ Ensures clean state between runs
- ✅ Easy to understand and modify

**Production Recommendation:**
```java
// build.gradle
dependencies {
    implementation 'org.flywaydb:flyway-core:9.16.0'
}

// Application startup
@Configuration
public class DatabaseConfig {
    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
        flyway.migrate();
        return flyway;
    }
}
```

**Migration Workflow:**
1. **Development**: Create migration files for schema changes
2. **Testing**: Run migrations against test databases
3. **Deployment**: Automatic migration on application startup
4. **Rollback**: Versioned rollback capability for emergencies

Your current approach demonstrates understanding of schema management. For production systems, migration tools provide essential versioning, rollback, and environment consistency features.

### Prompt 17
**Date and Time**: 2025-12-07 11:15:00
**Task**: Query performance analysis
**Prompt**:
```
How can I analyze query performance in PostgreSQL? What tools or commands should I use to identify slow queries and optimization opportunities?

For our CTE-heavy queries, how can I see the execution plan?
```
**Response**: PostgreSQL provides excellent tools for query performance analysis. Here's a comprehensive approach:

**1. EXPLAIN ANALYZE (Primary Tool):**
```sql
-- Basic execution plan
EXPLAIN ANALYZE
SELECT a.actname, TO_CHAR(ag.ontime, 'HH24:MI'), TO_CHAR(ag.ontime + (ag.duration || ' minutes')::INTERVAL, 'HH24:MI')
FROM ACT_GIG ag
JOIN ACT a ON ag.actid = a.actid
WHERE ag.gigid = 1
ORDER BY ag.ontime ASC;

-- Output shows:
-- - Execution time (actual vs estimated)
-- - Row counts (actual vs estimated)
-- - Node types (Index Scan, Seq Scan, Hash Join, etc.)
-- - Cost estimates (startup cost, total cost)
```

**2. Advanced EXPLAIN Options:**
```sql
-- Detailed analysis with buffers and timing
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, FORMAT JSON)
SELECT * FROM ACT_GIG WHERE gigid = 1;

-- Key metrics to check:
-- - actual time: How long each node took
-- - buffers: Memory/disk I/O usage
-- - rows: Actual vs estimated row counts
-- - loops: How many times each node executed
```

**3. pg_stat_statements (Query Statistics):**
```sql
-- Enable extension (requires superuser)
CREATE EXTENSION pg_stat_statements;

-- View slowest queries
SELECT query, calls, total_time, mean_time, rows
FROM pg_stat_statements
ORDER BY total_time DESC
LIMIT 10;

-- Reset statistics
SELECT pg_stat_statements_reset();
```

**4. Index Usage Analysis:**
```sql
-- Check which indexes are being used
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- Check unused indexes (low scan count)
SELECT schemaname, tablename, indexname
FROM pg_stat_user_indexes
WHERE idx_scan = 0;
```

**5. Table Statistics:**
```sql
-- Update table statistics
ANALYZE ACT_GIG;

-- View table statistics
SELECT schemaname, tablename, n_tup_ins, n_tup_upd, n_tup_del, n_live_tup, n_dead_tup
FROM pg_stat_user_tables
WHERE tablename = 'act_gig';
```

**6. Query Performance Patterns:**

**Good Performance Indicators:**
```
Index Scan on idx_act_gig_gigid_ontime (cost=0.42..8.44 rows=1 width=16)
  Index Cond: (gigid = 1)
  ->  Sort (cost=8.45..8.45 rows=1 width=16)
      Sort Key: ontime
      ->  Nested Loop (cost=0.42..8.44 rows=1 width=16)
```

**Poor Performance Indicators:**
```
Seq Scan on act_gig (cost=0.00..1234.00 rows=50000 width=16)
  Filter: (gigid = 1)
  Rows Removed by Filter: 49999
```

**7. Common Performance Issues:**

**Missing Indexes:**
```sql
-- Slow query without index
EXPLAIN ANALYZE SELECT * FROM ACT_GIG WHERE ontime > '2024-01-01 00:00:00';
-- Shows: Seq Scan (cost=1000+)

-- Add index
CREATE INDEX idx_act_gig_ontime ON ACT_GIG(ontime);

-- Query now uses: Index Scan (cost=10)
```

**Inefficient JOINs:**
```sql
-- Check JOIN performance
EXPLAIN ANALYZE
SELECT g.gigtitle, COUNT(ag.actid) as act_count
FROM GIG g
LEFT JOIN ACT_GIG ag ON g.gigid = ag.gigid
GROUP BY g.gigid, g.gigtitle;

-- Look for: Hash Join vs Nested Loop vs Merge Join
-- Hash Join is usually best for larger datasets
```

**8. Query Optimization Workflow:**
```sql
-- 1. Run EXPLAIN ANALYZE on slow queries
EXPLAIN (ANALYZE, BUFFERS) SELECT ...;

-- 2. Identify bottlenecks
-- Look for: Seq Scan, high cost, large row counts

-- 3. Check index usage
SELECT * FROM pg_stat_user_indexes WHERE tablename = 'your_table';

-- 4. Create targeted indexes
CREATE INDEX idx_specific_query ON your_table(column1, column2);

-- 5. Re-analyze and recheck
ANALYZE your_table;
EXPLAIN (ANALYZE) SELECT ...;
```

**9. Monitoring Setup:**
```sql
-- Enable query logging for slow queries
ALTER SYSTEM SET log_min_duration_statement = '1000'; -- Log queries > 1 second

-- View logs
tail -f /var/log/postgresql/postgresql-*.log

-- Real-time monitoring
SELECT pid, query, state, wait_event
FROM pg_stat_activity
WHERE state = 'active';
```

**10. Performance Maintenance:**
```sql
-- Regular maintenance
VACUUM ANALYZE; -- Update statistics and reclaim space

-- Rebuild indexes if fragmented
REINDEX INDEX idx_act_gig_gigid_ontime;

-- Monitor index bloat
SELECT schemaname, tablename, n_dead_tup, n_live_tup,
       ROUND(n_dead_tup::numeric / (n_live_tup + n_dead_tup) * 100, 2) as bloat_ratio
FROM pg_stat_user_tables
WHERE n_live_tup + n_dead_tup > 0
ORDER BY bloat_ratio DESC;
```

**Your Specific Case:**
For your Task 5 CTE query, monitor the execution plan to ensure:
- CTEs are optimized properly (not materialized unnecessarily)
- JOINs use appropriate strategies (Hash Join for larger sets)
- Indexes are utilized for all filtering operations

PostgreSQL's query planner is sophisticated, but understanding execution plans is essential for performance tuning. Start with `EXPLAIN ANALYZE` for any query taking more than a few milliseconds.

### Prompt 18
**Date and Time**: 2025-12-07 14:30:00
**Task**: Data validation strategy
**Prompt**:
```
For input validation in our Java methods, should we validate at multiple levels?

Current approach:
- Java method validates basic constraints
- Database triggers validate business rules
- CHECK constraints enforce data types and ranges

Is this overkill or good defense in depth?
```
**Response**: Defense in depth is excellent for data validation. Let's analyze your layered approach:

**Your Current Strategy:**
```
User Input → Java Validation → Database Constraints → Trigger Enforcement
```

**Detailed Layer Analysis:**

**1. Java/Application Layer:**
```java
public void validateInput(String name, String email, String ticketType) {
    // Immediate feedback, good UX
    if (name == null || name.trim().isEmpty()) {
        throw new ValidationException("Customer name is required");
    }
    if (!email.contains("@")) {
        throw new ValidationException("Valid email is required");
    }
    if (ticketType.length() != 1) {
        throw new ValidationException("Ticket type must be a single character");
    }
}
```
**Pros:** Fast feedback, business logic, internationalization
**Cons:** Can be bypassed by direct database access

**2. Database Constraints:**
```sql
-- CHECK constraints (automatic, always enforced)
ALTER TABLE TICKET ADD CONSTRAINT chk_cost_positive
    CHECK (cost >= 0);

ALTER TABLE ACT_GIG ADD CONSTRAINT chk_duration_range
    CHECK (duration >= 15 AND duration <= 90);

-- UNIQUE constraints
ALTER TABLE ACT_GIG ADD CONSTRAINT unique_act_gig_performance
    UNIQUE (actid, gigid, ontime);
```
**Pros:** Always enforced, fast, declarative
**Cons:** Limited to simple rules, poor error messages

**3. Trigger-Based Validation:**
```sql
CREATE TRIGGER validate_venue_capacity
    BEFORE INSERT ON TICKET
    FOR EACH ROW EXECUTE FUNCTION validate_venue_capacity();
```
**Pros:** Complex business logic, cross-table validation
**Cons:** Performance overhead, complex debugging

**4. Application-Level Business Rules:**
```java
// Complex validation requiring external data
public void validateGigSchedule(LocalDateTime gigStart, List<ActPerformanceDetails> acts) {
    // Check travel time between gigs
    for (Act act : acts) {
        List<ConflictingGig> conflicts = findConflictingGigs(act.getId(), gigStart);
        for (ConflictingGig conflict : conflicts) {
            long travelTimeRequired = calculateTravelTime(conflict.getVenue(), gigStart);
            if (travelTimeRequired < 60) { // Business rule
                throw new BusinessRuleViolationException(
                    "Act needs 60 minutes travel time, only has " + travelTimeRequired);
            }
        }
    }
}
```

**Comprehensive Validation Strategy:**

**Input Sanitization:**
```java
public String sanitizeInput(String input) {
    if (input == null) return null;
    return input.trim().replaceAll("[<>\"'&]", ""); // Basic XSS prevention
}
```

**Type Validation:**
```java
public void validateTypes(Object... params) {
    for (Object param : params) {
        if (param instanceof String) {
            String str = (String) param;
            if (str.length() > 255) { // VARCHAR limit
                throw new ValidationException("Input too long");
            }
        }
    }
}
```

**Business Rule Validation:**
```java
public void validateBusinessRules(Connection conn, GigCreationRequest request) {
    // Venue exists and is available
    validateVenueAvailability(conn, request.getVenueId(), request.getGigStart());

    // Acts exist and are available
    for (ActRequest act : request.getActs()) {
        validateActAvailability(conn, act.getActId(), request.getGigStart());
    }

    // Schedule constraints
    validateScheduleConstraints(request.getActs(), request.getGigStart());
}
```

**Cross-Layer Error Handling:**
```java
try {
    // Java validation
    validateInput(request);

    // Database transaction with constraint checking
    executeInTransaction(conn -> {
        validateBusinessRules(conn, request);
        insertGig(conn, request);
        return true;
    });

} catch (ValidationException e) {
    // User input error - show friendly message
    return createUserErrorResponse(e.getMessage());

} catch (DataIntegrityViolationException e) {
    // Database constraint violation
    logger.warn("Constraint violation: {}", e.getMessage());
    return createSystemErrorResponse("Data validation failed");

} catch (SQLException e) {
    // Trigger validation failure
    if (e.getMessage().contains("Final act must finish")) {
        return createUserErrorResponse("Gig duration is too short");
    }
    logger.error("Database error", e);
    return createSystemErrorResponse("System error occurred");
}
```

**Testing Strategy:**
```java
@Test
public void testValidationLayers() {
    // Test Java validation
    assertThrows(ValidationException.class, () -> validateInput("", "invalid"));

    // Test database constraints
    assertThrows(SQLException.class, () -> insertNegativeCost(conn, -100));

    // Test trigger validation
    assertThrows(SQLException.class, () -> createOverlappingGigs(conn));
}
```

**Best Practices:**
1. **Validate early** for good UX, but enforce everywhere for security
2. **Use appropriate layer** for each validation type
3. **Provide clear error messages** at each level
4. **Log validation failures** for monitoring and debugging
5. **Test all layers** independently and together
6. **Consider performance impact** of complex validations

Your approach demonstrates excellent understanding of defense in depth. Each layer serves a specific purpose and compensates for the limitations of other layers.

### Prompt 19
**Date and Time**: 2025-12-08 10:00:00
**Task**: Documentation generation
**Prompt**:
```
I'm working on documenting my gig management database system for the university coursework. The system has 8 main tasks (1-8) with complex SQL queries, triggers, and Java JDBC integration. I need help structuring the README.md file properly.

Specifically, I need guidance on:

1. What should be the overall structure of a README.md for a database project like this?
2. What sections should I include and in what order?
3. How should I document the database schema design decisions?
4. For documenting SQL functions and triggers, what's the best format to explain complex business logic? For example, how do I explain a trigger that validates overlapping performances or a CTE that calculates break-even ticket sales?
5. Should I include code examples, input/output formats, and rationale sections?
6. How do I balance technical detail with readability for someone who needs to understand the system?

The README should serve as both documentation for markers and a reference for future developers. I want it to be comprehensive but not overwhelming.

Also, I need to follow the coursework requirements for documentation, so it should cover:
- Design choices and rationale
- Implementation details for each task
- Business rule enforcement
- Database schema explanation
- Setup and testing instructions

Can you provide a detailed structure and examples of how to document complex database logic?
```
**Response**: For a database project README.md, structure it hierarchically: Overview → Design → Implementation → Usage → Testing. Include sections for schema design, business rules, task implementations with code examples and rationale, setup instructions, and appendices. For complex business logic, use step-by-step explanations with pseudocode, input/output examples, and design rationale. Balance technical depth with clear explanations, using bullet points, code blocks, and diagrams where helpful.

### Prompt 20
**Date and Time**: 2025-12-08 15:45:00
**Task**: Code review and optimization
**Prompt**:
```
Before implementing AI-generated code, what should I check for?

Areas to review:
- SQL injection vulnerabilities
- Performance implications
- Error handling completeness
- Business rule correctness
- Code maintainability

What specific patterns should I look for in JDBC code?
```
**Response**: Excellent question! AI-generated code requires thorough review before implementation. Here's a comprehensive checklist:

**Security Review:**

**1. SQL Injection Prevention:**
```java
// ✅ GOOD: Parameterized queries
PreparedStatement stmt = conn.prepareStatement(
    "SELECT * FROM ACT WHERE actname = ? AND genre = ?");
stmt.setString(1, name);
stmt.setString(2, genre);

// ❌ BAD: String concatenation
String sql = "SELECT * FROM ACT WHERE actname = '" + name + "'";
```
**Check:** All database queries use prepared statements with proper parameterization.

**2. Input Validation:**
```java
// ✅ GOOD: Comprehensive validation
public void validateInput(String name, String email) {
    if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Name required");
    }
    if (email == null || !email.contains("@")) {
        throw new IllegalArgumentException("Valid email required");
    }
}

// ❌ BAD: Missing validation
public void process(String input) {
    // No validation - vulnerable to various attacks
}
```
**Check:** All user inputs validated for null, empty, format, and length.

**3. Authentication & Authorization:**
**Check:** No hardcoded credentials, proper access controls, secure connection strings.

**Code Quality Review:**

**1. Resource Management:**
```java
// ✅ GOOD: try-with-resources
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql);
     ResultSet rs = stmt.executeQuery()) {
    // Use resources
} // Auto-closed

// ❌ BAD: Manual cleanup
Connection conn = null;
try {
    conn = dataSource.getConnection();
    // Use connection
} finally {
    if (conn != null) conn.close(); // Easy to forget
}
```
**Check:** All database connections, statements, and result sets properly closed.

**2. Exception Handling:**
```java
// ✅ GOOD: Specific exception handling
try {
    performDatabaseOperation();
} catch (SQLException e) {
    if (e.getSQLState().startsWith("23")) {
        throw new DataIntegrityException("Data constraint violated", e);
    }
    throw new DatabaseException("Database operation failed", e);
}

// ❌ BAD: Generic handling
try {
    performOperation();
} catch (Exception e) {
    e.printStackTrace(); // Not production-ready
}
```
**Check:** Meaningful exceptions, proper logging, no swallowed exceptions.

**3. Transaction Management:**
```java
// ✅ GOOD: Proper transaction scope
try {
    conn.setAutoCommit(false);
    // Multiple related operations
    updateAccount();
    insertTransaction();
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
    throw e;
} finally {
    conn.setAutoCommit(true);
}
```
**Check:** Transactions properly scoped, rollback on errors, auto-commit restored.

**4. SQL Best Practices:**
```sql
-- ✅ GOOD: Clear, efficient queries
SELECT a.actname, COUNT(t.ticketid) as tickets_sold
FROM ACT a
LEFT JOIN ACT_GIG ag ON a.actid = ag.actid
LEFT JOIN TICKET t ON ag.gigid = t.gigid
WHERE ag.gigid = ?
GROUP BY a.actid, a.actname
ORDER BY tickets_sold DESC;

-- ❌ BAD: Inefficient or unclear
SELECT * FROM ACT_GIG WHERE gigid = 1; -- Missing JOINs, select *
```
**Check:** Efficient queries, proper JOINs, appropriate indexing considerations.

**Business Logic Review:**

**1. Requirements Compliance:**
- ✅ Verify all business rules implemented correctly
- ✅ Check edge cases handled (empty results, null values, boundary conditions)
- ✅ Validate against original specification

**2. Performance Considerations:**
- ✅ Query optimization opportunities identified
- ✅ Appropriate indexing suggested
- ✅ Scalability considerations addressed

**3. Maintainability:**
- ✅ Code is readable and well-documented
- ✅ Consistent naming conventions
- ✅ Separation of concerns maintained

**Testing Review:**

**1. Unit Test Coverage:**
```java
@Test
public void testTask1ValidInput() {
    // Arrange
    Connection conn = createTestConnection();

    // Act
    String[][] result = task1(conn, 1);

    // Assert
    assertNotNull(result);
    assertTrue(result.length > 0);
    assertEquals(3, result[0].length); // actname, start, end
}
```

**2. Error Handling Tests:**
```java
@Test(expected = SQLException.class)
public void testTask1InvalidGigId() {
    task1(conn, -1); // Should throw exception
}
```

**Integration Review:**

**1. End-to-End Testing:**
- ✅ Verify complete workflows function correctly
- ✅ Check data consistency across operations
- ✅ Validate cascade effects of operations

**2. Cross-Module Dependencies:**
- ✅ Ensure changes don't break existing functionality
- ✅ Check for circular dependencies
- ✅ Verify proper module isolation

**Implementation Checklist:**

**Before Implementation:**
- [ ] Security review completed
- [ ] Code quality verified
- [ ] Business logic validated
- [ ] Tests written and passing
- [ ] Documentation updated
- [ ] Performance impact assessed

**After Implementation:**
- [ ] Integration tests pass
- [ ] No regressions introduced
- [ ] Performance benchmarks met
- [ ] Code reviewed by peer/team member
- [ ] Documentation reflects changes

**AI-Specific Concerns:**
- [ ] Generated code doesn't introduce subtle bugs
- [ ] AI suggestions align with project architecture
- [ ] Code follows team coding standards
- [ ] Complex algorithms are correctly implemented
- [ ] Edge cases properly handled

**Final Recommendation:**
Always implement AI-generated code with caution. Use it as a starting point for implementation, but apply rigorous review and testing before production deployment. The goal is to leverage AI productivity while maintaining code quality and reliability.