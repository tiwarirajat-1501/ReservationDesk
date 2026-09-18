package service;

import java.util.Vector;

import model.Vehicle;
import exception.CustomExceptions.SeatAlreadyBookedException;
import exception.CustomExceptions.BookingFailedException;

/**
 * ReservationEngine.java — Multithreaded Seat-Locking Engine
 *
 * Core concurrency controller for seat reservations. Manages fine-grained
 * per-seat mutual exclusion locks, temporary reservation holds with
 * background TTL expiry, and condition signaling (wait/notifyAll) for
 * contending booking agents.
 */
public class ReservationEngine {

    /* ── Seat State Constants ─── */
    public static final int AVAILABLE = 0;  // Open for booking
    public static final int LOCKED    = 1;  // Held temporarily by an agent
    public static final int BOOKED    = 2;  // Confirmed and committed to DB

    /* ── The vehicle this engine manages ─── */
    private final Vehicle vehicle;

    // Synchronized vector storing current seat allocation state
    private final Vector<Integer> seatStatus;

    /* ── Per-seat synchronisation infrastructure ─── */
    private final Object[] seatLocks;       // Monitor objects for synchronized blocks
    private final long[]   lockTimestamps;  // When each seat was locked (millis)
    private final String[] lockOwners;      // Which agent holds the lock

    /** Lock timeout in milliseconds (default: 60 seconds) */
    private long lockTimeoutMs = 60_000;

    /* ══════════════════════════════════════════════════════
     * CONSTRUCTOR
     *
     * Initialises the seat matrix with all seats AVAILABLE.
     * Creates per-seat lock objects for fine-grained synchronisation.
     * Starts the lock-expiry daemon thread.
     * ══════════════════════════════════════════════════════ */
    public ReservationEngine(Vehicle vehicle) {
        this.vehicle = vehicle;
        int totalSeats = vehicle.getTotalSeats();

        // ► Initialise the Vector with all seats AVAILABLE
        this.seatStatus = new Vector<>(totalSeats);
        this.seatLocks      = new Object[totalSeats];
        this.lockTimestamps = new long[totalSeats];
        this.lockOwners     = new String[totalSeats];

        for (int i = 0; i < totalSeats; i++) {
            seatStatus.add(AVAILABLE);
            seatLocks[i]      = new Object();  // Each seat gets its own monitor
            lockTimestamps[i] = 0;
            lockOwners[i]     = "";
        }

        // Start background daemon to expire stale locks
        startLockExpiryDaemon();
    }

    /**
     * Overloaded constructor that accepts a custom lock timeout.
     */
    public ReservationEngine(Vehicle vehicle, long lockTimeoutMs) {
        this(vehicle);
        this.lockTimeoutMs = lockTimeoutMs;
    }

    /**
     * Acquires a temporary lock on a seat.
     *
     * If the seat is held by another agent, the thread waits on the seat lock
     * monitor until released or confirmed.
     *
     * @param seatIndex Seat index (0-based).
     * @param agentName Identifier of the booking agent thread.
     */
    public boolean lockSeat(int seatIndex, String agentName)
            throws SeatAlreadyBookedException, InterruptedException {

        synchronized (seatLocks[seatIndex]) {
            // Wait in a loop to protect against spurious wakeups
            while (seatStatus.get(seatIndex) == LOCKED
                    && !lockOwners[seatIndex].equals(agentName)) {
                System.out.println("[" + agentName + "] Seat " + (seatIndex + 1)
                        + " is LOCKED by " + lockOwners[seatIndex]
                        + ". Calling wait()...");

                seatLocks[seatIndex].wait();

                System.out.println("[" + agentName + "] Woke up from wait() "
                        + "for seat " + (seatIndex + 1));
            }

            // Verify status after acquiring lock
            if (seatStatus.get(seatIndex) == BOOKED) {
                throw new SeatAlreadyBookedException(
                        "Seat " + (seatIndex + 1)
                                + " was BOOKED by another agent while waiting.");
            }

            // Lock the seat
            seatStatus.set(seatIndex, LOCKED);
            lockTimestamps[seatIndex] = System.currentTimeMillis();
            lockOwners[seatIndex]     = agentName;

            System.out.println("[" + agentName + "] LOCKED seat "
                    + (seatIndex + 1) + " (timeout: "
                    + (lockTimeoutMs / 1000) + "s)");
            return true;
        }
    }

    /**
     * Transitions a seat from LOCKED to BOOKED and wakes awaiting threads.
     */
    public void confirmSeat(int seatIndex) throws BookingFailedException {
        synchronized (seatLocks[seatIndex]) {
            if (seatStatus.get(seatIndex) != LOCKED) {
                throw new BookingFailedException(
                        "Seat " + (seatIndex + 1)
                                + " is not LOCKED. Cannot confirm.");
            }

            seatStatus.set(seatIndex, BOOKED);
            lockTimestamps[seatIndex] = 0;
            lockOwners[seatIndex]     = "";

            seatLocks[seatIndex].notifyAll();

            System.out.println("[ENGINE] Seat " + (seatIndex + 1)
                    + " CONFIRMED -> BOOKED. notifyAll() called.");
        }
    }

    /**
     * Releases a locked or booked seat back to AVAILABLE and notifies waiting threads.
     */
    public void releaseSeat(int seatIndex) {
        synchronized (seatLocks[seatIndex]) {
            int prevStatus = seatStatus.get(seatIndex);
            if (prevStatus == LOCKED || prevStatus == BOOKED) {
                seatStatus.set(seatIndex, AVAILABLE);
                lockTimestamps[seatIndex] = 0;
                lockOwners[seatIndex]     = "";

                seatLocks[seatIndex].notifyAll();

                System.out.println("[ENGINE] Seat " + (seatIndex + 1)
                        + " RELEASED -> AVAILABLE. notifyAll() called."
                        + " (was: " + statusName(prevStatus) + ")");
            }
        }
    }

    /**
     * Mark a seat as BOOKED directly on application initialization.
     */
    public void markBooked(int seatIndex) {
        seatStatus.set(seatIndex, BOOKED);
    }

    /**
     * Background daemon thread to release locks that exceeded their TTL.
     */
    private void startLockExpiryDaemon() {
        Thread daemon = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                for (int i = 0; i < seatStatus.size(); i++) {
                    synchronized (seatLocks[i]) {
                        if (seatStatus.get(i) == LOCKED
                                && lockTimestamps[i] > 0
                                && (now - lockTimestamps[i]) > lockTimeoutMs) {

                            String owner = lockOwners[i];
                            seatStatus.set(i, AVAILABLE);
                            lockTimestamps[i] = 0;
                            lockOwners[i]     = "";

                            seatLocks[i].notifyAll();

                            System.out.println("[DAEMON] Lock EXPIRED for seat "
                                    + (i + 1) + " (was held by " + owner + ")");
                        }
                    }
                }
                try {
                    Thread.sleep(2000);  // Check every 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "LockExpiryDaemon-" + vehicle.getVehicleId());

        daemon.setDaemon(true);
        daemon.start();
        System.out.println("[ENGINE] Lock expiry daemon started for "
                + vehicle.getVehicleId());
    }

    /**
     * Runnable agent representing concurrent booking attempts.
     */
    public static class BookingAgent implements Runnable {

        private final ReservationEngine engine;
        private final String agentName;
        private final int    targetSeat;     // 0-indexed
        private final String passengerName;

        /**
         * @param engine         The ReservationEngine to book on.
         * @param agentName      Human-readable name (e.g., "Agent-1").
         * @param targetSeat     The seat index to attempt booking (0-indexed).
         * @param passengerName  Name for the ticket.
         */
        public BookingAgent(ReservationEngine engine, String agentName,
                            int targetSeat, String passengerName) {
            this.engine        = engine;
            this.agentName     = agentName;
            this.targetSeat    = targetSeat;
            this.passengerName = passengerName;
        }

        /* ── Runnable.run() — executed on a separate Thread ── */
        @Override
        public void run() {
            try {
                System.out.println("[" + agentName + "] Starting booking attempt "
                        + "for seat " + (targetSeat + 1) + "...");

                // Step 1: Lock the seat
                engine.lockSeat(targetSeat, agentName);

                // Step 2: Simulate processing delay (2–4 seconds)
                long delay = 2000 + (long) (Math.random() * 2000);
                System.out.println("[" + agentName + "] Processing... ("
                        + delay + "ms)");
                Thread.sleep(delay);

                // Step 3: Confirm the seat
                engine.confirmSeat(targetSeat);

                System.out.println("[" + agentName + "] ✓ Successfully booked seat "
                        + (targetSeat + 1) + " for " + passengerName);

            } catch (SeatAlreadyBookedException e) {
                System.out.println("[" + agentName + "] ✗ Failed: " + e.getMessage());
            } catch (BookingFailedException e) {
                System.out.println("[" + agentName + "] ✗ Confirm failed: " + e.getMessage());
            } catch (InterruptedException e) {
                System.out.println("[" + agentName + "] ✗ Interrupted.");
                Thread.currentThread().interrupt();
            }
        }
    }

    /* ── Getters ─── */
    public int     getSeatStatus(int i) { return seatStatus.get(i);    }
    public Vehicle getVehicle()         { return vehicle;              }
    public int     getTotalSeats()      { return vehicle.getTotalSeats(); }
    public String  getLockOwner(int i)  { return lockOwners[i];        }

    public int getBookedCount() {
        int count = 0;
        for (int s : seatStatus) {
            if (s == BOOKED) count++;
        }
        return count;
    }

    public int getAvailableCount() {
        int count = 0;
        for (int s : seatStatus) {
            if (s == AVAILABLE) count++;
        }
        return count;
    }

    public int getLockedCount() {
        int count = 0;
        for (int s : seatStatus) {
            if (s == LOCKED) count++;
        }
        return count;
    }

    /** Helper: convert status int to name for logging. */
    private String statusName(int status) {
        switch (status) {
            case AVAILABLE: return "AVAILABLE";
            case LOCKED:    return "LOCKED";
            case BOOKED:    return "BOOKED";
            default:        return "UNKNOWN";
        }
    }
}
