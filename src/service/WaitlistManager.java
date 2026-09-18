package service;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import dao.DBConnection;

/**
 * WaitlistManager.java — Automated Priority Waitlist Management
 *
 * Manages waiting queues when all seats on a vehicle are fully booked.
 * Orders entries by priority category (VIP, Senior, Normal) and
 * arrival order (FIFO) using a Java PriorityQueue backed by SQLite.
 */
public class WaitlistManager {

    /* ── Inner Class: WaitlistEntry ── */
    public static class WaitlistEntry implements Comparable<WaitlistEntry> {
        public int    waitId;      // Database primary key
        public String vehicleId;   // Associated vehicle
        public String passenger;   // Passenger name
        public int    priority;    // Higher = earlier promotion (2=VIP, 1=Senior, 0=Normal)
        public long   timestamp;   // Enqueue timestamp for FIFO tiebreaking

        public WaitlistEntry(int waitId, String vehicleId, String passenger,
                             int priority, long timestamp) {
            this.waitId    = waitId;
            this.vehicleId = vehicleId;
            this.passenger = passenger;
            this.priority  = priority;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(WaitlistEntry other) {
            // Higher priority comes first
            if (this.priority != other.priority) {
                return other.priority - this.priority;
            }
            // Same priority: earlier timestamp comes first
            return Long.compare(this.timestamp, other.timestamp);
        }

        @Override
        public String toString() {
            String priorityLabel;
            switch (priority) {
                case 2:  priorityLabel = "VIP";    break;
                case 1:  priorityLabel = "Senior"; break;
                default: priorityLabel = "Normal"; break;
            }
            return passenger + " [" + priorityLabel + "]";
        }
    }

    private final PriorityQueue<WaitlistEntry> waitlist;

    /**
     * Initializes an empty priority waitlist.
     */
    public WaitlistManager() {
        this.waitlist = new PriorityQueue<>();
    }

    /**
     * Enqueues a passenger into the waitlist and records the entry in the DB.
     */
    public void addToWaitlist(String vehicleId, String passenger, int priority) {
        int waitId = -1;

        // Persist to database
        String sql = "INSERT INTO waitlist (vehicle_id, passenger, priority) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = DBConnection.getInstance().getConnection()
                .prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, vehicleId);
            stmt.setString(2, passenger);
            stmt.setInt(3, priority);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) waitId = keys.getInt(1);
        } catch (SQLException e) {
            System.err.println("[WAITLIST] DB insert failed: " + e.getMessage());
        }

        // Add to in-memory PriorityQueue
        WaitlistEntry entry = new WaitlistEntry(
                waitId, vehicleId, passenger, priority, System.currentTimeMillis());
        waitlist.offer(entry);

        System.out.println("[WAITLIST] Added: " + passenger
                + " for " + vehicleId
                + " (Priority: " + priority + ", Queue size: " + waitlist.size() + ")");
    }

    /**
     * Dequeues the highest priority passenger for a vehicle upon seat cancellation.
     */
    public WaitlistEntry promoteNext(String vehicleId) {
        // Find the highest-priority entry for this specific vehicle
        WaitlistEntry best = null;
        Iterator<WaitlistEntry> it = waitlist.iterator();
        while (it.hasNext()) {
            WaitlistEntry entry = it.next();
            if (entry.vehicleId.equals(vehicleId)) {
                if (best == null || entry.compareTo(best) < 0) {
                    best = entry;
                }
            }
        }

        if (best != null) {
            // ── Remove from PriorityQueue ──
            waitlist.remove(best);

            // ── Remove from database ──
            String sql = "DELETE FROM waitlist WHERE wait_id = ?";
            try (PreparedStatement stmt = DBConnection.getInstance().getConnection()
                    .prepareStatement(sql)) {
                stmt.setInt(1, best.waitId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[WAITLIST] DB delete failed: " + e.getMessage());
            }

            System.out.println("[WAITLIST] [PROMOTED]: " + best.passenger
                    + " for " + vehicleId
                    + " (Remaining in queue: " + waitlist.size() + ")");
        } else {
            System.out.println("[WAITLIST] No waitlisted passengers for " + vehicleId);
        }

        return best;
    }

    /* ══════════════════════════════════════════════════════
     * loadFromDatabase() — Rebuild in-memory queue from DB
     *
     * Called on application startup to restore the waitlist
     * state from the previous session.
     * ══════════════════════════════════════════════════════ */
    public void loadFromDatabase() {
        waitlist.clear();
        String sql = "SELECT * FROM waitlist ORDER BY priority DESC, added_at ASC";
        try (Statement stmt = DBConnection.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                WaitlistEntry entry = new WaitlistEntry(
                        rs.getInt("wait_id"),
                        rs.getString("vehicle_id"),
                        rs.getString("passenger"),
                        rs.getInt("priority"),
                        System.currentTimeMillis()
                );
                waitlist.offer(entry);
            }
            System.out.println("[WAITLIST] Loaded " + waitlist.size()
                    + " entries from database.");
        } catch (SQLException e) {
            System.err.println("[WAITLIST] Load failed: " + e.getMessage());
        }
    }

    /**
     * Returns the number of waitlisted passengers for a specific vehicle.
     */
    public int getWaitlistCount(String vehicleId) {
        int count = 0;
        for (WaitlistEntry e : waitlist) {
            if (e.vehicleId.equals(vehicleId)) count++;
        }
        return count;
    }

    /**
     * Checks if there are any waitlisted passengers for a specific vehicle.
     */
    public boolean hasWaitlist(String vehicleId) {
        return getWaitlistCount(vehicleId) > 0;
    }

    /**
     * Returns a sorted list of all waitlist entries for a vehicle
     * (used for display in the GUI dialog).
     */
    public List<WaitlistEntry> getWaitlistForVehicle(String vehicleId) {
        List<WaitlistEntry> list = new ArrayList<>();
        for (WaitlistEntry e : waitlist) {
            if (e.vehicleId.equals(vehicleId)) {
                list.add(e);
            }
        }
        Collections.sort(list);  // ► Uses Comparable.compareTo()
        return list;
    }
}
