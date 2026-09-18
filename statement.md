# 5.2 statement.md — Project Statement

========================================================================================
                      VELLORE INSTITUTE OF TECHNOLOGY — VIT BHOPAL
                       School of Computing Science and Engineering
                        CSE2006: Programming in Java | Fall 2026
========================================================================================

## Project Title
**Multi-Threaded Flight & Train Ticket Reservation Desk with Fine-Grained Seat Locking, Dynamic Surge Pricing, and ACID Persistence**

---

## 1. Problem Statement

Modern public and private transportation systems (such as national railway reservations and commercial airline ticketing) operate in high-contention, distributed environments where hundreds of booking agents, automated client applications, and passengers concurrently query and reserve a finite pool of physical inventory. When multiple concurrent transactions compete for the same resource without rigorous thread synchronization and transactional isolation, legacy or poorly architected reservation systems suffer from critical operational failures:

1. **Race Conditions and Double Bookings:** 
   When two or more threads attempt to reserve the same seat concurrently, an uncoordinated system reads the seat as available for both transactions. In the absence of mutual exclusion, both bookings succeed, leading to phantom bookings, double-sold physical seats, and operational chaos at departure gates.

2. **Static, Inflexible Pricing Schemes:** 
   Traditional reservation counters employ fixed, static fare structures that fail to respond to rapid fluctuations in customer demand, vehicle load factors, or peak booking velocity. This results in revenue leakage during peak demand periods and inefficient seat allocation.

3. **Uncoordinated and Manual Waitlist Management:** 
   When passenger cancellations occur, seats frequently remain empty or require manual intervention to identify and notify standby passengers. Without an automated, priority-aware queue (prioritizing emergency, VIP, and senior passengers while respecting arrival order), seat reallocation is delayed, error-prone, and unfair.

4. **Database State Inconsistency and Orphan Writes:** 
   Network blips, client disconnections, or mid-transaction application crashes often leave orphaned records in database tables. Without strict ACID boundaries and automated rollback mechanisms, inventory records diverge from financial ledgers.

5. **Terminal and Automated Evaluation Incompatibility:** 
   Many student software projects rely exclusively on graphical user interfaces (GUI), making them impossible to test, grade, or deploy in headless terminal environments, remote SSH sessions, Docker containers, or CI/CD automated grading pipelines. Systems that cannot be run from a pure terminal environment fail automated evaluation criteria and cannot be integrated into scripted infrastructure.

**Core Objective:** 
To architect, implement, and evaluate a resilient, thread-safe, and dual-interface (Desktop GUI and Headless Terminal CLI) Reservation Desk in Java that guarantees collision-free concurrent seat allocation, real-time dynamic pricing, automated priority waitlist promotion, and transactional persistence with automated recovery.

---

## 2. Scope of the Project

The scope of the project encompasses end-to-end design, implementation, and automated validation of a multi-modal transport reservation engine, balancing academic rigor with real-world software engineering practices.

### 2.1 In-Scope Deliverables

* **Multi-Modal Vehicle Modeling:** 
  Polymorphic abstraction representing both commercial flights (air travel) and express passenger trains (rail travel) under a unified `Vehicle` contract with mode-specific pricing rules.
* **Granular Mutex Concurrency Control:** 
  Per-seat lock monitors (`seatLocks[i]`) eliminating global bottlenecks, coupled with inter-thread signaling (`wait()` / `notifyAll()`) and a background daemon watchdog thread that automatically expires abandoned locks after 60 seconds.
* **Statistical Dynamic Pricing Engine:** 
  Real-time fare adjustment combining vehicle base rates, seat class multipliers (Business vs Economy, AC First vs Sleeper), capacity scarcity ratios, and demand volatility calculated via sample standard deviation ($\sigma$).
* **Automated Priority Waitlist:** 
  In-memory and persisted `PriorityQueue` supporting multi-tier passenger categories (VIP, Senior Citizen, Normal) with automatic seat reassignment triggered immediately upon any ticket cancellation.
* **ACID Transactional Persistence:** 
  Embedded SQLite database managed via JDBC, utilizing parameterized `PreparedStatement` queries to prevent SQL injection, and manual transaction controls (`setAutoCommit(false)` and `conn.rollback()`) to prevent partial or duplicate writes.
* **Dual Presentation Layer (GUI & Headless Terminal CLI):** 
  - An interactive Swing GUI (`view.BookingConsole`) with visual seat grids, live color-coded seat states, and dynamic status monitors.
  - A comprehensive Command-Line Interface (`view.BookingCLI`) supporting both an interactive terminal menu and non-interactive script flags (`--status`, `--test`, `--simulate`, `--help`).
  - Automatic environment detection (`Main.java`) ensuring seamless execution in headless terminal environments without requiring GUI dependencies or display servers.
* **Automated Verification Test Suite:** 
  A built-in 7-module test harness (`test.ReservationDeskTest`) that stress-tests polymorphism, dynamic pricing, thread locking, 10-thread simultaneous collision race conditions, waitlist promotion, transaction rollbacks, and file I/O.
* **Character Stream File I/O:** 
  Automated generation of electronic boarding passes (`tickets/TICKET_<id>.txt`) and comprehensive financial audit reports using `PrintWriter`, and external configuration ingestion via `BufferedReader`.

### 2.2 Out-of-Scope (Boundaries & Constraints)

* **External Banking/Payment Gateways:** 
  Live credit card / UPI processing networks are simulated through atomic database commits and fare verification.
* **Distributed Multi-Node Clustering:** 
  The system is engineered as an embedded, self-contained, zero-dependency Java application (single-JVM multi-threading with embedded SQLite) designed for predictable, deterministic academic evaluation without external server setup.
* **Physical Hardware Scanners:** 
  Barcode/NFC physical gate scanners are simulated through e-ticket file generation.

---

## 3. Target Users

The Reservation Desk serves five distinct user personas across operational, administrative, and evaluative roles:

| User Persona | Role & Description | Primary Needs & Use Cases | Preferred Interface |
| :--- | :--- | :--- | :--- |
| **1. Passenger / Commuter** | End-consumer seeking transport bookings across flights and trains. | • View available seats and departure schedules.<br>• View transparent dynamic fares based on cabin class.<br>• Securely book a seat and receive an instant e-ticket.<br>• Join a priority waitlist when vehicles are fully booked. | Desktop GUI / Interactive Terminal CLI |
| **2. Booking Desk Agent** | Counter staff at railway stations, airports, or travel agencies. | • Process customer seat requests rapidly.<br>• Hold seats during checkout with 60-second mutex locks.<br>• Process ticket cancellations and print boarding passes.<br>• View live seat map status (Available, Locked, Booked). | Desktop GUI / Interactive Terminal CLI |
| **3. Desk Supervisor & Auditor** | Administrative and compliance staff overseeing desk operations. | • Inspect all historical transactions across vehicles.<br>• Export full financial audit reports to text files.<br>• Monitor revenue collection, booking volume, and cancellation rates.<br>• Verify ACID database consistency and zero double-bookings. | Terminal CLI (Audit Export) / GUI Records Table |
| **4. Fleet Operations Manager** | Logistics coordinator managing flight and train inventories. | • Monitor real-time vehicle load factors and seat occupancy.<br>• Review surge pricing trends and standard deviation metrics.<br>• Track waitlist demand across routes to optimize fleet allocation. | Terminal CLI Summary (`--status`) / GUI |
| **5. Academic Evaluator & CI/CD Pipeline** | Faculty, automated grading scripts, and CI test runners. | • Execute the application in a headless terminal or SSH session.<br>• Run automated test suites with pass/fail exit codes (`--test`).<br>• Verify thread-safety, race condition prevention, and rollback logic without opening GUI windows. | Headless Terminal CLI (`--status`, `--test`, `--simulate`) |

---

## 4. High-Level Features

The architecture of the Reservation Desk is organized into eight high-level functional pillars:

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                           RESERVATION DESK ARCHITECTURE                           │
├────────────────────────┬────────────────────────┬─────────────────────────────────┤
│ 1. Multi-Modal Fleet   │ 2. Thread-Safe Mutex   │ 3. Statistical Dynamic Pricing  │
│    Inventory Engine    │    Seat Locking &      │    Engine (Scarcity & Volatility│
│    (Flights & Trains)  │    Daemon Watchdog     │    Standard Deviation)          │
├────────────────────────┼────────────────────────┼─────────────────────────────────┤
│ 4. Priority Waitlist   │ 5. ACID Persistence    │ 6. Dual Presentation Layer      │
│    Queue & Automatic   │    & JDBC Transaction  │    (Desktop GUI + Pure          │
│    Seat Promotion      │    Rollback Protection │    Terminal Headless CLI)       │
├────────────────────────┼────────────────────────┼─────────────────────────────────┤
│ 7. Automated Test      │ 8. Character Stream    │                                 │
│    Harness & Stress    │    File I/O (E-Tickets │                                 │
│    Collision Tests     │    & Audit Reports)    │                                 │
└────────────────────────┴────────────────────────┴─────────────────────────────────┘
```

### Feature 1: Multi-Modal Fleet Inventory Management
* **Heterogeneous Transport Support:** Manages both commercial flights (`Flight`) and passenger trains (`Train`) inheriting from an abstract `Vehicle` base class.
* **Unified Fleet Catalog:** Tracks vehicle identifiers, transport modes, origin-destination routes, departure timestamps, total capacity (30 seats), and base fares.
* **Cabin Seat Map Matrix:** Models a 6-row by 5-column seating grid with differentiated cabin tiers (Rows 1–2: Business/First Class; Rows 3–6: Economy/Sleeper Class).
* **Tri-State Seat Lifecycle:** Real-time state machine tracking every physical seat as:
  - `AVAILABLE` (Green): Open for selection and booking.
  - `LOCKED` (Amber/Yellow): Held exclusively by an active booking agent during checkout.
  - `BOOKED` (Red): Confirmed, paid, and permanently registered in the database.

### Feature 2: Thread-Safe Concurrency & Fine-Grained Seat Locking
* **Granular Mutex Monitors:** Instead of locking the entire vehicle or database, each seat is protected by its own monitor (`seatLocks[i]`). Concurrent agents booking different seats execute in parallel with zero contention.
* **Inter-Thread Signaling (`wait()` / `notifyAll()`):** If Agent B attempts to claim a seat currently locked by Agent A, Agent B enters a guarded `wait()` loop. When Agent A finishes or abandons the seat, `notifyAll()` awakens waiting threads to re-evaluate availability safely.
* **Daemon Watchdog Timer:** A background daemon thread sweeps the seat inventory every 2 seconds. If an agent locks a seat and fails to confirm within 60 seconds (abandoned cart or disconnection), the watchdog automatically releases the lock, resets the seat to `AVAILABLE`, and signals waiting threads.

### Feature 3: Dynamic Surge Pricing Engine
* **Load Factor Scarcity Multipliers:** As seat availability decreases, prices scale dynamically according to vehicle-specific scarcity curves (1.5x multiplier for flights, 1.2x multiplier for trains).
* **Statistical Volatility Detection:** The pricing engine tracks recent completed transaction fares and computes their sample mean ($\bar{x}$) and sample standard deviation ($\sigma$):
  $$\sigma = \sqrt{\frac{\sum_{i=1}^{n} (x_i - \bar{x})^2}{n - 1}}$$
  When the coefficient of variation ($\sigma / \bar{x}$) indicates a rapid surge in high-value bookings, an automated surge adjustment is applied to balance supply and demand.

### Feature 4: Priority Waitlist with Event-Driven Promotion
* **Multi-Tier Priority Queue:** When a flight or train is sold out, passengers join a `PriorityQueue` supporting three distinct priority tiers:
  - Priority 2: VIP / Emergency Travel
  - Priority 1: Senior Citizens / Medical
  - Priority 0: General Public
* **Deterministic FIFO Tie-Breaking:** Passengers with identical priority levels are queued strictly in order of their arrival timestamp.
* **Automated Seat Reallocation:** When a confirmed booking is cancelled, the `WaitlistManager` immediately dequeues the highest-priority candidate, generates a new confirmed ticket, updates the database, and assigns the newly freed seat without manual staff intervention.

### Feature 5: ACID Persistence & Transaction Rollback Protection
* **Relational Database Storage:** Persists all vehicles, confirmed bookings, cancellations, and waitlist records in an embedded SQLite database (`reservation_desk.db`).
* **SQL Injection Immunity:** All database queries utilize parameterized `PreparedStatement` calls with strict type binding.
* **Atomic Rollback Guarantee:** Every booking transaction executes under explicit transaction boundaries (`conn.setAutoCommit(false)`). If a duplicate seat collision or constraint violation is detected during execution, `conn.rollback()` is invoked immediately, guaranteeing that no partial, orphaned, or corrupt records persist in the database.

### Feature 6: Dual Presentation Layer (Terminal CLI + Desktop GUI)
* **Headless Terminal CLI (`view.BookingCLI`):** 
  - Complete interactive terminal menu for vehicle inspection, booking, cancellation, waitlist management, audit reporting, and test execution.
  - Non-interactive command-line flags (`--status`, `--test`, `--simulate N`, `--help`) enabling instant execution in headless servers, CI/CD scripts, and automated grading pipelines.
* **Interactive Desktop GUI (`view.BookingConsole`):** 
  - High-contrast dark theme visual seat map with real-time color updates.
  - Live background thread monitor displaying thread state transitions and mutex events.
  - Historical booking record inspector using `JTable` with live status tracking.
* **Universal Entry Point (`Main.java`):** 
  - Dynamically detects the runtime environment using `GraphicsEnvironment.isHeadless()`.
  - Automatically routes to the Terminal CLI when running in a terminal or headless container, ensuring zero crashes and 100% compliance with terminal evaluation requirements.

### Feature 7: Automated Concurrency & Reliability Test Suite
* **7 Comprehensive Test Modules:** 
  1. Vehicle class polymorphism and base fare calculation.
  2. Dynamic pricing engine and standard deviation volatility formula.
  3. Granular seat locking and three-state transitions.
  4. 10-thread simultaneous collision race condition stress test.
  5. Multi-tier priority waitlist ordering and automatic seat promotion.
  6. JDBC transaction rollback upon duplicate seat insertion.
  7. File I/O e-ticket generation and buffered configuration parsing.
* **Terminal-Native Test Execution:** Can be triggered via `./test.sh`, `test.bat`, or `run.bat --test`, outputting detailed module-by-module pass/fail reports with zero GUI dependencies.

### Feature 8: Digital E-Ticket Generation & Financial Audit Reporting
* **E-Ticket File Output:** On every confirmed booking, an electronic boarding pass formatted with border styling, passenger details, seat number, route, departure, and fare is generated to `tickets/TICKET_<booking_id>.txt` using Java `PrintWriter`.
* **Administrative Audit Export:** Generates an end-of-day financial audit report summarizing confirmed tickets, cancelled tickets, active waitlists, and total net revenue collected.
* **Buffered Configuration Management:** System parameters (database file path, lock timeout duration, seat matrix rows/columns, and simulation agent counts) are loaded dynamically from `config/app.config` using `BufferedReader`.

========================================================================================
