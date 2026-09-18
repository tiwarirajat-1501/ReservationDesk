# Multi-Threaded Flight & Train Ticket Reservation Desk

A concurrent desktop and command-line reservation system built in Java. The project simulates a transport ticketing desk handling multiple booking instances concurrently, using fine-grained locks, inter-thread signaling (`wait()` / `notifyAll()`), dynamic fare calculations, automated priority waitlists, transactional database persistence (SQLite via JDBC), and formatted ticket generation.

---

## Quick Start (Terminal & CLI)

The project can be run completely from the command line without requiring a graphical display, making it suitable for headless environments, remote SSH sessions, and automated evaluation scripts.

### How to Run

The application includes an all-in-one runner script (`run.bat`) that automatically compiles the project and launches it directly.

#### 1. Graphical Interface (GUI Mode)
Double-click `run.bat` or run:
```cmd
run.bat
```

#### 2. Terminal (CLI Mode — Headless Safe)
Run the application directly in your command line:
- **Interactive Menu:**
  ```cmd
  run.bat --cli
  ```
- **Automated Validation & Unit Tests:**
  ```cmd
  run.bat --test
  ```
- **Check Fleet Inventory & Status:**
  ```cmd
  run.bat --status
  ```
- **Run Multi-Agent Concurrency Simulation:**
  ```cmd
  run.bat --simulate 5
  ```

---

## Core Architecture & Features

### 1. Concurrency Control & Race Condition Prevention
- **Per-Seat Lock Monitors:** Rather than locking the entire vehicle or database, each seat has an independent lock object (`seatLocks[i]`). Multiple agents can book different seats simultaneously without blocking each other.
- **Inter-Thread Signaling (`wait()` and `notifyAll()`):** If Agent A holds a 60-second checkout lock on Seat 4, and Agent B attempts to book Seat 4, Agent B enters a `wait()` state inside a while loop. When Agent A confirms or cancels, `notifyAll()` wakes Agent B.
- **Lock Expiry Watchdog:** A background daemon thread checks seat timestamps every 2 seconds. If an agent locks a seat and disconnects or abandons the transaction for more than 60 seconds, the lock is automatically cleared, seat state is reverted to `AVAILABLE`, and waiting agents are notified.

### 2. Dynamic Surge Pricing
- **Scarcity Model:** As remaining seats drop, ticket prices scale dynamically according to vehicle type multipliers (1.5x for air travel, 1.2x for rail travel).
- **Surge Detection:** The pricing engine tracks recent completed transaction fares and computes their sample mean and standard deviation:
  $$\sigma = \sqrt{\frac{\sum (x_i - \bar{x})^2}{n - 1}}$$
  When the coefficient of variation ($\sigma / \bar{x}$) rises during heavy booking bursts, a surge multiplier is applied automatically.

### 3. Priority Waitlist
- When all seats on a vehicle are filled, customers can join a priority waitlist.
- Managed via `java.util.PriorityQueue` using custom priority tiers (`VIP` > `Senior` > `Normal`), with arrival timestamp tie-breaking (FIFO).
- When any booking is cancelled, the waitlist manager dequeues the top candidate and assigns the newly freed seat automatically.

### 4. Database Persistence & Rollbacks
- Uses an embedded SQLite database (`reservation_desk.db`) via JDBC.
- All write operations execute through parameterised `PreparedStatement` queries to prevent SQL injection.
- The booking transaction begins with `conn.setAutoCommit(false)`. If a duplicate seat booking is detected during insertion, the transaction executes `conn.rollback()`, ensuring no partial or corrupted rows are saved.

### 5. File I/O & Audit Reports
- **E-Tickets:** On confirmation, a formatted ticket text file is written to `tickets/TICKET_<booking_id>.txt` via `PrintWriter`.
- **Audit Export:** An administrative audit report detailing total confirmations, cancellations, and net revenue can be exported to text format.
- **Configuration Reader:** Application settings (`db.path`, `lock.timeout.seconds`, `grid.rows`, `grid.cols`, `sim.agents`) are read line-by-line using `BufferedReader` from `config/app.config`.

---

## Project Structure

```
ReservationDesk/
├── src/
│   ├── exception/
│   │   └── CustomExceptions.java      # Domain exceptions (SeatAlreadyBooked, BookingFailed, InvalidVehicle)
│   ├── model/
│   │   ├── Vehicle.java               # Abstract base class defining vehicle fields & calculateFare()
│   │   ├── Flight.java                # Concrete class with airline scarcity multiplier
│   │   └── Train.java                 # Concrete class with rail scarcity multiplier
│   ├── dao/
│   │   ├── DBConnection.java          # Thread-safe Singleton managing SQLite connection & config loading
│   │   └── BookingDAO.java            # JDBC CRUD operations, transaction rollback, e-ticket output
│   ├── service/
│   │   ├── ReservationEngine.java     # Multi-threaded seat locking, daemon expiry, wait/notifyAll
│   │   ├── PricingEngine.java         # Scarcity formula & sample standard deviation surge pricing
│   │   └── WaitlistManager.java       # PriorityQueue waitlist with automatic cancellation promotion
│   ├── view/
│   │   ├── BookingConsole.java        # Desktop Swing GUI application
│   │   └── BookingCLI.java            # Terminal Command-Line Interface (interactive + automated flags)
│   └── test/
│       └── ReservationDeskTest.java   # 7-module automated unit & concurrency test suite
├── sql/
│   └── schema.sql                     # SQLite table definitions and seed inventory
├── config/
│   └── app.config                     # Application parameters (timeout, dimensions, simulation settings)
├── lib/
│   └── sqlite-jdbc-3.42.0.0.jar       # Bundled SQLite JDBC driver
├── tickets/                           # Output folder for generated e-tickets and audit text files
├── run.bat                            # All-in-one launcher (auto-compiles & launches GUI or CLI)
├── design diagrams.md                 # System architecture & UML design diagrams
└── README.md                          # Main project guide
```

---

## Environment & Dependencies

- **Java Version:** JDK 11 or higher (tested on JDK 17, JDK 21).
- **Build System:** No external package manager (Maven/Gradle) is required. The SQLite JDBC library is included in `lib/`.
- **Database:** SQLite is embedded and creates `reservation_desk.db` automatically in the project root on first execution.

---

## Verification & Test Scenarios

### Automated Concurrency Test
Run `run.bat --test` to execute the stress test:
- Spawns 10 concurrent `Runnable` threads using a `CyclicBarrier` to launch simultaneous booking requests targeting the same seat index.
- Verifies that exactly one thread succeeds in confirming the seat, while other threads enter waiting states and receive `SeatAlreadyBookedException`.
- Asserts that no duplicate entries exist in the database table for that seat.

### CLI Menu Options
When running in CLI mode (`run.bat --cli`), the interactive terminal menu provides:
1. View Fleet Inventory & Occupancy Status
2. View Vehicle Seat Map (visual ASCII representation of Available, Locked, Booked seats)
3. Book a Seat (with passenger name and priority tier)
4. Cancel a Confirmed Booking (triggers automatic waitlist promotion)
5. View Vehicle Priority Waitlist
6. View Recent Bookings Table
7. Run Concurrent Multi-Agent Simulation
8. Export Financial Audit Report to `tickets/`
9. Run Automated Test Suite
0. Exit
