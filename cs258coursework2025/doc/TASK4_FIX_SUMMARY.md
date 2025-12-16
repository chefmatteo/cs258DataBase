# Task 4 Failure Analysis and Solution

## Problem Summary

Task 4 was failing with the error:
```
ERROR: Ticket cost (0) does not match expected price (40) for gig 40 and pricetype A
```

## Root Cause Analysis

### What Task 4 Does
Task 4 handles act cancellation from a gig. It has two scenarios:
1. **Situation A**: Cancel an act and adjust the schedule (if the act is not the headline act and cancellation doesn't violate interval rules)
2. **Situation B**: Cancel the entire gig (if the act is the headline act or cancellation would violate interval rules)

In the test case:
- Gig ID: 40
- Act to cancel: "Scalar Swift" (which is the only act in the gig, making it the headline act)
- Since it's the headline act, the entire gig should be cancelled

### The Problem
When canceling an entire gig, the `cancelEntireGig()` method performs these steps:
1. Update gig status to 'C' (cancelled)
2. **Update all ticket costs to 0** (this is where it fails)
3. Return list of affected customers

The `validate_ticket_cost()` database trigger requires that ticket costs match the expected price from the `GIG_TICKET` table. When the code tries to set ticket costs to 0, the trigger raises an exception because 0 ≠ 40 (expected price).

## Solution Implemented

Modified the `validate_ticket_cost()` function to allow `cost = 0` when the gig is cancelled:

```sql
-- Check if the gig is cancelled - if so, allow cost = 0
SELECT gigstatus INTO gig_status
FROM GIG
WHERE gigid = NEW.gigid;

IF gig_status = 'C' AND NEW.cost = 0 THEN
    -- Allow cost = 0 for cancelled gigs
    RETURN NEW;
END IF;

-- For active gigs, cost must match expected price
IF NEW.cost != expected_price THEN
    RAISE EXCEPTION 'Ticket cost (%) does not match expected price (%) for gig % and pricetype %',
        NEW.cost, expected_price, NEW.gigid, NEW.pricetype;
END IF;
```

## Test Results

After the fix:
- ✅ Task 4: **PASSED**
- Test output: "Test passed: Gig cancelled successfully, 2 customers affected"

## Files Modified

1. `schema.sql`:
   - Updated `validate_ticket_cost()` function (lines 652-678)

## Key Learnings

1. **Business Logic**: Cancelled gigs should have ticket costs set to 0 (refund scenario)
2. **Trigger Flexibility**: Database triggers should accommodate business operations like cancellations
3. **Context Awareness**: Triggers can check related data (gig status) to make contextual decisions
4. **Test Coverage**: The fix ensures both active gigs (strict price validation) and cancelled gigs (allow zero cost) work correctly

## Current Status
- Tasks 1, 2, 4, 6, 7, 8: ✅ PASSED
- Tasks 3, 5: ❌ FAILED (remaining issues to fix)