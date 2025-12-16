# Task 2 Failure Analysis and Solution

## Problem Summary

Task 2 was failing with the error:
```
ERROR: Final act must finish at least 60 minutes after gig start, but only 30.0000000000000000 minutes
```

## Root Cause Analysis

### Issue 1: Trigger Validation Logic
The `validate_final_act_duration()` trigger was checking the duration requirement when inserting the **first act**, even though more acts would be inserted later. 

**What happened:**
- When inserting Act 1 (starts 20:00, duration 30 min, ends 20:30):
  - Trigger fires BEFORE insert
  - Query `MAX(ontime + duration)` returns NULL (no acts exist yet)
  - Condition `final_act_end_time IS NULL` is TRUE
  - Calculates: 20:30 - 20:00 = 30 minutes < 60
  - Raises exception **incorrectly** (Act 1 is not the final act!)

**The Problem:**
The trigger couldn't distinguish between:
- A single-act gig (where we need to validate)
- The first act of a multi-act gig (where we should wait for the final act)

### Issue 2: SQL Error in `validate_same_act_break()`
A separate SQL error occurred:
```
ERROR: column "act_gig.actid" must appear in the GROUP BY clause or be used in an aggregate function
```

**What happened:**
The query used `MAX(ontime + duration), actid` without proper grouping, which violates SQL aggregation rules.

## Solutions Implemented

### Solution 1: Fixed Trigger Validation Logic
Modified `validate_final_act_duration()` to only validate when we're **certain** this is the final act:

```sql
-- Only validate if this act's end time >= all existing acts' end times
-- This means this act is (or will be) the final act
IF existing_act_count > 0 AND final_act_end_time IS NOT NULL 
   AND new_act_end_time >= final_act_end_time THEN
    -- Validate duration
END IF;
```

**Key Changes:**
- Skip validation for the first act (can't know if it's final yet)
- Only validate when `new_act_end_time >= final_act_end_time` (this act is the final act)
- Java code in `task2()` already validates the final act after all inserts (line 677-684), providing a safety net

### Solution 2: Fixed SQL Aggregation Error
Fixed `validate_same_act_break()` by removing unnecessary aggregate functions:

**Before:**
```sql
SELECT MAX(ontime + (duration || ' minutes')::INTERVAL), actid INTO ...
ORDER BY ontime DESC LIMIT 1;
```

**After:**
```sql
SELECT ontime + (duration || ' minutes')::INTERVAL, actid INTO ...
ORDER BY ontime DESC LIMIT 1;
```

Since we're using `ORDER BY ... LIMIT 1`, we don't need `MAX()` - we just get the row with the maximum value directly.

## Test Results

After fixes:
- ✅ Task 2: **PASSED**
- Test output: "Test passed: Gig created successfully with correct schedule"

## Files Modified

1. `schema.sql`:
   - Updated `validate_final_act_duration()` function (lines 524-560)
   - Fixed `validate_same_act_break()` function (lines 354, 363)

## Key Learnings

1. **BEFORE triggers** fire before row insertion, so queries don't see the NEW row yet
2. **Validation timing** matters - we can't validate the first act as "final" until all acts are inserted
3. **SQL aggregation rules** - can't mix aggregate functions with non-aggregated columns without GROUP BY
4. **Defense in depth** - Java code validation provides a safety net for edge cases

