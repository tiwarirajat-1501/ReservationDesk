-- ═══════════════════════════════════════════════════════════
-- schema.sql — Database Schema for Reservation Desk
-- ═══════════════════════════════════════════════════════════
-- Database Engine : SQLite (via JDBC — org.sqlite.JDBC)
-- Read by         : DBConnection.java (using BufferedReader)
--
-- SYLLABUS CONSTRAINT: JDBC Database
-- ► These tables are queried using PreparedStatement in BookingDAO.
-- ► Transaction integrity is enforced via setAutoCommit(false)
--   and Connection.rollback() on failure.
-- ═══════════════════════════════════════════════════════════

-- ─── Table 1: vehicles ───────────────────────────────────
-- Stores metadata for each bookable flight or train.
CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id   TEXT    PRIMARY KEY,
    vehicle_type TEXT    NOT NULL CHECK(vehicle_type IN ('FLIGHT','TRAIN')),
    route        TEXT    NOT NULL,
    departure    TEXT    NOT NULL,
    total_seats  INTEGER NOT NULL,
    base_fare    REAL    NOT NULL
);

-- ─── Table 2: bookings ──────────────────────────────────
-- Every confirmed or cancelled ticket is stored here.
-- booking_id auto-increments for unique ticket numbers.
CREATE TABLE IF NOT EXISTS bookings (
    booking_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    vehicle_id    TEXT    NOT NULL,
    seat_number   INTEGER NOT NULL,
    passenger     TEXT    NOT NULL,
    fare_paid     REAL    NOT NULL,
    status        TEXT    NOT NULL DEFAULT 'CONFIRMED'
                         CHECK(status IN ('CONFIRMED','CANCELLED')),
    booked_at     TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id)
);

-- ─── Table 3: waitlist ───────────────────────────────────
-- Passengers who couldn't get a seat are queued here.
-- 'priority' allows VIP or senior-citizen escalation.
CREATE TABLE IF NOT EXISTS waitlist (
    wait_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    vehicle_id    TEXT    NOT NULL,
    passenger     TEXT    NOT NULL,
    priority      INTEGER NOT NULL DEFAULT 0,
    added_at      TEXT    NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id)
);

-- ═══════════════════════════════════════════════════════════
-- SEED DATA — Sample vehicles (2 Flights + 2 Trains)
-- ═══════════════════════════════════════════════════════════
INSERT OR IGNORE INTO vehicles VALUES
    ('AI-101',  'FLIGHT', 'Delhi -> Mumbai',       '2026-09-15 06:00', 30, 4500.00);
INSERT OR IGNORE INTO vehicles VALUES
    ('AI-202',  'FLIGHT', 'Bangalore -> Chennai',  '2026-09-15 08:30', 30, 3200.00);
INSERT OR IGNORE INTO vehicles VALUES
    ('RAJ-001', 'TRAIN',  'Bhopal -> Delhi',       '2026-09-15 22:00', 30,  850.00);
INSERT OR IGNORE INTO vehicles VALUES
    ('SHA-002', 'TRAIN',  'Mumbai -> Pune',         '2026-09-15 14:15', 30,  450.00);
