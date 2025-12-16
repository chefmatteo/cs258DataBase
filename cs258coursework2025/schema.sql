-- Database Schema for GigSystem
-- This schema creates all tables, sequences, and constraints needed for the coursework

-- for clean reset: 
DROP TABLE IF EXISTS TICKET CASCADE;
DROP TABLE IF EXISTS GIG_TICKET CASCADE;
DROP TABLE IF EXISTS ACT_GIG CASCADE;
DROP TABLE IF EXISTS GIG CASCADE;
DROP TABLE IF EXISTS VENUE CASCADE;
DROP TABLE IF EXISTS ACT CASCADE;

-- Drop sequences if they exist
DROP SEQUENCE IF EXISTS act_actid_seq CASCADE;
DROP SEQUENCE IF EXISTS venue_venueid_seq CASCADE;
DROP SEQUENCE IF EXISTS gig_gigid_seq CASCADE;
DROP SEQUENCE IF EXISTS ticket_ticketid_seq CASCADE;

-- Drop triggers if they exist
DROP TRIGGER IF EXISTS trigger_validate_act_gig_ontime ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_prevent_overlapping_performances ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_act_gap ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_ticket_cost ON TICKET CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_venue_capacity ON TICKET CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_first_act_start ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_prevent_act_simultaneous_gigs ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_act_travel_gap ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_venue_gap ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_interval_duration ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_same_act_break ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_final_act_duration ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_gig_finish_time ON ACT_GIG CASCADE;
DROP TRIGGER IF EXISTS trigger_validate_act_fee_per_gig ON ACT_GIG CASCADE;

-- Drop functions if they exist
DROP FUNCTION IF EXISTS validate_act_gig_ontime() CASCADE;
DROP FUNCTION IF EXISTS prevent_overlapping_performances() CASCADE;
DROP FUNCTION IF EXISTS validate_act_gap() CASCADE;
DROP FUNCTION IF EXISTS validate_ticket_cost() CASCADE;
DROP FUNCTION IF EXISTS validate_venue_capacity() CASCADE;
DROP FUNCTION IF EXISTS validate_first_act_start() CASCADE;
DROP FUNCTION IF EXISTS prevent_act_simultaneous_gigs() CASCADE;
DROP FUNCTION IF EXISTS validate_act_travel_gap() CASCADE;
DROP FUNCTION IF EXISTS validate_venue_gap_on_act_change() CASCADE;
DROP FUNCTION IF EXISTS validate_interval_duration() CASCADE;
DROP FUNCTION IF EXISTS validate_same_act_break() CASCADE;
DROP FUNCTION IF EXISTS validate_final_act_duration() CASCADE;
DROP FUNCTION IF EXISTS validate_gig_finish_time() CASCADE;
DROP FUNCTION IF EXISTS validate_act_fee_per_gig() CASCADE;

-- ============================================
-- ACT Table
-- Stores information about musical acts
-- ============================================
CREATE TABLE ACT (
    actid INTEGER PRIMARY KEY,
    actname VARCHAR(255) NOT NULL,
    genre VARCHAR(100),
    standardfee INTEGER NOT NULL CHECK (standardfee >= 0)
);

-- Sequence for ACT table (auto-increment actid)
CREATE SEQUENCE act_actid_seq
    START WITH 10001
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- Set default value for actid to use sequence
ALTER TABLE ACT ALTER COLUMN actid SET DEFAULT nextval('act_actid_seq');

-- VENUE Table
-- Stores information about venues
CREATE TABLE VENUE (
    venueid INTEGER PRIMARY KEY,
    venuename VARCHAR(100) NOT NULL,
    hirecost INTEGER NOT NULL CHECK (hirecost >= 0),
    capacity INTEGER NOT NULL CHECK (capacity > 0)
);

-- Sequence for VENUE table
CREATE SEQUENCE venue_venueid_seq
    START WITH 10001
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE VENUE ALTER COLUMN venueid SET DEFAULT nextval('venue_venueid_seq');

-- ============================================
-- GIG Table
-- Stores information about gigs/concerts
-- ============================================
CREATE TABLE GIG (
    gigid INTEGER PRIMARY KEY,
    venueid INTEGER NOT NULL,
    gigtitle VARCHAR(255) NOT NULL,
    gigdatetime TIMESTAMP NOT NULL,
    gigstatus CHAR(1) NOT NULL CHECK (gigstatus IN ('G', 'C')), -- 'G' = Going ahead, 'C' = Cancelled
    FOREIGN KEY (venueid) REFERENCES VENUE(venueid) ON DELETE CASCADE,
    -- Business Rule 15: Gigs start between 9am and 11:59pm
    CONSTRAINT gig_start_time_check CHECK (
        EXTRACT(HOUR FROM gigdatetime) >= 9 
        AND (EXTRACT(HOUR FROM gigdatetime) < 23 OR (EXTRACT(HOUR FROM gigdatetime) = 23 AND EXTRACT(MINUTE FROM gigdatetime) <= 59))
    )
);

-- Sequence for GIG table
CREATE SEQUENCE gig_gigid_seq
    START WITH 10001
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE GIG ALTER COLUMN gigid SET DEFAULT nextval('gig_gigid_seq');

-- ============================================
-- ACT_GIG Table
-- Junction table linking acts to gigs
-- Stores performance details for each act at each gig
-- ============================================
CREATE TABLE ACT_GIG (
    actid INTEGER NOT NULL,
    gigid INTEGER NOT NULL,
    actgigfee INTEGER NOT NULL CHECK (actgigfee >= 0),
    ontime TIMESTAMP NOT NULL,
    duration INTEGER NOT NULL CHECK (duration >= 15 AND duration <= 90), -- Business Rule 5: 15-90 minutes
    PRIMARY KEY (actid, gigid, ontime), -- Composite primary key
    FOREIGN KEY (actid) REFERENCES ACT(actid) ON DELETE CASCADE,
    FOREIGN KEY (gigid) REFERENCES GIG(gigid) ON DELETE CASCADE
);

-- ============================================
-- GIG_TICKET Table
-- Stores ticket pricing information for each gig
-- ============================================
CREATE TABLE GIG_TICKET (
    gigid INTEGER NOT NULL,
    pricetype CHAR(1) NOT NULL, -- 'A' = Adult, etc.
    price INTEGER NOT NULL CHECK (price >= 0),
    PRIMARY KEY (gigid, pricetype),
    FOREIGN KEY (gigid) REFERENCES GIG(gigid) ON DELETE CASCADE
);

-- ============================================
-- TICKET Table
-- Stores individual ticket purchases
-- ============================================
CREATE TABLE TICKET (
    ticketid INTEGER PRIMARY KEY,
    gigid INTEGER NOT NULL,
    customername VARCHAR(255) NOT NULL,
    customeremail VARCHAR(255) NOT NULL,
    pricetype CHAR(1) NOT NULL,
    cost INTEGER NOT NULL CHECK (cost >= 0),
    FOREIGN KEY (gigid) REFERENCES GIG(gigid) ON DELETE CASCADE,
    -- Business Rule: Ticket pricetype must match a valid GIG_TICKET entry
    FOREIGN KEY (gigid, pricetype) REFERENCES GIG_TICKET(gigid, pricetype) ON DELETE CASCADE
);

-- Sequence for TICKET table
CREATE SEQUENCE ticket_ticketid_seq
    START WITH 10001
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER TABLE TICKET ALTER COLUMN ticketid SET DEFAULT nextval('ticket_ticketid_seq');

-- ============================================
-- Indexes for better query performance
-- ============================================
-- Index on ACT_GIG for Task 1 queries (filtering by gigid and ordering by ontime)
CREATE INDEX idx_act_gig_gigid_ontime ON ACT_GIG(gigid, ontime);

-- Index on GIG for venue lookups
CREATE INDEX idx_gig_venueid ON GIG(venueid);

-- Index on TICKET for gig lookups
CREATE INDEX idx_ticket_gigid ON TICKET(gigid);

-- ============================================
-- Functions and Triggers for Business Rules
-- ============================================

-- Business Rule 11: First act must start at gigdatetime
CREATE OR REPLACE FUNCTION validate_first_act_start()
RETURNS TRIGGER AS $$
DECLARE
    gig_start_time TIMESTAMP;
    first_act_ontime TIMESTAMP;
BEGIN
    SELECT gigdatetime INTO gig_start_time
    FROM GIG
    WHERE gigid = NEW.gigid;
    
    -- Find the earliest act for this gig (excluding current row on update)
    SELECT MIN(ontime) INTO first_act_ontime
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime));
    
    -- If this is the first act (or will be after update), it must start at gigdatetime
    IF first_act_ontime IS NULL OR NEW.ontime <= first_act_ontime THEN
        IF NEW.ontime != gig_start_time THEN
            RAISE EXCEPTION 'First act must start at gig datetime (%), but got %', gig_start_time, NEW.ontime;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_first_act_start
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_first_act_start();

-- Business Rule 1: No overlap between acts at a gig (can start exactly when previous finishes)
CREATE OR REPLACE FUNCTION prevent_overlapping_performances()
RETURNS TRIGGER AS $$
DECLARE
    overlap_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO overlap_count
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime))  -- Exclude old row on update
      AND (
          -- New performance starts during an existing performance (but not exactly when it ends)
          (NEW.ontime > ontime AND NEW.ontime < ontime + (duration || ' minutes')::INTERVAL)
          OR
          -- New performance ends during an existing performance (but not exactly when it starts)
          (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL > ontime 
           AND NEW.ontime + (NEW.duration || ' minutes')::INTERVAL < ontime + (duration || ' minutes')::INTERVAL)
          OR
          -- New performance completely contains an existing performance
          (NEW.ontime < ontime AND NEW.ontime + (NEW.duration || ' minutes')::INTERVAL > ontime + (duration || ' minutes')::INTERVAL)
      );
    
    IF overlap_count > 0 THEN
        RAISE EXCEPTION 'Act performance overlaps with another performance at the same gig';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_prevent_overlapping_performances
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION prevent_overlapping_performances();

-- Business Rule 10: Intervals (breaks where no act is playing) must be 10-30 mins
CREATE OR REPLACE FUNCTION validate_interval_duration()
RETURNS TRIGGER AS $$
DECLARE
    prev_end_time TIMESTAMP;
    next_start_time TIMESTAMP;
    gap_minutes NUMERIC;
BEGIN
    -- Check gap before this act (previous act's end to this act's start)
    SELECT MAX(ontime + (duration || ' minutes')::INTERVAL) INTO prev_end_time
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND ontime < NEW.ontime
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime));
    
    IF prev_end_time IS NOT NULL AND NEW.ontime > prev_end_time THEN
        gap_minutes := EXTRACT(EPOCH FROM (NEW.ontime - prev_end_time)) / 60;
        IF gap_minutes < 10 OR gap_minutes > 30 THEN
            RAISE EXCEPTION 'Interval between acts (%, % minutes) must be between 10 and 30 minutes', 
                prev_end_time, gap_minutes;
        END IF;
    END IF;
    
    -- Check gap after this act (this act's end to next act's start)
    SELECT MIN(ontime) INTO next_start_time
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND ontime > NEW.ontime
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime));
    
    IF next_start_time IS NOT NULL AND next_start_time > (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) THEN
        gap_minutes := EXTRACT(EPOCH FROM (next_start_time - (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL))) / 60;
        IF gap_minutes < 10 OR gap_minutes > 30 THEN
            RAISE EXCEPTION 'Interval between acts (%, % minutes) must be between 10 and 30 minutes', 
                NEW.ontime + (NEW.duration || ' minutes')::INTERVAL, gap_minutes;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_interval_duration
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_interval_duration();

-- Business Rule 2: Acts cannot perform in multiple gigs at the same time (ignore cancelled gigs)
CREATE OR REPLACE FUNCTION prevent_act_simultaneous_gigs()
RETURNS TRIGGER AS $$
DECLARE
    overlap_count INTEGER;
    new_end_time TIMESTAMP;
BEGIN
    new_end_time := NEW.ontime + (NEW.duration || ' minutes')::INTERVAL;
    
    -- Check for overlapping performances in other gigs (only non-cancelled gigs)
    SELECT COUNT(*) INTO overlap_count
    FROM ACT_GIG ag
    JOIN GIG g ON ag.gigid = g.gigid
    WHERE ag.actid = NEW.actid
      AND g.gigstatus = 'G'  -- Business Rule 16: Ignore cancelled gigs
      AND g.gigid != NEW.gigid
      AND (TG_OP = 'INSERT' OR (ag.actid, ag.gigid, ag.ontime) != (OLD.actid, OLD.gigid, OLD.ontime))
      AND (
          -- New performance overlaps with existing performance
          (NEW.ontime >= ag.ontime AND NEW.ontime < ag.ontime + (ag.duration || ' minutes')::INTERVAL)
          OR
          (new_end_time > ag.ontime AND new_end_time <= ag.ontime + (ag.duration || ' minutes')::INTERVAL)
          OR
          (NEW.ontime <= ag.ontime AND new_end_time >= ag.ontime + (ag.duration || ' minutes')::INTERVAL)
      );
    
    IF overlap_count > 0 THEN
        RAISE EXCEPTION 'Act cannot perform in multiple gigs at the same time';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_prevent_act_simultaneous_gigs
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION prevent_act_simultaneous_gigs();

-- Business Rule 6: Same act cannot perform twice without a break (interval or different act)
CREATE OR REPLACE FUNCTION validate_same_act_break()
RETURNS TRIGGER AS $$
DECLARE
    prev_end_time TIMESTAMP;
    next_start_time TIMESTAMP;
    prev_act_id INTEGER;
    next_act_id INTEGER;
BEGIN
    -- Find previous performance end time and act
    SELECT MAX(ontime + (duration || ' minutes')::INTERVAL), actid INTO prev_end_time, prev_act_id
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND ontime < NEW.ontime
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime))
    ORDER BY ontime DESC
    LIMIT 1;
    
    -- Find next performance start time and act
    SELECT MIN(ontime), actid INTO next_start_time, next_act_id
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND ontime > NEW.ontime
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime))
    ORDER BY ontime ASC
    LIMIT 1;
    
    -- Check if same act performs consecutively without break
    IF prev_end_time IS NOT NULL AND prev_act_id = NEW.actid AND NEW.ontime = prev_end_time THEN
        RAISE EXCEPTION 'Same act cannot perform twice without a break (interval or different act)';
    END IF;
    
    IF next_start_time IS NOT NULL AND next_act_id = NEW.actid AND next_start_time = (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) THEN
        RAISE EXCEPTION 'Same act cannot perform twice without a break (interval or different act)';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_same_act_break
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_same_act_break();

-- Business Rule 7: Acts need 60 mins gap to travel between venues (ignore cancelled gigs)
CREATE OR REPLACE FUNCTION validate_act_travel_gap()
RETURNS TRIGGER AS $$
DECLARE
    other_gig_end_time TIMESTAMP;
    other_gig_start_time TIMESTAMP;
    gap_minutes NUMERIC;
    new_end_time TIMESTAMP;
BEGIN
    new_end_time := NEW.ontime + (NEW.duration || ' minutes')::INTERVAL;
    
    -- Check if act has another gig on the same day with insufficient travel time
    -- Find the latest end time of another performance by this act that ends before this one starts
    SELECT MAX(ag.ontime + (ag.duration || ' minutes')::INTERVAL) INTO other_gig_end_time
    FROM ACT_GIG ag
    JOIN GIG g ON ag.gigid = g.gigid
    WHERE ag.actid = NEW.actid
      AND g.gigstatus = 'G'  -- Business Rule 16: Ignore cancelled gigs
      AND g.gigid != NEW.gigid
      AND DATE(ag.ontime) = DATE(NEW.ontime)  -- Same day
      AND (TG_OP = 'INSERT' OR (ag.actid, ag.gigid, ag.ontime) != (OLD.actid, OLD.gigid, OLD.ontime))
      AND (ag.ontime + (ag.duration || ' minutes')::INTERVAL) <= NEW.ontime;
    
    IF other_gig_end_time IS NOT NULL THEN
        -- Other gig ends before this one starts - check gap
        gap_minutes := EXTRACT(EPOCH FROM (NEW.ontime - other_gig_end_time)) / 60;
        IF gap_minutes < 60 THEN
            RAISE EXCEPTION 'Act needs 60 minutes gap to travel between venues, but only % minutes available', gap_minutes;
        END IF;
    END IF;
    
    -- Find the earliest start time of another performance by this act that starts after this one ends
    SELECT MIN(ag.ontime) INTO other_gig_start_time
    FROM ACT_GIG ag
    JOIN GIG g ON ag.gigid = g.gigid
    WHERE ag.actid = NEW.actid
      AND g.gigstatus = 'G'  -- Business Rule 16: Ignore cancelled gigs
      AND g.gigid != NEW.gigid
      AND DATE(ag.ontime) = DATE(NEW.ontime)  -- Same day
      AND (TG_OP = 'INSERT' OR (ag.actid, ag.gigid, ag.ontime) != (OLD.actid, OLD.gigid, OLD.ontime))
      AND ag.ontime >= new_end_time;
    
    IF other_gig_start_time IS NOT NULL THEN
        -- This gig ends before other one starts - check gap
        gap_minutes := EXTRACT(EPOCH FROM (other_gig_start_time - new_end_time)) / 60;
        IF gap_minutes < 60 THEN
            RAISE EXCEPTION 'Act needs 60 minutes gap to travel between venues, but only % minutes available', gap_minutes;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_act_travel_gap
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_act_travel_gap();

-- Business Rule 9: Venues need 180 mins gap between gigs (ignore cancelled gigs)
-- This is validated when acts are added/updated, checking against other gigs at the same venue
CREATE OR REPLACE FUNCTION validate_venue_gap_on_act_change()
RETURNS TRIGGER AS $$
DECLARE
    other_gig_end_time TIMESTAMP;
    other_gig_start_time TIMESTAMP;
    gap_minutes NUMERIC;
    new_gig_end_time TIMESTAMP;
    new_gig_start_time TIMESTAMP;
    venue_id INTEGER;
BEGIN
    -- Get venue and gig start time
    SELECT venueid, gigdatetime INTO venue_id, new_gig_start_time
    FROM GIG
    WHERE gigid = NEW.gigid;
    
    -- Calculate this gig's end time (latest act end time)
    SELECT MAX(ontime + (duration || ' minutes')::INTERVAL) INTO new_gig_end_time
    FROM ACT_GIG
    WHERE gigid = NEW.gigid;
    
    -- If no acts yet, use gig start time as end time
    IF new_gig_end_time IS NULL THEN
        new_gig_end_time := new_gig_start_time;
    END IF;
    
    -- Check for other gigs at the same venue on the same day (non-cancelled)
    -- Find the latest end time of gigs that end before this one starts
    SELECT MAX(gig_end_time) INTO other_gig_end_time
    FROM (
        SELECT g2.gigid,
               COALESCE(MAX(ag2.ontime + (ag2.duration || ' minutes')::INTERVAL), g2.gigdatetime) AS gig_end_time
        FROM GIG g2
        LEFT JOIN ACT_GIG ag2 ON g2.gigid = ag2.gigid
        WHERE g2.venueid = venue_id
          AND g2.gigstatus = 'G'  -- Business Rule 16: Ignore cancelled gigs
          AND g2.gigid != NEW.gigid
          AND DATE(g2.gigdatetime) = DATE(new_gig_start_time)
        GROUP BY g2.gigid, g2.gigdatetime
    ) AS other_gigs
    WHERE gig_end_time <= new_gig_start_time;
    
    IF other_gig_end_time IS NOT NULL THEN
        gap_minutes := EXTRACT(EPOCH FROM (new_gig_start_time - other_gig_end_time)) / 60;
        IF gap_minutes < 180 THEN
            RAISE EXCEPTION 'Venues need 180 minutes gap between gigs, but only % minutes available', gap_minutes;
        END IF;
    END IF;
    
    -- Find gigs that start after this one ends
    SELECT MIN(g2.gigdatetime) INTO other_gig_start_time
    FROM GIG g2
    WHERE g2.venueid = venue_id
      AND g2.gigstatus = 'G'  -- Business Rule 16: Ignore cancelled gigs
      AND g2.gigid != NEW.gigid
      AND DATE(g2.gigdatetime) = DATE(new_gig_start_time)
      AND g2.gigdatetime >= new_gig_end_time;
    
    IF other_gig_start_time IS NOT NULL THEN
        gap_minutes := EXTRACT(EPOCH FROM (other_gig_start_time - new_gig_end_time)) / 60;
        IF gap_minutes < 180 THEN
            RAISE EXCEPTION 'Venues need 180 minutes gap between gigs, but only % minutes available', gap_minutes;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_venue_gap
    AFTER INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_venue_gap_on_act_change();

-- Business Rule 13: Final act must finish at least 60 mins after gig start
CREATE OR REPLACE FUNCTION validate_final_act_duration()
RETURNS TRIGGER AS $$
DECLARE
    gig_start_time TIMESTAMP;
    final_act_end_time TIMESTAMP;
    duration_minutes NUMERIC;
BEGIN
    SELECT gigdatetime INTO gig_start_time
    FROM GIG
    WHERE gigid = NEW.gigid;
    
    -- Find the latest act end time for this gig
    SELECT MAX(ontime + (duration || ' minutes')::INTERVAL) INTO final_act_end_time
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime));
    
    -- If this is the final act (or will be after update), check duration
    IF final_act_end_time IS NULL OR (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) >= final_act_end_time THEN
        duration_minutes := EXTRACT(EPOCH FROM ((NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) - gig_start_time)) / 60;
        IF duration_minutes < 60 THEN
            RAISE EXCEPTION 'Final act must finish at least 60 minutes after gig start, but only % minutes', duration_minutes;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_final_act_duration
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_final_act_duration();

-- Business Rule 14: Rock/pop gigs finish by 11pm, others by 1am
CREATE OR REPLACE FUNCTION validate_gig_finish_time()
RETURNS TRIGGER AS $$
DECLARE
    gig_start_time TIMESTAMP;
    final_act_end_time TIMESTAMP;
    finish_hour INTEGER;
    has_rock_pop BOOLEAN;
    max_finish_hour INTEGER;
BEGIN
    SELECT gigdatetime INTO gig_start_time
    FROM GIG
    WHERE gigid = NEW.gigid;
    
    -- Find the latest act end time for this gig
    SELECT MAX(ontime + (duration || ' minutes')::INTERVAL) INTO final_act_end_time
    FROM ACT_GIG
    WHERE gigid = NEW.gigid
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime));
    
    -- If this is the final act (or will be after update), check finish time
    IF final_act_end_time IS NULL OR (NEW.ontime + (NEW.duration || ' minutes')::INTERVAL) >= final_act_end_time THEN
        final_act_end_time := NEW.ontime + (NEW.duration || ' minutes')::INTERVAL;
        finish_hour := EXTRACT(HOUR FROM final_act_end_time);
        
        -- Check if gig involves rock or pop (case sensitive)
        SELECT EXISTS(
            SELECT 1
            FROM ACT_GIG ag2
            JOIN ACT a ON ag2.actid = a.actid
            WHERE ag2.gigid = NEW.gigid
              AND (a.genre = 'rock' OR a.genre = 'pop')
        ) INTO has_rock_pop;
        
        IF has_rock_pop THEN
            max_finish_hour := 23;  -- 11pm
        ELSE
            max_finish_hour := 1;   -- 1am (next day)
        END IF;
        
        -- Check finish time based on genre
        IF has_rock_pop THEN
            -- Rock/pop must finish by 11pm (23:00) inclusive
            -- Check if hour is past 23 or if it's 23 with minutes > 0
            IF finish_hour > 23 OR (finish_hour = 23 AND EXTRACT(MINUTE FROM final_act_end_time) > 0) THEN
                RAISE EXCEPTION 'Rock/pop gigs must finish by 11pm (inclusive), but finishes at %', final_act_end_time;
            END IF;
        ELSE
            -- Other gigs must finish by 1am (01:00 next day)
            -- Check if it's the next day
            IF EXTRACT(DAY FROM final_act_end_time) > EXTRACT(DAY FROM gig_start_time) THEN
                -- It's the next day - must be at or before 1am (hour 1, minute 0)
                IF finish_hour > 1 OR (finish_hour = 1 AND EXTRACT(MINUTE FROM final_act_end_time) > 0) THEN
                    RAISE EXCEPTION 'Non-rock/pop gigs must finish by 1am, but finishes at %', final_act_end_time;
                END IF;
            END IF;
            -- If same day, it's fine (can finish any time on the same day)
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_gig_finish_time
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_gig_finish_time();

-- Business Rule 4: Acts only receive one fee per gig (all performances must have same fee)
CREATE OR REPLACE FUNCTION validate_act_fee_per_gig()
RETURNS TRIGGER AS $$
DECLARE
    existing_fee INTEGER;
BEGIN
    -- Check if this act already has a different fee for this gig
    SELECT DISTINCT actgigfee INTO existing_fee
    FROM ACT_GIG
    WHERE actid = NEW.actid
      AND gigid = NEW.gigid
      AND (TG_OP = 'INSERT' OR (actid, gigid, ontime) != (OLD.actid, OLD.gigid, OLD.ontime))
    LIMIT 1;
    
    IF existing_fee IS NOT NULL AND existing_fee != NEW.actgigfee THEN
        RAISE EXCEPTION 'Act can only receive one fee per gig. Existing fee: %, new fee: %', existing_fee, NEW.actgigfee;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_act_fee_per_gig
    BEFORE INSERT OR UPDATE ON ACT_GIG
    FOR EACH ROW
    EXECUTE FUNCTION validate_act_fee_per_gig();

-- Function to validate ticket cost matches GIG_TICKET price
CREATE OR REPLACE FUNCTION validate_ticket_cost()
RETURNS TRIGGER AS $$
DECLARE
    expected_price INTEGER;
    gig_status CHAR(1);
BEGIN
    SELECT price INTO expected_price
    FROM GIG_TICKET
    WHERE gigid = NEW.gigid AND pricetype = NEW.pricetype;

    IF expected_price IS NULL THEN
        RAISE EXCEPTION 'No price defined for gig % and pricetype %', NEW.gigid, NEW.pricetype;
    END IF;

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

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_ticket_cost
    BEFORE INSERT OR UPDATE ON TICKET
    FOR EACH ROW
    EXECUTE FUNCTION validate_ticket_cost();

-- Function to validate venue capacity is not exceeded
CREATE OR REPLACE FUNCTION validate_venue_capacity()
RETURNS TRIGGER AS $$
DECLARE
    venue_capacity INTEGER;
    tickets_sold INTEGER;
BEGIN
    -- Get venue capacity for the gig
    SELECT v.capacity INTO venue_capacity
    FROM VENUE v
    JOIN GIG g ON v.venueid = g.venueid
    WHERE g.gigid = NEW.gigid;
    
    -- Count tickets sold for this gig (excluding the current ticket being inserted/updated)
    SELECT COUNT(*) INTO tickets_sold
    FROM TICKET
    WHERE gigid = NEW.gigid
      AND (TG_OP = 'INSERT' OR ticketid != OLD.ticketid);
    
    -- Check if adding this ticket would exceed capacity
    IF tickets_sold + 1 > venue_capacity THEN
        RAISE EXCEPTION 'Ticket sales (%) would exceed venue capacity (%) for gig %', 
            tickets_sold + 1, venue_capacity, NEW.gigid;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_validate_venue_capacity
    BEFORE INSERT OR UPDATE ON TICKET
    FOR EACH ROW
    EXECUTE FUNCTION validate_venue_capacity();
