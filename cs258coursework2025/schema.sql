-- Database Schema for GigSystem
-- This schema creates all tables, sequences, and constraints needed for the coursework

-- Drop existing tables if they exist (for clean reset)
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

-- ============================================
-- VENUE Table
-- Stores information about venues
-- ============================================
CREATE TABLE VENUE (
    venueid INTEGER PRIMARY KEY,
    venuename VARCHAR(255) NOT NULL,
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
    FOREIGN KEY (venueid) REFERENCES VENUE(venueid) ON DELETE CASCADE
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
    duration INTEGER NOT NULL CHECK (duration > 0), -- Duration in minutes
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
    FOREIGN KEY (gigid) REFERENCES GIG(gigid) ON DELETE CASCADE
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
