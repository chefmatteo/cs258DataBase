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

## Task 3

## Task 4

## Task 5

## Task 6

## Task 7

## Task 8

