package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import model.*;
import service.*;
import dao.*;
import exception.CustomExceptions.*;

/**
 * ReservationDeskTest.java — Automated Test Suite
 * ═══════════════════════════════════════════════════════════
 *
 * Provides comprehensive unit and concurrency stress testing
 * across all major architectural modules:
 *
 * 1. Vehicle Polymorphism & Dynamic Pricing Calculations
 * 2. High-Concurrency Multi-Threaded Seat Collision Test
 * 3. Lock Expiry & Re-entrancy Protection
 * 4. Priority FIFO Waitlist Management & Auto-Promotion
 * 5. JDBC Transaction Integrity & Atomic Rollback
 * 6. File I/O Verification (PrintWriter & BufferedReader)
 *
 * Run directly via:
 *   javac -cp "lib/*;out" -d out src/test/ReservationDeskTest.java
 *   java -cp "out;lib/*" test.ReservationDeskTest
 *
 * @author Group 24 — VIT Bhopal (CSE2006)
 */
public class ReservationDeskTest {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("    RESERVATION DESK - AUTOMATED TEST SUITE & VALIDATION    ");
        System.out.println("    CSE2006 - Programming in Java | VIT Bhopal              ");
        System.out.println("============================================================");
        System.out.println();

        // Ensure database connection
        DBConnection.getInstance();

        runTest("Module 1: Vehicle Polymorphism & Fare Calculation", ReservationDeskTest::testVehiclePolymorphism);
        runTest("Module 2: Dynamic Pricing Engine & Std Dev", ReservationDeskTest::testPricingEngine);
        runTest("Module 3: Granular Seat Locking & State Transitions", ReservationDeskTest::testSeatLocking);
        runTest("Module 4: High-Concurrency 10-Agent Collision Stress Test", ReservationDeskTest::testConcurrencyCollision);
        runTest("Module 5: Priority FIFO Waitlist & Promotion", ReservationDeskTest::testWaitlistLogic);
        runTest("Module 6: JDBC Transaction Rollback on Duplicate Booking", ReservationDeskTest::testDatabaseTransactionRollback);
        runTest("Module 7: File I/O - E-Ticket PrintWriter & BufferedReader", ReservationDeskTest::testFileIO);

        System.out.println();
        System.out.println("============================================================");
        System.out.printf(" TEST SUMMARY: Total = %d | Passed = %d | Failed = %d%n", totalTests, passedTests, failedTests);
        System.out.println("============================================================");

        if (failedTests == 0) {
            System.out.println(">>> ALL TESTS PASSED SUCCESSFULLY! (100% PASS RATE) <<<");
            System.exit(0);
        } else {
            System.err.println(">>> SOME TESTS FAILED! <<<");
            System.exit(1);
        }
    }

    private static void runTest(String testName, Runnable testFunc) {
        totalTests++;
        System.out.printf("[TEST %d] %s ... ", totalTests, testName);
        try {
            testFunc.run();
            passedTests++;
            System.out.println("PASSED [OK]");
        } catch (Throwable t) {
            failedTests++;
            System.out.println("FAILED [FAIL]");
            System.err.println("   Reason: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " [Expected: " + expected + ", Actual: " + actual + "]");
        }
    }

    /* ══════════════════════════════════════════════════════
     * TEST 1: Vehicle Polymorphism
     * ══════════════════════════════════════════════════════ */
    private static void testVehiclePolymorphism() {
        Vehicle flight = new Flight("TEST-FLIGHT", "DEL -> BOM", "08:00 AM", 30, 5000.0);
        Vehicle train  = new Train("TEST-TRAIN", "NDLS -> HWH", "04:30 PM", 30, 1200.0);

        // Polymorphic fare calculation based on remaining seats
        // When all 30 seats remain: ratio = 30/30 = 1.0 -> fare = baseFare
        double fBase = flight.calculateFare(30);
        double tBase = train.calculateFare(30);

        // When 15 seats remain (50% full):
        // Flight (scarcity 1.5): 5000 * (1 + (30/15 - 1) * 1.5) = 5000 * 2.5 = 12500.0
        // Train  (scarcity 1.2): 1200 * (1 + (30/15 - 1) * 1.2) = 1200 * 2.2 = 2640.0
        double fMid = flight.calculateFare(15);
        double tMid = train.calculateFare(15);

        assertEquals(5000.0, fBase, "Flight fare with all seats remaining must equal base fare");
        assertEquals(1200.0, tBase, "Train fare with all seats remaining must equal base fare");
        assertEquals(12500.0, fMid, "Flight fare with 15 seats remaining must equal 12500.0");
        assertEquals(2640.0, tMid, "Train fare with 15 seats remaining must equal 2640.0");
    }

    /* ══════════════════════════════════════════════════════
     * TEST 2: Dynamic Pricing Engine
     * ══════════════════════════════════════════════════════ */
    private static void testPricingEngine() {
        Vehicle flight = new Flight("TEST-F2", "DEL -> BLR", "10:00 AM", 30, 4000.0);

        // Base price with low occupancy
        double fareLow = PricingEngine.calculateDynamicPrice(flight, 2, new double[]{4000.0, 4200.0});
        assertTrue(fareLow >= 4000.0, "Fare should not be lower than base rate");

        // High occupancy (28/30 seats booked = 93% occupancy) -> Surge multiplier applied
        double fareSurge = PricingEngine.calculateDynamicPrice(flight, 28, new double[]{4000.0, 5500.0, 6000.0});
        assertTrue(fareSurge > fareLow, "Surge fare at 93% occupancy should exceed low-occupancy fare");
    }

    /* ══════════════════════════════════════════════════════
     * TEST 3: Granular Seat Locking & State Transitions
     * ══════════════════════════════════════════════════════ */
    private static void testSeatLocking() {
        Vehicle v = new Flight("TEST-V3", "A -> B", "12:00 PM", 30, 3000.0);
        ReservationEngine engine = new ReservationEngine(v, 60000L);

        assertEquals(ReservationEngine.AVAILABLE, engine.getSeatStatus(0), "Initial seat status must be AVAILABLE");

        // Lock seat 0
        try {
            engine.lockSeat(0, "Agent-Alpha");
            assertEquals(ReservationEngine.LOCKED, engine.getSeatStatus(0), "Seat status after lock must be LOCKED");
            assertEquals("Agent-Alpha", engine.getLockOwner(0), "Lock owner must match Agent-Alpha");

            // Confirm seat 0
            engine.confirmSeat(0);
            assertEquals(ReservationEngine.BOOKED, engine.getSeatStatus(0), "Seat status after confirm must be BOOKED");

            // Release seat 0
            engine.releaseSeat(0);
            assertEquals(ReservationEngine.AVAILABLE, engine.getSeatStatus(0), "Seat status after release must be AVAILABLE");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ══════════════════════════════════════════════════════
     * TEST 4: High-Concurrency 10-Agent Collision Stress Test
     * ══════════════════════════════════════════════════════ */
    private static void testConcurrencyCollision() {
        Vehicle v = new Flight("TEST-V4", "DEL -> CCU", "06:00 AM", 30, 3500.0);
        ReservationEngine engine = new ReservationEngine(v, 60000L);
        final int targetSeat = 7; // All threads race for seat index 7

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount); // Synchronize start time

        AtomicInteger successLocks = new AtomicInteger(0);
        AtomicInteger collisionsHandled = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String agentName = "Racer-" + i;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(); // Simultaneous start
                    engine.lockSeat(targetSeat, agentName);
                    successLocks.incrementAndGet();
                    Thread.sleep(50); // Simulate processing
                    engine.confirmSeat(targetSeat);
                } catch (SeatAlreadyBookedException e) {
                    collisionsHandled.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        executor.shutdown();

        assertEquals(1, successLocks.get(), "Strictly one thread must win the lock for the contested seat");
        assertEquals(threadCount - 1, collisionsHandled.get(), "All other concurrent threads must receive SeatAlreadyBookedException");
        assertEquals(ReservationEngine.BOOKED, engine.getSeatStatus(targetSeat), "Final state of contested seat must be BOOKED");
    }

    /* ══════════════════════════════════════════════════════
     * TEST 5: Priority FIFO Waitlist & Promotion
     * ══════════════════════════════════════════════════════ */
    private static void testWaitlistLogic() {
        WaitlistManager wm = new WaitlistManager();
        String vId = "AI-101";

        wm.addToWaitlist(vId, "Regular-Bob", 0);       // Normal priority
        wm.addToWaitlist(vId, "VIP-Alice", 2);          // VIP priority
        wm.addToWaitlist(vId, "Senior-Charlie", 1);    // Senior citizen

        assertTrue(wm.hasWaitlist(vId), "Waitlist must not be empty");
        assertEquals(3, wm.getWaitlistCount(vId), "Waitlist count should be 3");

        // First promotion should be VIP (Alice)
        WaitlistManager.WaitlistEntry first = wm.promoteNext(vId);
        assertEquals("VIP-Alice", first.passenger, "VIP passenger must be promoted first");

        // Second promotion should be Senior (Charlie)
        WaitlistManager.WaitlistEntry second = wm.promoteNext(vId);
        assertEquals("Senior-Charlie", second.passenger, "Senior citizen must be promoted next");

        // Third promotion should be Regular (Bob)
        WaitlistManager.WaitlistEntry third = wm.promoteNext(vId);
        assertEquals("Regular-Bob", third.passenger, "Normal passenger must be promoted last");

        assertTrue(!wm.hasWaitlist(vId), "Waitlist should now be empty");
    }

    /* ══════════════════════════════════════════════════════
     * TEST 6: JDBC Transaction Rollback on Duplicate Booking
     * ══════════════════════════════════════════════════════ */
    private static void testDatabaseTransactionRollback() {
        BookingDAO dao = new BookingDAO();
        String vehicleId = "AI-101"; // Pre-existing vehicle from schema
        int seatNum = 29; // Dedicated test seat

        // Pre-test cleanup: ensure test seat is not held by a previous interrupted run
        try {
            BookingDAO.BookingRecord existing = dao.findBookingBySeat(vehicleId, seatNum);
            if (existing != null) {
                dao.cancelBooking(existing.bookingId);
            }
        } catch (Exception ignored) {}

        // First booking: should succeed and commit
        int bookingId = -1;
        try {
            bookingId = dao.insertBooking(vehicleId, seatNum, "InitialPassenger", 4500.0);
            assertTrue(bookingId > 0, "Initial booking ID must be positive");

            // Duplicate booking on same seat & vehicle: must fail and trigger rollback()
            boolean caughtRollback = false;
            try {
                dao.insertBooking(vehicleId, seatNum, "DuplicateIntruder", 4500.0);
            } catch (BookingFailedException e) {
                caughtRollback = true;
                assertTrue(e.getMessage().toLowerCase().contains("rolled back") 
                        || e.getMessage().toLowerCase().contains("already booked"),
                        "Exception message must confirm transaction rollback");
            }
            assertTrue(caughtRollback, "Duplicate booking must throw BookingFailedException with rollback");
        } catch (BookingFailedException e) {
            throw new RuntimeException("Initial booking unexpectedly failed: " + e.getMessage());
        } finally {
            // Cleanup: cancel test booking
            if (bookingId > 0) {
                try {
                    dao.cancelBooking(bookingId);
                } catch (Exception ignored) {}
            }
        }
    }

    /* ══════════════════════════════════════════════════════
     * TEST 7: File I/O — E-Ticket PrintWriter & BufferedReader
     * ══════════════════════════════════════════════════════ */
    private static void testFileIO() {
        BookingDAO dao = new BookingDAO();
        int testBookingId = 99999;
        String pName = "TestPassenger-FileIO";

        dao.generateETicket(testBookingId, "AI-101", "FLIGHT", "Delhi -> Mumbai",
                "08:00 AM", 12, pName, 4950.0);

        File ticketFile = new File("tickets", "TICKET_" + testBookingId + ".txt");
        assertTrue(ticketFile.exists(), "E-ticket file must exist on disk");
        assertTrue(ticketFile.length() > 0, "E-ticket file must not be empty");

        // Verify contents using BufferedReader
        boolean foundPassenger = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(ticketFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(pName)) {
                    foundPassenger = true;
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read back e-ticket with BufferedReader: " + e.getMessage());
        }

        assertTrue(foundPassenger, "BufferedReader must verify passenger name in the generated e-ticket");

        // Delete test artifact
        ticketFile.delete();
    }
}
