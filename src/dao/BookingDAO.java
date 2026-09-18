package dao;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import exception.CustomExceptions.BookingFailedException;

/**
 * BookingDAO.java — Data Access Object for Bookings
 *
 * Handles database operations for bookings, including ACID transaction
 * commits/rollbacks, double-booking prevention, audit logging,
 * and e-ticket generation.
 */
public class BookingDAO {

    /**
     * Data holder representing a booking transaction record.
     */
    public static class BookingRecord {
        public int    bookingId;
        public String vehicleId;
        public int    seatNumber;
        public String passenger;
        public double farePaid;
        public String status;
        public String bookedAt;

        public BookingRecord(int bookingId, String vehicleId, int seatNumber,
                             String passenger, double farePaid, String status,
                             String bookedAt) {
            this.bookingId  = bookingId;
            this.vehicleId  = vehicleId;
            this.seatNumber = seatNumber;
            this.passenger  = passenger;
            this.farePaid   = farePaid;
            this.status     = status;
            this.bookedAt   = bookedAt;
        }

        @Override
        public String toString() {
            return String.format("Booking #%d | Seat %d | %s | Rs.%.2f | %s",
                    bookingId, seatNumber, passenger, farePaid, status);
        }
    }

    /**
     * Inserts a new booking within an ACID transaction.
     * Checks for seat conflict prior to insertion and rolls back if already booked.
     */
    public int insertBooking(String vehicleId, int seatNumber,
                             String passenger, double fare)
            throws BookingFailedException {

        Connection conn = DBConnection.getInstance().getConnection();
        try {
            // Begin transaction
            conn.setAutoCommit(false);
            System.out.println("[DAO] Transaction started (autoCommit = false)");

            // Check for duplicate booking guard
            String checkSql = "SELECT COUNT(*) FROM bookings "
                    + "WHERE vehicle_id = ? AND seat_number = ? AND status = 'CONFIRMED'";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, vehicleId);
                checkStmt.setInt(2, seatNumber);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    conn.rollback();
                    System.out.println("[DAO] Duplicate booking detected, rolling back transaction.");
                    throw new BookingFailedException(
                            "Seat " + seatNumber + " on " + vehicleId
                                    + " is already booked. Transaction rolled back.");
                }
            }

            // Insert confirmed booking record
            String insertSql = "INSERT INTO bookings "
                    + "(vehicle_id, seat_number, passenger, fare_paid) "
                    + "VALUES (?, ?, ?, ?)";
            int bookingId;
            try (PreparedStatement stmt = conn.prepareStatement(
                    insertSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, vehicleId);
                stmt.setInt(2, seatNumber);
                stmt.setString(3, passenger);
                stmt.setDouble(4, fare);
                stmt.executeUpdate();

                ResultSet keys = stmt.getGeneratedKeys();
                bookingId = keys.next() ? keys.getInt(1) : -1;
            }

            // Commit transaction
            conn.commit();
            System.out.println("[DAO] Booking #" + bookingId + " committed successfully.");
            return bookingId;

        } catch (SQLException e) {
            // Roll back on failure
            try {
                conn.rollback();
                System.out.println("[DAO] Transaction rolled back due to error: " + e.getMessage());
            } catch (SQLException ex) {
                System.err.println("[DAO] Rollback failed: " + ex.getMessage());
            }
            throw new BookingFailedException(
                    "Database error, transaction rolled back: " + e.getMessage());
        } finally {
            /* ── Always restore autoCommit ── */
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                System.err.println("[DAO] Failed to restore autoCommit: " + e.getMessage());
            }
        }
    }

    /**
     * Cancels an existing confirmed booking and marks its status as CANCELLED.
     */
    public boolean cancelBooking(int bookingId) throws BookingFailedException {
        String sql = "UPDATE bookings SET status = 'CANCELLED' "
                + "WHERE booking_id = ? AND status = 'CONFIRMED'";
        try (PreparedStatement stmt =
                     DBConnection.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setInt(1, bookingId);  // ► ? placeholder
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("[DAO] Booking #" + bookingId + " CANCELLED.");
                return true;
            } else {
                System.out.println("[DAO] Booking #" + bookingId + " not found or already cancelled.");
                return false;
            }
        } catch (SQLException e) {
            throw new BookingFailedException("Cancel failed: " + e.getMessage());
        }
    }

    /**
     * Retrieves all confirmed bookings for a vehicle.
     */
    public List<BookingRecord> getBookingsForVehicle(String vehicleId) {
        List<BookingRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM bookings WHERE vehicle_id = ? AND status = 'CONFIRMED'";
        try (PreparedStatement stmt =
                     DBConnection.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setString(1, vehicleId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new BookingRecord(
                        rs.getInt("booking_id"),
                        rs.getString("vehicle_id"),
                        rs.getInt("seat_number"),
                        rs.getString("passenger"),
                        rs.getDouble("fare_paid"),
                        rs.getString("status"),
                        rs.getString("booked_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[DAO] Error fetching bookings: " + e.getMessage());
        }
        return list;
    }

    /**
     * Looks up an active booking for a given vehicle and seat.
     */
    public BookingRecord findBookingBySeat(String vehicleId, int seatNumber) {
        String sql = "SELECT * FROM bookings "
                + "WHERE vehicle_id = ? AND seat_number = ? AND status = 'CONFIRMED'";
        try (PreparedStatement stmt =
                     DBConnection.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setString(1, vehicleId);
            stmt.setInt(2, seatNumber);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new BookingRecord(
                        rs.getInt("booking_id"),
                        rs.getString("vehicle_id"),
                        rs.getInt("seat_number"),
                        rs.getString("passenger"),
                        rs.getDouble("fare_paid"),
                        rs.getString("status"),
                        rs.getString("booked_at")
                );
            }
        } catch (SQLException e) {
            System.err.println("[DAO] Error finding booking: " + e.getMessage());
        }
        return null;
    }

    /**
     * Retrieves recent paid fares for dynamic pricing calculations.
     */
    public double[] getRecentFares(String vehicleId, int limit) {
        List<Double> fares = new ArrayList<>();
        String sql = "SELECT fare_paid FROM bookings "
                + "WHERE vehicle_id = ? AND status = 'CONFIRMED' "
                + "ORDER BY booked_at DESC LIMIT ?";
        try (PreparedStatement stmt =
                     DBConnection.getInstance().getConnection().prepareStatement(sql)) {
            stmt.setString(1, vehicleId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                fares.add(rs.getDouble("fare_paid"));
            }
        } catch (SQLException e) {
            System.err.println("[DAO] Error fetching fares: " + e.getMessage());
        }

        double[] result = new double[fares.size()];
        for (int i = 0; i < fares.size(); i++) {
            result[i] = fares.get(i);
        }
        return result;
    }

    /**
     * Generates a plain-text e-ticket and writes it to the tickets/ folder.
     */
    public void generateETicket(int bookingId, String vehicleId,
                                String vehicleType, String route,
                                String departure, int seatNumber,
                                String passenger, double fare) {
        File ticketDir = new File("tickets");
        if (!ticketDir.exists()) {
            ticketDir.mkdirs();
        }

        String fileName = "tickets" + File.separator + "TICKET_" + bookingId + ".txt";

        try (PrintWriter pw = new PrintWriter(new FileWriter(fileName))) {
            pw.println("================================================================");
            pw.println("   MULTI-THREADED RESERVATION DESK — ELECTRONIC TICKET (E-TICKET)");
            pw.println("================================================================");
            pw.println();
            pw.printf("   Booking ID    :  %d%n", bookingId);
            pw.printf("   Vehicle ID    :  %s%n", vehicleId);
            pw.printf("   Vehicle Type  :  %s%n", vehicleType);
            pw.printf("   Route         :  %s%n", route);
            pw.printf("   Departure     :  %s%n", departure);
            pw.printf("   Seat Number   :  %d%n", seatNumber);
            pw.printf("   Passenger     :  %s%n", passenger);
            pw.printf("   Fare Paid     :  Rs. %.2f%n", fare);
            pw.println();
            pw.println("   Status        :  CONFIRMED");
            pw.println();
            pw.println("================================================================");
            pw.println("   * This is a computer-generated e-ticket.                    *");
            pw.println("   * Please keep this document for your records.               *");
            pw.println("================================================================");
            pw.flush();
            System.out.println("[TICKET] E-ticket generated: " + fileName);
        } catch (IOException e) {
            System.err.println("[TICKET] Failed to generate e-ticket: " + e.getMessage());
        }
    }

    /**
     * Returns full booking history for auditing and reporting.
     */
    public List<BookingRecord> getAllBookings() {
        List<BookingRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM bookings ORDER BY booking_id DESC";
        try (Statement stmt = DBConnection.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new BookingRecord(
                        rs.getInt("booking_id"),
                        rs.getString("vehicle_id"),
                        rs.getInt("seat_number"),
                        rs.getString("passenger"),
                        rs.getDouble("fare_paid"),
                        rs.getString("status"),
                        rs.getString("booked_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[DAO] Error fetching all bookings: " + e.getMessage());
        }
        return list;
    }

    /**
     * Generates and writes an audit summary text report.
     */
    public boolean exportAuditReport(File targetFile) {
        List<BookingRecord> all = getAllBookings();
        try (PrintWriter pw = new PrintWriter(new FileWriter(targetFile))) {
            pw.println("================================================================================");
            pw.println("                 RESERVATION DESK — TRANSACTION AUDIT REPORT                    ");
            pw.println("================================================================================");
            pw.printf(" Generated On : %s%n", new java.util.Date());
            pw.printf(" Total Records: %d%n", all.size());
            pw.println("--------------------------------------------------------------------------------");
            pw.printf("%-10s | %-12s | %-6s | %-20s | %-12s | %-10s | %-19s%n",
                    "BOOKING ID", "VEHICLE ID", "SEAT", "PASSENGER", "FARE (INR)", "STATUS", "BOOKED AT");
            pw.println("--------------------------------------------------------------------------------");

            double totalRevenue = 0.0;
            int confirmedCount = 0;
            int cancelledCount = 0;

            for (BookingRecord r : all) {
                pw.printf("%-10d | %-12s | %-6d | %-20s | %12.2f | %-10s | %-19s%n",
                        r.bookingId, r.vehicleId, r.seatNumber, r.passenger, r.farePaid, r.status, r.bookedAt);
                if ("CONFIRMED".equalsIgnoreCase(r.status)) {
                    totalRevenue += r.farePaid;
                    confirmedCount++;
                } else {
                    cancelledCount++;
                }
            }

            pw.println("================================================================================");
            pw.println("                             FINANCIAL SUMMARY                                  ");
            pw.println("================================================================================");
            pw.printf(" Confirmed Tickets : %d%n", confirmedCount);
            pw.printf(" Cancelled Tickets : %d%n", cancelledCount);
            pw.printf(" Total Net Revenue : Rs. %.2f%n", totalRevenue);
            pw.println("================================================================================");
            pw.println(" End of Audit Report.");
            pw.flush();
            System.out.println("[DAO] Audit report exported successfully: " + targetFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("[DAO] Failed to export audit report: " + e.getMessage());
            return false;
        }
    }
}

