package view;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import model.*;
import service.*;
import dao.*;
import exception.CustomExceptions.*;
import test.ReservationDeskTest;

/**
 * Terminal Command Line Interface (CLI) for Reservation Desk.
 * Allows running and evaluating the application entirely in a headless terminal,
 * Docker container, or CI/CD automated grading pipeline without a GUI environment.
 */
public class BookingCLI {

    private final BookingDAO bookingDAO;
    private final WaitlistManager waitlistManager;
    private final List<Vehicle> vehicles;
    private final Map<String, ReservationEngine> engines;
    private final Scanner scanner;

    public BookingCLI() {
        this.bookingDAO = new BookingDAO();
        this.waitlistManager = new WaitlistManager();
        this.engines = new HashMap<>();
        this.scanner = new Scanner(System.in);

        DBConnection.getInstance();
        this.waitlistManager.loadFromDatabase();
        this.vehicles = loadVehiclesFromDB();

        for (Vehicle v : vehicles) {
            getOrCreateEngine(v);
        }
    }

    public static void main(String[] args) {
        BookingCLI cli = new BookingCLI();

        // Support non-interactive CLI flags for automated grading scripts
        if (args.length > 0) {
            String flag = args[0].trim().toLowerCase();
            switch (flag) {
                case "test":
                case "-t":
                case "--test":
                    System.out.println("[CLI] Running automated test suite in terminal mode...");
                    ReservationDeskTest.main(new String[0]);
                    return;

                case "status":
                case "-s":
                case "--status":
                case "--summary":
                    cli.printSystemSummary();
                    return;

                case "simulate":
                case "--simulate":
                    int count = 3;
                    if (args.length > 1) {
                        try { count = Integer.parseInt(args[1]); } catch (Exception ignored) {}
                    }
                    cli.runTerminalSimulation(count);
                    return;

                case "cli":
                case "--cli":
                case "-c":
                    // Explicitly requested interactive CLI menu
                    break;

                case "help":
                case "--help":
                case "-h":
                    System.out.println("Reservation Desk - Usage Options:");
                    System.out.println("  run.bat               (Compile & launch GUI application)");
                    System.out.println("  run.bat --cli         (Compile & launch interactive CLI menu)");
                    System.out.println("  run.bat --test        (Compile & run automated test suite)");
                    System.out.println("  run.bat --status      (Print fleet inventory summary)");
                    System.out.println("  run.bat --simulate N  (Run N concurrent simulated agents)");
                    return;
            }
        }

        // Run interactive CLI menu
        cli.runInteractiveMenu();
    }

    public void runInteractiveMenu() {
        boolean running = true;
        while (running) {
            printBanner();
            System.out.println("  [1] View Vehicles & Real-Time Seat Availability");
            System.out.println("  [2] Book a Seat (Lock + Dynamic Fare + Persist + E-Ticket)");
            System.out.println("  [3] Cancel a Booking (Triggers Waitlist Promotion)");
            System.out.println("  [4] View Priority Waitlist");
            System.out.println("  [5] Add Passenger to Waitlist");
            System.out.println("  [6] View All Bookings (Database Records Table)");
            System.out.println("  [7] Run Multi-Agent Concurrency Simulation (Terminal)");
            System.out.println("  [8] Export Transaction Audit Report (.txt)");
            System.out.println("  [9] Run Automated Concurrency & Unit Test Suite");
            System.out.println("  [0] Exit");
            System.out.println("------------------------------------------------------------");
            System.out.print("Enter choice (0-9): ");

            String input = scanner.nextLine().trim();
            System.out.println();

            switch (input) {
                case "1": displaySeatMap(); break;
                case "2": handleInteractiveBooking(); break;
                case "3": handleInteractiveCancellation(); break;
                case "4": displayWaitlist(); break;
                case "5": handleAddWaitlist(); break;
                case "6": displayBookingsTable(); break;
                case "7": runTerminalSimulation(3); break;
                case "8": handleExportAudit(); break;
                case "9": ReservationDeskTest.main(new String[0]); break;
                case "0":
                    System.out.println("Exiting Reservation Desk. Have a great day!");
                    DBConnection.getInstance().close();
                    running = false;
                    break;
                default:
                    System.out.println("[!] Invalid choice. Please select an option between 0 and 9.");
            }
            if (running) {
                System.out.println();
                System.out.print("Press ENTER to continue...");
                scanner.nextLine();
            }
        }
    }

    private void printBanner() {
        System.out.println("============================================================");
        System.out.println("       RESERVATION DESK - COMMAND LINE INTERFACE (CLI)      ");
        System.out.println("       CSE2006: Programming in Java | Group 24 - VIT Bhopal");
        System.out.println("============================================================");
    }

    public void printSystemSummary() {
        printBanner();
        System.out.println("FLEET INVENTORY & OCCUPANCY STATUS:");
        System.out.printf("%-10s | %-8s | %-24s | %-16s | %-8s | %-8s%n",
                "VEHICLE", "TYPE", "ROUTE", "DEPARTURE", "BOOKED", "AVAILABLE");
        System.out.println("----------------------------------------------------------------------------------");
        for (Vehicle v : vehicles) {
            ReservationEngine eng = getOrCreateEngine(v);
            int booked = eng.getBookedCount();
            int avail = eng.getAvailableCount();
            System.out.printf("%-10s | %-8s | %-24s | %-16s | %-8d | %-8d%n",
                    v.getVehicleId(), v.getVehicleType(), v.getRoute(), v.getDeparture(), booked, avail);
        }
        System.out.println("----------------------------------------------------------------------------------");
    }

    private void displaySeatMap() {
        Vehicle v = selectVehiclePrompt();
        if (v == null) return;
        ReservationEngine eng = getOrCreateEngine(v);

        System.out.println();
        System.out.println("CABIN SEAT MAP FOR " + v.getVehicleId() + " (" + v.getRoute() + "):");
        System.out.println("Rows 1-2: First / Business Class (1.5x Surge) | Rows 3-6: Standard Class");
        System.out.println("Legend: [O] Available   [*] Locked   [X] Booked");
        System.out.println();
        System.out.println("Row |  Col A   Col B  | AISLE |  Col C   Col D   Col E");
        System.out.println("----+-----------------+-------+-----------------------");

        for (int r = 0; r < 6; r++) {
            System.out.printf("R%-2d | ", (r + 1));
            for (int c = 0; c < 5; c++) {
                int sIdx = r * 5 + c;
                int status = eng.getSeatStatus(sIdx);
                String code = String.format("%d%c", (r + 1), (char) ('A' + c));
                String tag;
                switch (status) {
                    case ReservationEngine.BOOKED: tag = "[X]"; break;
                    case ReservationEngine.LOCKED: tag = "[*]"; break;
                    default: tag = "[O]"; break;
                }
                System.out.printf(" %s %-3s", tag, code);
                if (c == 1) {
                    System.out.print(" |  || | ");
                }
            }
            System.out.println();
        }

        System.out.printf("%nStats: Available = %d | Locked = %d | Booked = %d | Total = 30%n",
                eng.getAvailableCount(), eng.getLockedCount(), eng.getBookedCount());
    }

    private void handleInteractiveBooking() {
        Vehicle v = selectVehiclePrompt();
        if (v == null) return;
        ReservationEngine eng = getOrCreateEngine(v);

        System.out.print("Enter seat number to book (1-30): ");
        int seatNum;
        try {
            seatNum = Integer.parseInt(scanner.nextLine().trim());
            if (seatNum < 1 || seatNum > 30) {
                System.out.println("[!] Invalid seat. Must be between 1 and 30.");
                return;
            }
        } catch (Exception e) {
            System.out.println("[!] Please enter a valid numeric seat number.");
            return;
        }

        int seatIdx = seatNum - 1;
        if (eng.getSeatStatus(seatIdx) != ReservationEngine.AVAILABLE) {
            System.out.println("[!] Seat " + seatNum + " is not available right now.");
            return;
        }

        System.out.print("Enter Passenger Full Name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("[!] Passenger name cannot be blank.");
            return;
        }

        try {
            System.out.println("[+] Acquiring mutex lock for Seat " + seatNum + "...");
            eng.lockSeat(seatIdx, "CLI-User");

            double fare = PricingEngine.calculateDynamicPrice(
                    v, eng.getBookedCount(), bookingDAO.getRecentFares(v.getVehicleId(), 10));

            System.out.printf("[+] Dynamic Fare calculated: Rs. %.2f%n", fare);
            System.out.println("[+] Executing transactional database insert...");

            int bookingId = bookingDAO.insertBooking(v.getVehicleId(), seatNum, name, fare);
            eng.confirmSeat(seatIdx);

            bookingDAO.generateETicket(bookingId, v.getVehicleId(), v.getVehicleType(),
                    v.getRoute(), v.getDeparture(), seatNum, name, fare);

            System.out.println("[SUCCESS] Booking confirmed.");
            System.out.printf("    Booking ID : %d%n", bookingId);
            System.out.printf("    Passenger  : %s%n", name);
            System.out.printf("    Seat Number: %d%n", seatNum);
            System.out.printf("    Fare Paid  : Rs. %.2f%n", fare);
            System.out.println("    E-Ticket   : tickets/TICKET_" + bookingId + ".txt");

        } catch (SeatAlreadyBookedException e) {
            eng.releaseSeat(seatIdx);
            System.out.println("[COLLISION] Seat already booked: " + e.getMessage());
        } catch (BookingFailedException e) {
            eng.releaseSeat(seatIdx);
            System.out.println("[ERROR] Transaction error (rolled back): " + e.getMessage());
        } catch (Exception e) {
            eng.releaseSeat(seatIdx);
            System.out.println("[ERROR] Unexpected error: " + e.getMessage());
        }
    }

    private void handleInteractiveCancellation() {
        Vehicle v = selectVehiclePrompt();
        if (v == null) return;
        ReservationEngine eng = getOrCreateEngine(v);

        System.out.print("Enter seat number to cancel (1-30): ");
        int seatNum;
        try {
            seatNum = Integer.parseInt(scanner.nextLine().trim());
        } catch (Exception e) {
            System.out.println("[!] Invalid seat number.");
            return;
        }

        int seatIdx = seatNum - 1;
        if (eng.getSeatStatus(seatIdx) != ReservationEngine.BOOKED) {
            System.out.println("[!] Seat " + seatNum + " is not currently confirmed/booked.");
            return;
        }

        BookingDAO.BookingRecord rec = bookingDAO.findBookingBySeat(v.getVehicleId(), seatNum);
        if (rec == null) {
            System.out.println("[!] No active booking record found in database.");
            return;
        }

        System.out.printf("Found Booking #%d for %s (Seat %d). Confirm cancellation (y/N)? ",
                rec.bookingId, rec.passenger, seatNum);
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!confirm.startsWith("y")) {
            System.out.println("Cancellation aborted.");
            return;
        }

        try {
            bookingDAO.cancelBooking(rec.bookingId);
            eng.releaseSeat(seatIdx);
            System.out.println("[SUCCESS] Booking #" + rec.bookingId + " marked as CANCELLED.");

            // Check waitlist promotion
            WaitlistManager.WaitlistEntry promoted = waitlistManager.promoteNext(v.getVehicleId());
            if (promoted != null) {
                System.out.println("[WAITLIST] Passenger found: " + promoted.passenger + " (Priority " + promoted.priority + ")");
                double fare = PricingEngine.calculateDynamicPrice(
                        v, eng.getBookedCount(), bookingDAO.getRecentFares(v.getVehicleId(), 10));
                int newId = bookingDAO.insertBooking(v.getVehicleId(), seatNum, promoted.passenger, fare);
                eng.confirmSeat(seatIdx);
                bookingDAO.generateETicket(newId, v.getVehicleId(), v.getVehicleType(),
                        v.getRoute(), v.getDeparture(), seatNum, promoted.passenger, fare);
                System.out.println("[PROMOTED] Automatically reallocated Seat " + seatNum + " to waitlisted passenger " + promoted.passenger);
            } else {
                System.out.println("[INFO] No waitlisted passengers. Seat " + seatNum + " is now free.");
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Cancellation failed: " + e.getMessage());
        }
    }

    private void displayWaitlist() {
        Vehicle v = selectVehiclePrompt();
        if (v == null) return;
        List<WaitlistManager.WaitlistEntry> list = waitlistManager.getWaitlistForVehicle(v.getVehicleId());
        if (list.isEmpty()) {
            System.out.println("No passengers currently on the waitlist for " + v.getVehicleId());
            return;
        }
        System.out.println("WAITLIST FOR " + v.getVehicleId() + ":");
        int rank = 1;
        for (WaitlistManager.WaitlistEntry e : list) {
            System.out.printf("  %d. %s%n", rank++, e.toString());
        }
    }

    private void handleAddWaitlist() {
        Vehicle v = selectVehiclePrompt();
        if (v == null) return;
        System.out.print("Enter passenger name: ");
        String name = scanner.nextLine().trim();
        if (name.isEmpty()) return;

        System.out.print("Enter priority level (0 = Normal, 1 = Senior Citizen, 2 = VIP): ");
        int prio = 0;
        try { prio = Integer.parseInt(scanner.nextLine().trim()); } catch (Exception ignored) {}

        waitlistManager.addToWaitlist(v.getVehicleId(), name, prio);
        System.out.println("[SUCCESS] Added " + name + " to waitlist with priority " + prio);
    }

    private void displayBookingsTable() {
        List<BookingDAO.BookingRecord> all = bookingDAO.getAllBookings();
        if (all.isEmpty()) {
            System.out.println("No booking records in database.");
            return;
        }
        System.out.printf("%-6s | %-10s | %-6s | %-20s | %-12s | %-10s | %-19s%n",
                "ID", "VEHICLE", "SEAT", "PASSENGER", "FARE (INR)", "STATUS", "BOOKED AT");
        System.out.println("---------------------------------------------------------------------------------------------");
        for (BookingDAO.BookingRecord r : all) {
            System.out.printf("%-6d | %-10s | %-6d | %-20s | %12.2f | %-10s | %-19s%n",
                    r.bookingId, r.vehicleId, r.seatNumber, r.passenger, r.farePaid, r.status, r.bookedAt);
        }
        System.out.println("---------------------------------------------------------------------------------------------");
        System.out.println("Total records: " + all.size());
    }

    public void runTerminalSimulation(int threadCount) {
        Vehicle v = vehicles.get(0);
        ReservationEngine eng = getOrCreateEngine(v);
        System.out.printf("[SIMULATION] Launching %d concurrent booking threads on %s...%n", threadCount, v.getVehicleId());

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        Random rand = new Random();

        for (int i = 1; i <= threadCount; i++) {
            final int id = i;
            pool.submit(() -> {
                try {
                    String agent = "Worker-" + id;
                    barrier.await();
                    // pick a seat
                    int seat = rand.nextInt(30);
                    System.out.printf("  [%s] Contending for Seat %d...%n", agent, (seat + 1));
                    eng.lockSeat(seat, agent);
                    Thread.sleep(300 + rand.nextInt(500));
                    double fare = PricingEngine.calculateDynamicPrice(v, eng.getBookedCount(), new double[]{4000.0});
                    int bId = bookingDAO.insertBooking(v.getVehicleId(), seat + 1, "SimPass-" + id, fare);
                    eng.confirmSeat(seat);
                    System.out.printf("  [%s] [OK] Successfully booked Seat %d (Ticket #%d, Rs.%.2f)%n",
                            agent, (seat + 1), bId, fare);
                } catch (SeatAlreadyBookedException e) {
                    System.out.printf("  [Worker-%d] Contention handled: Seat already booked.%n", id);
                } catch (Exception e) {
                    System.out.printf("  [Worker-%d] Error: %s%n", id, e.getMessage());
                }
            });
        }
        pool.shutdown();
        try { pool.awaitTermination(6, TimeUnit.SECONDS); } catch (Exception ignored) {}
        System.out.println("[SIMULATION] All worker threads terminated cleanly.");
    }

    private void handleExportAudit() {
        File out = new File("tickets", "AUDIT_REPORT_CLI_" + System.currentTimeMillis() + ".txt");
        if (bookingDAO.exportAuditReport(out)) {
            System.out.println("[OK] Financial audit report successfully generated: " + out.getAbsolutePath());
        } else {
            System.out.println("[ERROR] Failed to generate audit report.");
        }
    }

    private Vehicle selectVehiclePrompt() {
        System.out.println("Available Vehicles:");
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle v = vehicles.get(i);
            System.out.printf("  [%d] %-10s (%s | %s)%n", (i + 1), v.getVehicleId(), v.getVehicleType(), v.getRoute());
        }
        System.out.print("Select vehicle (1-" + vehicles.size() + "): ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (idx >= 0 && idx < vehicles.size()) return vehicles.get(idx);
        } catch (Exception ignored) {}
        System.out.println("[!] Invalid vehicle selection.");
        return null;
    }

    private ReservationEngine getOrCreateEngine(Vehicle vehicle) {
        String id = vehicle.getVehicleId();
        if (!engines.containsKey(id)) {
            ReservationEngine eng = new ReservationEngine(vehicle, 60000L);
            List<BookingDAO.BookingRecord> list = bookingDAO.getBookingsForVehicle(id);
            for (BookingDAO.BookingRecord r : list) {
                eng.markBooked(r.seatNumber - 1);
            }
            engines.put(id, eng);
        }
        return engines.get(id);
    }

    private List<Vehicle> loadVehiclesFromDB() {
        List<Vehicle> list = new ArrayList<>();
        String sql = "SELECT * FROM vehicles ORDER BY vehicle_type, vehicle_id";
        try (Statement stmt = DBConnection.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id        = rs.getString("vehicle_id");
                String type      = rs.getString("vehicle_type");
                String route     = rs.getString("route");
                String departure = rs.getString("departure");
                int    seats     = rs.getInt("total_seats");
                double fare      = rs.getDouble("base_fare");

                Vehicle v = "FLIGHT".equals(type)
                        ? new Flight(id, route, departure, seats, fare)
                        : new Train(id, route, departure, seats, fare);
                list.add(v);
            }
        } catch (SQLException e) {
            System.err.println("[CLI] Error loading vehicles: " + e.getMessage());
        }
        return list;
    }
}
