<!-- This is a template for the README.md that you should submit. For instructions on how to get started, see INSTRUCTIONS.md -->
# Design Choices

## Database Schema Design

### Table Structure
The schema follows a normalized design with five core tables:
- **ACT**: Stores musical acts with their genre and standard fee
- **VENUE**: Stores venue information including capacity and hire cost
- **GIG**: Stores gig information linked to venues, with status tracking ('G' for going ahead, 'C' for cancelled)
- **ACT_GIG**: Junction table linking acts to gigs, storing performance details (ontime, duration, fee)
- **GIG_TICKET**: Stores ticket pricing information per gig and price type
- **TICKET**: Stores individual ticket purchases

### Primary Keys and Sequences
- All tables use INTEGER primary keys with sequences starting at 10001
- ACT_GIG uses a composite primary key (actid, gigid, ontime) to allow the same act to perform multiple times at the same gig
- Sequences ensure test data (IDs 1-10000) doesn't conflict with auto-generated IDs

### Foreign Keys and Cascade Deletes
All foreign keys use `ON DELETE CASCADE` to maintain referential integrity:
- Deleting a gig automatically removes all related ACT_GIG, GIG_TICKET, and TICKET records
- Deleting an act removes all related ACT_GIG records
- Deleting a venue removes all related GIG records

## Business Rules Enforcement

The schema implements 17 business rules through a combination of CHECK constraints and database triggers. The rationale for using triggers over application-level validation is:

1. **Data Integrity**: Business rules are enforced at the database level, preventing invalid data regardless of how it's inserted
2. **Consistency**: All applications accessing the database must follow the same rules
3. **Performance**: Database-level validation is efficient and doesn't require multiple round trips
4. **Maintainability**: Rules are centralized in the schema, making them easier to update

### Key Constraints and Triggers

#### Table-Level CHECK Constraints
- **GIG.gigdatetime**: Ensures gigs start between 9am and 11:59pm (Business Rule 15)
- **ACT_GIG.duration**: Ensures performance duration is 15-90 minutes (Business Rule 5)
- **All monetary values**: Non-negative checks for fees, costs, and prices
- **VENUE.capacity**: Must be positive

#### Trigger-Based Validations

**1. First Act Start Time (Business Rule 11)**
- `validate_first_act_start()`: Ensures the first act starts exactly at the gig's datetime
- Rationale: The gig datetime represents when the event begins, so the first performance must align with this

**2. No Overlapping Performances (Business Rule 1)**
- `prevent_overlapping_performances()`: Prevents acts from overlapping at the same gig
- Allows acts to start exactly when the previous act finishes (no gap required)
- Rationale: Ensures smooth scheduling without conflicts while allowing back-to-back performances

**3. Interval Duration (Business Rule 10)**
- `validate_interval_duration()`: Ensures gaps between acts are 10-30 minutes
- Only validates when there's an actual gap (not back-to-back performances)
- Rationale: Maintains appropriate break times for audience and performers

**4. Act Simultaneous Performance Prevention (Business Rule 2)**
- `prevent_act_simultaneous_gigs()`: Prevents acts from performing in multiple gigs at the same time
- Only checks non-cancelled gigs (Business Rule 16)
- Rationale: Acts can only be in one place at a time

**5. Same Act Break Requirement (Business Rule 6)**
- `validate_same_act_break()`: Ensures the same act doesn't perform twice consecutively without a break
- Rationale: Allows acts to perform multiple times at a gig (e.g., first half, interval, second half) but requires breaks between performances

**6. Act Travel Time (Business Rule 7)**
- `validate_act_travel_gap()`: Ensures 60-minute gap between gigs for the same act on the same day
- Only checks non-cancelled gigs
- Rationale: Provides sufficient time for acts to travel between venues

**7. Venue Gap Requirement (Business Rule 9)**
- `validate_venue_gap_on_act_change()`: Ensures 180-minute gap between gigs at the same venue
- Validates when acts are added/updated to recalculate gig end times
- Rationale: Provides time for venue staff to clean and prepare between events

**8. Final Act Duration (Business Rule 13)**
- `validate_final_act_duration()`: Ensures the final act finishes at least 60 minutes after gig start
- Rationale: Ensures gigs have minimum duration for value and logistics

**9. Gig Finish Time by Genre (Business Rule 14)**
- `validate_gig_finish_time()`: Rock/pop gigs must finish by 11pm, others by 1am
- Checks if any act in the gig has 'rock' or 'pop' genre (case sensitive)
- Rationale: Noise regulations for residential areas

**10. Act Fee Per Gig (Business Rule 4)**
- `validate_act_fee_per_gig()`: Ensures all performances by the same act at the same gig have the same fee
- Rationale: Acts receive one fee per gig regardless of number of performances

**11. Ticket Cost Validation**
- `validate_ticket_cost()`: Ensures ticket cost matches the price in GIG_TICKET
- Rationale: Prevents pricing inconsistencies

**12. Venue Capacity Validation (Business Rule 12)**
- `validate_venue_capacity()`: Prevents ticket sales from exceeding venue capacity
- Validates on INSERT and UPDATE
- Rationale: Safety and legal compliance

### Indexes
Indexes are created on frequently queried columns:
- `ACT_GIG(gigid, ontime)`: Optimizes Task 1 queries (gig schedule retrieval)
- `GIG(venueid)`: Optimizes venue lookups
- `TICKET(gigid)`: Optimizes ticket queries per gig

# Task Implementations

## Task 1

Task 1 retrieves the lineup (schedule) for a given gigID, returning act names, start times, and end times in 24-hour format without seconds.

**Implementation:**
The solution uses a single SQL query that joins ACT_GIG with ACT to retrieve act names. The query:
1. Selects `actname` from the ACT table
2. Formats `ontime` using `TO_CHAR(ontime, 'HH24:MI')` to get 24-hour format without seconds
3. Calculates `offtime` by adding duration to ontime: `ontime + (duration || ' minutes')::INTERVAL` and formats it similarly
4. Filters by `gigid` using a parameterized query (prepared statement) to prevent SQL injection
5. Orders results by `ontime ASC` to ensure chronological order

**Design Rationale:**
- Uses prepared statements for security and performance (query plan caching)
- Performs time calculations in PostgreSQL rather than Java for accuracy
- Uses `TO_CHAR` for consistent formatting regardless of timezone settings
- Single query approach minimizes database round trips
- The `convertResultToStrings()` helper method handles ResultSet to String[][] conversion, making the code reusable

**Output Format:**
Returns a 2D String array where each row contains [ActName, OnTime, OffTime] in the format:
- ActName: Full act name (e.g., "ViewBee 40")
- OnTime: Start time in HH:MM format (e.g., "18:00")
- OffTime: End time in HH:MM format (e.g., "18:50")

Rows are ordered chronologically by start time, ensuring the schedule is presented in performance order.

## Task 2
Task 2 required us to create a new gig at a venue with multiple acts, ensuring all business rules are satisfied. 

**Input:**
- venue: venue name 
- gigTitle: Title of the gig 
- gigStart (LocalDateTime): When the gig starts
- adultTicketPrice (int): Price for adult tickets 
- actDetails(ActPerformanceDetails[]): Array of act performances -> might need to sort before adding it to our database


**Implementation**
- Disable auto-commit: We wanna make sure all the business rules and requirements are met before commiting the record 
    - Use-try-catch finally for rollback on errors
    - Commits only if all the validations pass
- Validate venue exists: 
    - Query `VENUE` by name to get `venueid`    
    - If not found, rollback and return 
- Validate gig start time: 
    - gigs must start between 9am and 11:59pm
    - Check gigStart is within this range 
- Sort acts chronologically
    - Sort actDetails by onTIme(earliest first)
    - Needed for subsequent validation
- Validate first act start time:
    - Ensure the first act starts exactly at gigStart
    - Compare the earliest act's onTime with gigStart

- Validate all act performances:
    For each act in actDetails:
    - Act exists in the ACT table (query by actid)
    - Duration is between 15 and 90 minutes (Business Rule 5); already enforced by CHECK constraint, but verify
    - Fee is non-negative; already enforced by CHECK, but verify

- Validate cross-gig constraints for each act:
    - Not performing in another non-cancelled gig at the same time (Business Rule 2)
    - 60-minute gap between gigs for the same act on the same day (Business Rule 7)

- Validate venue constraints:
    - 180-minute gap between gigs at the same venue (Business Rule 9)

- Validate gig finish time:
    - Rock/pop gigs must finish by 11pm; others by 1am (Business Rule 14)
    - Check genre of all acts for appropriate finish time

- Validate act fees:
    - All performances by the same act at the same gig must have the same fee (Business Rule 4)
    - Group actDetails by actID and verify all fees per act are identical

- Insert data (if all validations pass):
    - Insert into GIG table: get next gigid from sequence, insert venueid, gigtitle, gigdatetime, gigstatus = 'G'
    - Insert into ACT_GIG table: for each act, insert actid, gigid, actgigfee, ontime, duration
    - Insert into GIG_TICKET table: insert adult ticket price (gigid, pricetype='A', price=adultTicketPrice)

- Commit or rollback:
    - If all inserts succeed: commit
    - If any error: rollback

**Design Rationale**

The implementation uses a two-layer validation approach combining application-level checks with database-level triggers:

1. **Transaction Management**: All operations are wrapped in a transaction with manual commit control. This ensures atomicity - either all inserts succeed or none do, maintaining database consistency. The original auto-commit setting is preserved and restored in the finally block to avoid affecting other operations.

2. **Pre-validation in Java**: We validate certain rules in Java before attempting database inserts:
   - **Venue existence**: Early validation prevents unnecessary work if the venue doesn't exist
   - **Gig start time**: Simple time range check that's efficient in Java
   - **Act existence**: Validates all acts exist before processing
   - **First act timing**: Ensures the first act starts exactly at gigStart (Business Rule 11)
   - **Final act duration**: Validates minimum gig duration (Business Rule 13)
   - **Genre-based finish time**: Checks if rock/pop acts require earlier finish (Business Rule 14)
   - **Fee consistency**: Validates same act has same fee across all performances (Business Rule 4)

3. **Database Triggers for Complex Rules**: We rely on database triggers to validate complex cross-gig and scheduling rules:
   - **No overlapping performances** (Business Rule 1): Trigger checks all existing performances
   - **No simultaneous gigs** (Business Rule 2): Trigger checks all non-cancelled gigs
   - **Same act break requirement** (Business Rule 6): Trigger validates consecutive performances
   - **Travel time between gigs** (Business Rule 7): Trigger checks 60-minute gap requirement
   - **Venue gap requirement** (Business Rule 9): Trigger validates 180-minute gap between gigs
   - **Interval duration** (Business Rule 10): Trigger ensures 10-30 minute intervals

4. **Chronological Sorting**: Acts are sorted by `onTime` before validation to:
   - Identify the first and last acts for timing validations
   - Ensure proper order for interval calculations
   - Make the validation logic simpler and more efficient

5. **Helper Methods**: The implementation uses private helper methods for:
   - **Code reusability**: Common operations like venue lookup are encapsulated
   - **Separation of concerns**: Each method has a single responsibility
   - **Error handling**: SQLExceptions are properly propagated to the transaction handler
   - **Maintainability**: Changes to database queries are localized

6. **Error Handling Strategy**: All SQLExceptions are caught at the transaction level and trigger a rollback. This ensures that:
   - Database triggers that raise exceptions for business rule violations are properly handled
   - Any unexpected database errors don't leave partial data
   - The database state is always consistent

**Output Format**

Task 2 returns `void` - it does not return any data structure. The method either:
- **Succeeds silently**: All validations pass and the gig is created in the database (transaction committed)
- **Fails silently**: Any validation fails or business rule is violated, and the transaction is rolled back (no changes to database)

The method uses early returns with rollbacks for validation failures, and relies on exception handling for database-level constraint violations. This design ensures that callers cannot distinguish between different failure modes, maintaining the requirement that invalid gigs are simply not created.

## Task 3

Task 3 allows customers to purchase tickets for a gig, ensuring all business rules are satisfied.

**Input:**
- gigid: ID of the gig to purchase a ticket for
- name: Customer name
- email: Customer email address
- ticketType: Ticket type (single character, e.g., 'A' for Adult)

**Implementation**

- **Transaction Management**: All operations are wrapped in a transaction with manual commit control to ensure atomicity - either the ticket purchase succeeds or the database state remains unchanged.

- **Input Validation**:
  - Customer name must not be null or empty
  - Email must not be null or empty
  - Ticket type must be a single character

- **Gig Validation**:
  - Gig must exist in the database
  - Gig status must be 'G' (going ahead, not cancelled)

- **Ticket Type Validation**:
  - Ticket type must exist in GIG_TICKET table for the specified gig
  - Retrieves the correct price for the ticket type

- **Ticket Insertion**:
  - Inserts TICKET record with customer details and correct price
  - Database triggers automatically validate:
    - **Ticket cost matches GIG_TICKET price**: Ensures the cost field matches the price defined in GIG_TICKET
    - **Venue capacity not exceeded** (Business Rule 12): Prevents ticket sales from exceeding venue capacity

- **Error Handling**:
  - Any validation failure triggers rollback
  - SQLExceptions from triggers (capacity exceeded, price mismatch) are caught and trigger rollback
  - Original auto-commit setting is restored in finally block

**Design Rationale**

1. **Transaction Atomicity**: The entire ticket purchase is wrapped in a transaction to ensure that if any validation fails or a business rule is violated, no partial data is inserted into the database.

2. **Two-Layer Validation**:
   - **Application-level checks**: Validates gig existence, status, and ticket type availability before attempting database operations
   - **Database-level triggers**: Enforce business rules (capacity limits, price consistency) that require checking against current database state

3. **Price Retrieval**: The ticket price is retrieved from GIG_TICKET before insertion to ensure the correct price is used, and the trigger validates that the inserted cost matches this price.

4. **Capacity Validation**: The venue capacity check is handled by a database trigger because it requires counting all existing tickets for the gig, which is more efficiently done at the database level and ensures consistency even with concurrent ticket purchases.

5. **Error Handling Strategy**: All SQLExceptions (including those raised by triggers) are caught at the transaction level, ensuring proper rollback and maintaining database consistency.

**Output Format**

Task 3 returns `void` - it does not return any data structure. The method either:
- **Succeeds silently**: All validations pass and the ticket is purchased (transaction committed)
- **Fails silently**: Any validation fails or business rule is violated, and the transaction is rolled back (no changes to database)

The method uses early returns with rollbacks for validation failures, and relies on exception handling for database-level constraint violations (capacity exceeded, price mismatch).

## Task 4

Task 4 handles act cancellations for a gig, with two possible outcomes: cancelling only the act (and adjusting the schedule) or cancelling the entire gig.

**Input:**
- gigID: ID of the gig where the act needs to be cancelled
- actName: Name of the act to cancel

**Implementation**

- **Transaction Management**: All operations are wrapped in a transaction with manual commit control to ensure atomicity - either the cancellation succeeds or the database state remains unchanged.

- **Input Validation**:
  - Act name must not be null or empty
  - Gig must exist in the database
  - Gig status must be 'G' (going ahead, not cancelled) - cannot cancel acts from already cancelled gigs
  - Act must exist in the ACT table
  - Act must have at least one performance in the specified gig

- **Cancellation Decision Logic**:
  The method determines whether to cancel only the act or the entire gig based on two conditions:
  
  1. **Headline Act Check**: 
     - An act is considered a headline act if it is the only act in the gig, or if it is the final act (finishes last)
     - If the act is a headline act, the entire gig must be cancelled (Situation B)
  
  2. **Interval Rule Violation Check**:
     - Calculates the total duration of all cancelled performances
     - Determines the gap that would be created after cancellation by:
       - Finding the performance that ends just before the cancelled ones
       - Finding the first performance that starts after the cancelled ones
       - Calculating the adjusted start time of the next performance (moved earlier by cancelled duration)
       - Checking if the resulting gap violates Business Rule 10 (10-30 minutes)
     - If cancellation would create an invalid interval, the entire gig must be cancelled (Situation B)

- **Situation A: Cancel Act Only** (when act is not headline and no interval violation):
  - Delete all ACT_GIG records for the specified act and gig
  - Adjust subsequent performances: move all performances that start after the cancelled ones earlier by the total cancelled duration
  - Return the updated lineup using Task 1 logic (act name, ontime, offtime format)

- **Situation B: Cancel Entire Gig** (when act is headline or interval violation would occur):
  - Update GIG status to 'C' (cancelled)
  - **Preserve ACT_GIG records**: Do not delete any ACT_GIG records (as per requirement)
  - Update all TICKET costs to 0 (refund customers, but preserve original price structure)
  - Return distinct customer names and emails (ordered alphabetically by name) who have tickets for this gig

- **Error Handling**:
  - Any validation failure returns null and triggers rollback
  - SQLExceptions are caught and trigger rollback
  - Original auto-commit setting is restored in finally block

**Design Rationale**

1. **Transaction Atomicity**: The entire cancellation operation is wrapped in a transaction to ensure that if any step fails, no partial changes are made to the database.

2. **Two-Path Decision Logic**: The implementation uses a clear decision tree:
   - First checks if the act is a headline act (simpler check)
   - Only checks interval violations if the act is not a headline (optimization)
   - This ensures that headline acts always trigger full gig cancellation, which is required by business rules

3. **Headline Act Identification**: 
   - An act is headline if it's the only act in the gig (trivial case)
   - Otherwise, it's headline if it finishes last (has the latest end time)
   - This logic correctly handles both single-act gigs and multi-act gigs

4. **Interval Violation Detection**:
   - The method calculates the gap that would exist after cancellation by:
     - Finding the performance immediately before the cancelled block (if any)
     - Finding the first performance after the cancelled block
     - Adjusting the next performance's start time (subtracting cancelled duration)
     - Calculating the gap between previous end and adjusted next start
   - Special handling for when cancelled act is the first act: checks gap from gig start time (Business Rule 11 requires first act to start at gig start)
   - Special handling for when cancelled act is the last act: checks if remaining gig would meet minimum duration (Business Rule 13)

5. **Schedule Adjustment Strategy**:
   - When cancelling only an act, subsequent performances are moved earlier by the total cancelled duration
   - This maintains the chronological order and removes gaps
   - The adjustment is done via SQL UPDATE using interval arithmetic: `ontime - (duration || ' minutes')::INTERVAL`
   - Database triggers automatically validate that the adjusted schedule still meets all business rules

6. **Full Gig Cancellation Strategy**:
   - When cancelling entire gig, ACT_GIG records are preserved (as per requirement)
   - This maintains historical record of what was planned
   - All ticket costs are set to 0 to refund customers
   - Customer list is retrieved using DISTINCT to avoid duplicates (same customer may have multiple tickets)

7. **Helper Methods**: The implementation uses several helper methods for:
   - **Code reusability**: Common operations like act lookup, performance retrieval are encapsulated
   - **Separation of concerns**: Each method has a single responsibility
   - **Maintainability**: Changes to logic are localized
   - **Testability**: Individual components can be tested independently

8. **PerformanceInfo Inner Class**: Used to store performance details (actId, onTime, endTime) for easier manipulation and comparison in Java, avoiding repeated database queries.

**Output Format**

Task 4 returns a 2D String array, but the format depends on which situation occurred:

**Situation A (Act Cancelled, Gig Continues)**:
Returns the updated lineup in the same format as Task 1:
- Each row: [ActName, OnTime, OffTime]
- ActName: Full act name (e.g., "ViewBee 40")
- OnTime: Start time in HH:MM format (e.g., "18:00")
- OffTime: End time in HH:MM format (e.g., "18:50")
- Rows ordered chronologically by start time
- The cancelled act does not appear in the result

**Situation B (Entire Gig Cancelled)**:
Returns affected customers:
- Each row: [CustomerName, CustomerEmail]
- CustomerName: Full customer name (e.g., "John Smith")
- CustomerEmail: Customer email address (e.g., "john@example.com")
- Rows ordered alphabetically by customer name (ascending)
- No duplicate customers (DISTINCT)
- If no customers have tickets, returns empty array (0 rows, 2 columns)

**Error Cases**:
- Returns `null` if:
  - Act name is null or empty
  - Gig does not exist
  - Gig is already cancelled
  - Act does not exist
  - Act has no performances in the specified gig
  - Any SQLException occurs during execution

## Task 5

Task 5 calculates how many more tickets (at the cheapest price) need to be sold for each gig to cover all agreed fees and venue costs.

**Input:**
- No input parameters (operates on all gigs in the database)

**Implementation**

- **Single SQL Query Approach**: The solution uses a single complex SQL query with Common Table Expressions (CTEs) to calculate all required values in one database round trip, ensuring efficiency and consistency.

- **Calculation Steps** (implemented as CTEs):

  1. **Act Fees Per Gig** (`act_fees_per_gig`):
     - Groups ACT_GIG records by `gigid` and `actid`
     - Uses `MAX(actgigfee)` to get the fee for each act (all performances by the same act have the same fee per Business Rule 4)
     - This ensures each act is counted only once per gig, even if they perform multiple times

  2. **Gig Costs** (`gig_costs`):
     - Joins GIG with VENUE to get venue hire cost
     - Sums all act fees from the previous CTE
     - Calculates total cost: `total_act_fees + venue_hirecost`
     - Uses `COALESCE` to handle gigs with no acts (defaults to 0)

  3. **Gig Revenue** (`gig_revenue`):
     - Sums all ticket costs from TICKET table for each gig
     - Uses `COALESCE` to handle gigs with no tickets sold (defaults to 0)

  4. **Cheapest Ticket Price** (`gig_cheapest_price`):
     - Finds the minimum price from GIG_TICKET table for each gig
     - This represents the cheapest ticket type available for the gig

  5. **Final Calculation**:
     - Calculates tickets needed: `CEIL((total_cost - total_revenue) / cheapest_price)`
     - If `total_revenue >= total_cost`: returns 0 (already profitable)
     - If no tickets defined or cheapest price is 0: returns 0 (cannot calculate)
     - Otherwise: calculates and rounds up to nearest integer

- **Result Processing**:
  - Converts ResultSet to List first to determine size
  - Converts to 2D String array with format: `[gigID, ticketsToSell]`
  - Returns empty array if no gigs exist

- **Error Handling**:
  - SQLExceptions are caught and logged
  - Returns `null` on any database error

**Design Rationale**

1. **Single Query Efficiency**: Using a single SQL query with CTEs ensures:
   - All calculations are done in one database round trip (better performance)
   - Data consistency (all values calculated from the same database state)
   - Atomic view of the data (no changes between calculations)

2. **CTE Structure**: The use of Common Table Expressions provides:
   - **Readability**: Each step is clearly separated and named
   - **Maintainability**: Easy to modify individual calculation steps
   - **Reusability**: CTEs can reference previous CTEs, avoiding redundant calculations

3. **Business Rule 4 Compliance**: 
   - The query correctly handles the fact that "Acts only receive one fee per gig"
   - By grouping by `gigid` and `actid` and using `MAX(actgigfee)`, we ensure each act is counted once
   - This is correct because all performances by the same act at the same gig have the same fee (enforced by triggers)

4. **Handling Edge Cases**:
   - **Gigs with no acts**: `COALESCE(SUM(af.act_fee), 0)` returns 0, so only venue cost is considered
   - **Gigs with no tickets sold**: `COALESCE(gr.total_revenue, 0)` returns 0
   - **Gigs with no ticket prices defined**: Returns 0 (cannot calculate without price)
   - **Already profitable gigs**: Returns 0 immediately without unnecessary calculations

5. **Ceiling Function**: Uses `CEIL()` to round up to the nearest integer, ensuring that:
   - Partial ticket sales are not possible (must sell whole tickets)
   - The calculation always covers the full cost (never underestimates)

6. **Ordering**: Results are ordered by `gigid ASC` as required by the specification.

7. **Type Conversion**: 
   - Converts INTEGER results to String for the 2D array format
   - Uses `::NUMERIC` in SQL for precise division before ceiling calculation
   - Converts back to INTEGER for the final result

**Output Format**

Returns a 2D String array where each row contains:
- **Column 0**: `gigID` (as String, e.g., "1")
- **Column 1**: `ticketsToSell` (as String, e.g., "1600")

Rows are ordered by `gigID` in ascending order (smallest first).

**Example Output:**
```
{
    {"1", "1600"},
    {"2", "2000"},
    {"3", "1525"},
    {"4", "0"}  // Already profitable
}
```

**Error Cases**:
- Returns `null` if any SQLException occurs during execution
- Returns empty array (0 rows, 2 columns) if no gigs exist in the database

## Task 6

## Task 7

## Task 8








