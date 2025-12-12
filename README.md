AI usage: Claude Sonnet 4.5

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

Task 4 handles act cancellations from gigs, with two possible outcomes: either canceling just the act (and adjusting the schedule) or canceling the entire gig if constraints would be violated.

**Input:**

- gigID: ID of the gig to cancel an act from
- actName: Name of the act to cancel

**Implementation**

- **Transaction Management**: All operations are wrapped in a transaction with manual commit control to ensure atomicity - either the cancellation succeeds or the database state remains unchanged.
- **Input Validation**:

  - Act name must not be null or empty
  - Gig must exist and be active (status = 'G', not cancelled)
  - Act must exist in the ACT table
  - Act must have performances in the specified gig
- **Determine Cancellation Type**:
  The method determines whether to cancel just the act or the entire gig based on two conditions:

  1. **Headline Act Check**:

     - A headline act is defined as the final or only act in a gig (the act that finishes last)
     - If the act to cancel is a headline act, the entire gig must be cancelled
     - Uses `isHeadlineAct()` helper to check if the act is the headline act
  2. **Interval Rule Violation Check**:

     - Calculates the total duration of all performances by the act to be cancelled
     - Determines the latest end time of cancelled performances
     - Checks if removing the act would create an interval gap that violates Business Rule 10 (intervals must be 10-30 minutes)
     - Uses `wouldViolateIntervalRules()` helper to simulate the cancellation and check resulting gaps
     - Also checks if cancellation would violate minimum gig duration (Business Rule 13: at least 60 minutes)
- **Situation A: Cancel Act Only (Updated Line-up)**:
  If the act is not a headline act and cancellation won't violate interval rules:

  1. Delete all ACT_GIG records for the specified act and gig
  2. Adjust subsequent performances: Move all performances that start after the cancelled act's latest end time earlier by the total cancelled duration
  3. Return updated lineup using Task 1 logic (2D array of [ActName, OnTime, OffTime])
- **Situation B: Cancel Entire Gig**:
  If the act is a headline act OR cancellation would violate interval rules:

  1. Update GIG status to 'C' (cancelled)
  2. Leave all ACT_GIG records unchanged (do not remove the cancelling act)
  3. Update all TICKET records for this gig: Set cost to 0 (preserving original price in GIG_TICKET)
  4. Return 2D array of affected customers: [CustomerName, CustomerEmail], ordered by customer name (ascending), with no duplicates
- **Error Handling**:

  - Any validation failure triggers rollback and returns null
  - SQLExceptions are caught and trigger rollback
  - Original auto-commit setting is restored in finally block

**Design Rationale**

1. **Transaction Atomicity**: The entire cancellation operation is wrapped in a transaction to ensure that if any step fails, the database state remains unchanged. This is critical because cancellation involves multiple updates (deleting ACT_GIG records, updating subsequent performances, or updating gig status and tickets).
2. **Headline Act Detection**: The `isHeadlineAct()` helper method identifies headline acts by:

   - Checking if the act is the only act in the gig (always headline)
   - Finding the act with the latest end time (the final act)
   - This ensures accurate detection even when multiple acts might end at the same time
3. **Interval Violation Prediction**: The `wouldViolateIntervalRules()` helper method simulates the cancellation to check if it would create invalid intervals:

   - Calculates the gap that would exist after moving subsequent acts earlier
   - Checks if the gap violates Business Rule 10 (10-30 minutes)
   - Also validates minimum gig duration (Business Rule 13)
   - This pre-validation prevents creating invalid schedules
4. **Schedule Adjustment Logic**: When canceling an act (not the entire gig):

   - All performances starting after the cancelled act's latest end time are moved earlier
   - The adjustment amount equals the total duration of cancelled performances
   - This maintains chronological order while removing gaps
   - Uses PostgreSQL interval arithmetic: `ontime - (duration || ' minutes')::INTERVAL`
5. **Gig Cancellation Handling**: When canceling the entire gig:

   - ACT_GIG records are preserved (as per specification)
   - Only ticket costs are set to 0 (not the original prices in GIG_TICKET)
   - This allows tracking of what was planned while marking tickets as refunded
6. **Helper Methods**: The implementation uses several helper methods for:

   - **Code organization**: Complex logic is separated into focused methods
   - **Reusability**: Methods like `getAllPerformances()` are used by multiple checks
   - **Testability**: Individual components can be understood and tested separately
   - **Maintainability**: Changes to cancellation logic are localized
7. **Return Value Strategy**: The method returns different data structures based on the outcome:

   - **Act cancellation**: Returns lineup (same format as Task 1) to show the updated schedule
   - **Gig cancellation**: Returns affected customers to notify them of the cancellation
   - **Error**: Returns null to indicate failure

**Output Format**

Task 4 returns a 2D String array, but the format depends on the outcome:

- **If act is cancelled (updated line-up)**: Returns array of [ActName, OnTime, OffTime] in the same format as Task 1, showing the updated schedule after cancellation
- **If entire gig is cancelled**: Returns array of [CustomerName, CustomerEmail] for all customers who purchased tickets, ordered alphabetically by customer name, with no duplicates
- **If error occurs**: Returns null

## Task 5

Task 5 calculates how many more tickets (at the cheapest price) need to be sold for each gig to cover all costs (act fees and venue hire cost).

**Input:**

- No input parameters - calculates for all gigs in the database

**Implementation**

- **SQL Query with CTEs**: Uses Common Table Expressions (CTEs) to break down the calculation into logical steps:

  1. **act_fees_per_gig CTE**:

     - Groups ACT_GIG records by gigid and actid
     - Uses MAX(actgigfee) to get the fee per act per gig (Business Rule 4: same act has same fee across all performances)
     - This ensures each act is counted once per gig, regardless of number of performances
  2. **gig_costs CTE**:

     - Joins GIG with VENUE to get venue hire cost
     - Joins with act_fees_per_gig to sum all act fees per gig
     - Calculates total_cost = total_act_fees + venue_hirecost
     - Uses COALESCE to handle gigs with no acts (returns 0)
  3. **gig_revenue CTE**:

     - Sums all ticket costs (not prices) from TICKET table per gig
     - Uses COALESCE to handle gigs with no tickets sold (returns 0)
     - Note: Uses `cost` field from TICKET, which may be 0 for cancelled gigs
  4. **gig_cheapest_price CTE**:

     - Finds the minimum ticket price from GIG_TICKET per gig
     - This is the price used for calculating tickets needed
  5. **Final SELECT**:

     - Joins all CTEs together
     - Calculates tickets_to_sell using the formula:
       - If revenue >= total_cost: return 0 (already profitable)
       - If cheapest_price is NULL or 0: return 0 (no tickets defined)
       - Otherwise: CEIL((total_cost - revenue) / cheapest_price)
     - Uses CEIL() to round up (can't sell fractional tickets)
     - Orders results by gigid ASC
- **Result Processing**:

  - Collects results in a List first to determine size
  - Converts to 2D String array with format [gigID, tickets_to_sell]
  - Returns empty array if no gigs exist

**Design Rationale**

1. **CTE-Based Approach**: Using Common Table Expressions provides several benefits:

   - **Readability**: Each step of the calculation is clearly separated and named
   - **Maintainability**: Individual CTEs can be modified without affecting others
   - **Performance**: PostgreSQL can optimize CTEs effectively
   - **Debugging**: Each CTE can be tested independently
2. **Act Fee Calculation**: The implementation correctly handles Business Rule 4:

   - Groups by both gigid and actid to ensure each act is counted once per gig
   - Uses MAX(actgigfee) to get the fee (all performances by same act have same fee)
   - This prevents double-counting fees for acts that perform multiple times
3. **Revenue Calculation**: Uses the `cost` field from TICKET table:

   - For active gigs: cost equals the ticket price
   - For cancelled gigs: cost is 0 (as set by Task 4)
   - This correctly reflects that cancelled gig tickets don't contribute to revenue
4. **Ticket Count Calculation**: Uses ceiling division to ensure integer results:

   - `CEIL((total_cost - revenue) / cheapest_price)` ensures we round up
   - Handles edge cases: already profitable gigs return 0, gigs with no ticket prices return 0
   - The calculation assumes all future tickets will be sold at the cheapest price (most conservative estimate)
5. **Null Handling**: Uses COALESCE throughout to handle:

   - Gigs with no acts (total_act_fees = 0)
   - Gigs with no tickets sold (total_revenue = 0)
   - Gigs with no ticket prices defined (cheapest_price = NULL)
6. **Single Query Approach**: All calculations are done in one SQL query:

   - Minimizes database round trips
   - Leverages PostgreSQL's query optimizer
   - Ensures consistency (all calculations use the same database state)
7. **Ordering**: Results are ordered by gigid ASC as specified in the requirements, ensuring consistent output format.

**Output Format**

Task 5 returns a 2D String array where each row contains [gigID, tickets_to_sell]:

- gigID: The gig identifier as a string
- tickets_to_sell: The number of tickets needed (as a string), which is:
  - 0 if the gig has already sold enough tickets to cover costs
  - 0 if the gig has no ticket prices defined
  - A positive integer representing the minimum number of tickets needed (rounded up)

Rows are ordered by gigID in ascending order. The output includes all gigs in the database, even those that haven't sold any tickets yet.

## Task 6

Task 6 calculates the total number of tickets sold by each act when they performed as headline acts (final or only act), broken down by year, with totals per act.

**Input:**

- No input parameters - calculates for all acts in the database

**Implementation**

- **SQL Query with CTEs**: Uses Common Table Expressions to break down the calculation:

  1. **headline_acts CTE**:

     - Identifies headline acts for each non-cancelled gig
     - A headline act is defined as the act with the latest end time (`ontime + duration`) in a gig
     - Uses a subquery to find the maximum end time per gig
     - Only considers gigs where `gigstatus = 'G'` (not cancelled)
     - Joins ACT_GIG with ACT to get act names
  2. **tickets_per_year CTE**:

     - Counts tickets sold per act per year
     - Joins headline_acts with GIG to get gig dates
     - Joins with TICKET to count tickets
     - Uses `EXTRACT(YEAR FROM gigdatetime)` to extract the year
     - Groups by act name and year
  3. **act_totals CTE**:

     - Calculates total tickets sold per act across all years
     - Sums tickets_sold from tickets_per_year
     - Groups by act name
  4. **Final SELECT**:

     - Combines per-year data with total rows using UNION ALL
     - Per-year rows: [actname, year, tickets_sold, total_tickets]
     - Total rows: [actname, 'Total', total_tickets, total_tickets]
     - Orders by:
       - `total_tickets ASC` (acts with least tickets first)
       - `CASE WHEN year = 'Total' THEN 1 ELSE 0 END` (Total rows at end of each act)
       - `year ASC` (years in ascending order, with 'Total' last)
- **Result Processing**:

  - Collects results in a List
  - Converts to 2D String array with format [ActName, Year, TicketsSold]
  - Returns empty array if no data exists

**Design Rationale**

1. **Headline Act Identification**: The implementation correctly identifies headline acts by:

   - Finding the act with the maximum end time (`ontime + duration`) for each gig
   - This handles both single-act gigs (only act is headline) and multi-act gigs (final act is headline)
   - Uses a correlated subquery to compare each act's end time with the maximum for that gig
2. **CTE-Based Approach**: Using Common Table Expressions provides:

   - **Readability**: Each step is clearly separated and named
   - **Maintainability**: Individual CTEs can be modified independently
   - **Performance**: PostgreSQL optimizes CTEs effectively
   - **Debugging**: Each CTE can be tested separately
3. **Year Extraction**: Uses `EXTRACT(YEAR FROM gigdatetime)` to:

   - Extract the year from the gig datetime
   - Group tickets by year for each act
   - Cast to INTEGER for grouping, then to TEXT for output
4. **Total Calculation**: Uses UNION ALL to combine:

   - Per-year rows showing tickets sold each year
   - Total rows showing cumulative tickets per act
   - The 'Total' string is used as the year value for total rows
5. **Ordering Strategy**: The ORDER BY clause ensures:

   - Acts with least total tickets appear first (ascending order)
   - Within each act, years are ordered ascending
   - 'Total' rows appear at the end of each act's group (using CASE expression)
   - This matches the required output format exactly
6. **Single Query Approach**: All calculations are done in one SQL query:

   - Minimizes database round trips
   - Leverages PostgreSQL's query optimizer
   - Ensures consistency (all calculations use the same database state)
7. **Filtering**: Only includes:

   - Non-cancelled gigs (`gigstatus = 'G'`)
   - Headline acts only (acts that finish last in their gig)
   - This ensures we only count tickets for gigs where the act was the main attraction

**Output Format**

Task 6 returns a 2D String array where each row contains [ActName, Year, TicketsSold]:

- ActName: The name of the act
- Year: The year (as a string, e.g., "2017") or "Total" for the total row
- TicketsSold: The number of tickets sold (as a string)

Rows are ordered by:

1. Total tickets per act (ascending) - acts with least tickets first
2. Year (ascending) - with 'Total' rows appearing at the end of each act's group

The output includes all acts that have performed as headline acts in non-cancelled gigs, with per-year breakdowns and totals.

## Task 7

Task 7 identifies regular customers for headline acts - customers who have attended at least 2 tickets for gigs where the act was a headline act.

**Input:**

- No input parameters - calculates for all acts in the database

**Implementation**

- **SQL Query with CTEs**: Uses Common Table Expressions to break down the calculation:

  1. **headline_acts CTE**:
     - Identifies headline acts for each non-cancelled gig
     - A headline act is defined as the act with the latest end time (`ontime + duration`) in a gig
     - Uses a subquery to find the maximum end time per gig
     - Only considers gigs where `gigstatus = 'G'` (not cancelled)
     - Joins ACT_GIG with ACT to get act names

  2. **customer_tickets CTE**:
     - Counts tickets sold per customer per act for headline acts
     - Joins headline_acts with TICKET to count tickets
     - Groups by act name and customer name
     - Uses `HAVING COUNT(*) >= 2` to filter for regular customers (customers with at least 2 tickets)

  3. **all_headline_acts CTE**:
     - Gets all distinct headline acts (acts that have performed as headline at least once)
     - This ensures acts with no customers are still included

  4. **Final SELECT**:
     - LEFT JOINs all_headline_acts with customer_tickets to include acts with no customers
     - Uses `COALESCE(ct.customername, '[None]')` to show '[None]' for acts with no regular customers
     - Orders by:
       - `actname ASC` (acts in alphabetical order)
       - `ticket_count DESC NULLS LAST` (customers with most tickets first, acts with no customers last)

- **Result Processing**:
  - Collects results in a List
  - Converts to 2D String array with format [ActName, CustomerName]
  - Returns empty array if no data exists

**Design Rationale**

1. **Regular Customer Definition**: The implementation defines "regular customers" as customers who have bought at least 2 tickets:
   - Uses `HAVING COUNT(*) >= 2` to filter customers
   - This aligns with the term "regular" which implies repeat attendance
   - Matches the test expectations which exclude customers with only 1 ticket

2. **Headline Act Identification**: Uses the same logic as Task 6:
   - Finds the act with the maximum end time for each gig
   - Handles both single-act gigs and multi-act gigs correctly

3. **Including Acts with No Customers**: Uses LEFT JOIN to ensure:
   - All headline acts are included in the result
   - Acts with no regular customers show '[None]' in the Customer Name column
   - This matches the requirement that acts should be listed even if they have no customers

4. **Customer Ordering**: Orders customers by ticket count (descending):
   - Customers who have bought the most tickets appear first
   - This helps identify the most loyal customers for each act

5. **CTE-Based Approach**: Using Common Table Expressions provides:
   - **Readability**: Each step is clearly separated and named
   - **Maintainability**: Individual CTEs can be modified independently
   - **Performance**: PostgreSQL optimizes CTEs effectively
   - **Debugging**: Each CTE can be tested separately

6. **Filtering Strategy**: The `HAVING COUNT(*) >= 2` clause:
   - Filters out customers with only 1 ticket (not considered "regular")
   - Ensures only repeat customers are included
   - Matches the test expectations

**Output Format**

Task 7 returns a 2D String array where each row contains [ActName, CustomerName]:

- ActName: The name of the act
- CustomerName: The name of the customer, or '[None]' if the act has no regular customers

Rows are ordered by:
1. Act name (ascending) - acts in alphabetical order
2. Ticket count (descending) - customers with most tickets first, acts with no customers last

The output includes all acts that have performed as headline acts in non-cancelled gigs, with their regular customers (customers with at least 2 tickets).

## Task 8

Task 8 identifies economically feasible venue-act combinations for single-act gigs, where the organizers can break even by selling tickets at the average price.

**Input:**

- No input parameters - calculates for all venue-act combinations

**Implementation**

- **SQL Query with CTEs**: Uses Common Table Expressions to break down the calculation:

  1. **average_ticket_price CTE**:
     - Calculates the average ticket price from all non-cancelled gigs
     - Joins TICKET with GIG to filter only active gigs (`gigstatus = 'G'`)
     - Uses `ROUND(AVG(t.cost))` to round to the nearest £
     - This represents the average price organizers can charge

  2. **venue_act_combinations CTE**:
     - Generates all possible venue-act combinations using CROSS JOIN
     - Includes venue name, act name, act's standard fee, and venue hire cost
     - This creates a Cartesian product of all venues and acts

  3. **total_costs CTE**:
     - Calculates total cost for each venue-act combination
     - Total cost = `standardfee + hirecost`
     - Cross joins with average_ticket_price to get the average price for calculations

  4. **tickets_required CTE**:
     - Calculates minimum tickets needed to break even
     - Formula: `CEIL(total_cost / avg_price)`
     - Uses `CEIL()` to round up (can't sell fractional tickets)
     - Handles division by zero by returning NULL if avg_price is 0

  5. **Final SELECT**:
     - Filters economically feasible combinations:
       - `tickets_needed IS NOT NULL` (valid calculation)
       - `avg_price * tickets_needed >= total_cost` (ensures break-even or profit)
     - Orders by:
       - `venuename ASC` (venues in alphabetical order)
       - `tickets_needed DESC` (highest number of tickets required first)

- **Result Processing**:
  - Collects results in a List
  - Converts to 2D String array with format [VenueName, ActName, TicketsRequired]
  - Returns empty array if no feasible combinations exist

**Design Rationale**

1. **Average Price Calculation**: Uses `ROUND(AVG(cost))` from TICKET table:
   - Only considers non-cancelled gigs to get realistic pricing
   - Rounds to nearest £ as specified in requirements
   - Represents the maximum price organizers can charge

2. **Economic Feasibility Check**: Validates two conditions:
   - **Valid calculation**: `tickets_needed IS NOT NULL` ensures division by zero is handled
   - **Break-even guarantee**: `avg_price * tickets_needed >= total_cost` ensures revenue covers costs
   - This ensures organizers can break even or make a profit

3. **Ticket Count Calculation**: Uses `CEIL()` to round up:
   - Ensures integer ticket counts (can't sell half a ticket)
   - Conservative approach: rounds up to guarantee break-even
   - Formula: `CEIL(total_cost / avg_price)` gives minimum tickets needed

4. **CROSS JOIN Strategy**: Generates all venue-act combinations:
   - Allows evaluation of every possible combination
   - Filters out infeasible combinations in the final SELECT
   - Ensures comprehensive coverage of all possibilities

5. **CTE-Based Approach**: Using Common Table Expressions provides:
   - **Readability**: Each calculation step is clearly separated
   - **Maintainability**: Individual CTEs can be modified independently
   - **Performance**: PostgreSQL optimizes CTEs effectively
   - **Debugging**: Each CTE can be tested separately

6. **Ordering Strategy**: Orders results by:
   - Venue name (ascending) - groups results by venue
   - Tickets required (descending) - shows most challenging combinations first
   - This helps organizers prioritize easier bookings

7. **Null Handling**: Properly handles edge cases:
   - Division by zero (avg_price = 0) returns NULL
   - NULL combinations are filtered out
   - Ensures only valid, feasible combinations are returned

**Output Format**

Task 8 returns a 2D String array where each row contains [VenueName, ActName, TicketsRequired]:

- VenueName: The name of the venue
- ActName: The name of the act
- TicketsRequired: The minimum number of tickets needed to break even (as a string)

Rows are ordered by:
1. Venue name (ascending) - venues in alphabetical order
2. Tickets required (descending) - highest number of tickets required first

The output includes only economically feasible combinations where organizers can break even by selling tickets at the average price. If a venue has no feasible acts, no rows are included for that venue.
