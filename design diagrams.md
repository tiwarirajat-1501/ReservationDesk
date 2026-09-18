# Design Diagrams

## 1. Use Case Diagram

```mermaid
flowchart TD
    subgraph Users ["Actors"]
        Agent["Passenger / Booking Agent"]
        Admin["Desk Supervisor / Auditor"]
        SystemTimer["System Clock Daemon"]
    end

    subgraph SystemBoundary ["Reservation Desk System"]
        UC1["Select Vehicle & View Seat Matrix"]
        UC2["Lock Selected Seat"]
        UC3["Calculate Dynamic Surge Price"]
        UC4["Confirm & Persist Booking"]
        UC5["Generate Boarding Pass (PrintWriter)"]
        UC6["Cancel Booking"]
        UC7["Auto-Promote Waitlisted Passenger"]
        UC8["View All Records Table (JTable)"]
        UC9["Export Financial Audit Report"]
        UC10["Reclaim Expired Locks (60s Watchdog)"]
        UC11["Simulate Concurrent Booking Agents"]
    end

    Agent --> UC1
    Agent --> UC2
    UC2 --> UC3
    UC3 --> UC4
    UC4 --> UC5
    Agent --> UC6
    UC6 --> UC7

    Admin --> UC8
    Admin --> UC9
    Admin --> UC11

    SystemTimer --> UC10
```

---

## 2. Process Flow / Workflow Diagram

```mermaid
flowchart TD
    Start([User Selects Seat]) --> CheckStatus{Is Seat Available?}
    
    CheckStatus -- No: Booked --> OptionCancel{User Wants to Cancel?}
    OptionCancel -- Yes --> CancelBooking[Cancel in DB & Release Lock]
    CancelBooking --> CheckWaitlist{Waitlist Empty?}
    CheckWaitlist -- No --> Promote[Auto-Book Top Waitlisted Passenger]
    CheckWaitlist -- Yes --> SeatFree[Mark Seat Green / Available]
    Promote --> RefreshUI[Refresh Grid & Update Status]
    SeatFree --> RefreshUI
    OptionCancel -- No --> End1([Display Info Only])

    CheckStatus -- No: Locked --> WaitLock[Thread Calls wait - Blocks until notified]
    WaitLock --> Recheck[Woken by notifyAll - Re-evaluate Availability]

    CheckStatus -- Yes --> AcquireLock[Enter synchronized block & Lock Seat]
    AcquireLock --> CalcPrice[PricingEngine Calculates Dynamic Fare]
    CalcPrice --> InputPassenger[Agent Enters Passenger Name & Clicks Book]
    InputPassenger --> BeginTx[DAO Starts Transaction: setAutoCommit false]
    BeginTx --> CheckDup{Duplicate Exists in DB?}
    CheckDup -- Yes --> Rollback[conn.rollback & throw BookingFailedException]
    Rollback --> ReleaseLockErr[Release Lock & Notify Waiting Threads]
    
    CheckDup -- No --> InsertBooking[INSERT into bookings table]
    InsertBooking --> CommitTx[conn.commit & Mark Seat BOOKED Red]
    CommitTx --> PrintTicket[PrintWriter Generates E-Ticket .txt]
    PrintTicket --> NotifyAll[Call notifyAll to wake waiting threads]
    NotifyAll --> RefreshUI
    RefreshUI --> Success([Booking Confirmed])
```

---

## 3. Sequence Diagram (Concurrent Booking & Wait/Notify Flow)

```mermaid
sequenceDiagram
    autonumber
    actor AgentA as Agent Thread A
    actor AgentB as Agent Thread B
    participant Engine as ReservationEngine
    participant Pricing as PricingEngine
    participant DAO as BookingDAO
    participant DB as SQLite DB

    AgentA->>Engine: lockSeat(Seat 5, "AgentA")
    activate Engine
    Engine-->>AgentA: Lock Acquired (Seat 5 -> LOCKED)
    deactivate Engine

    par Agent B Attempts Same Seat
        AgentB->>Engine: lockSeat(Seat 5, "AgentB")
        activate Engine
        Note over Engine,AgentB: Seat is LOCKED! Agent B calls seatLocks[5].wait()
        Engine-->>AgentB: Enters WAITING state
        deactivate Engine
    and Agent A Completes Checkout
        AgentA->>Pricing: calculateDynamicPrice(vehicle, bookedCount, fares)
        Pricing-->>AgentA: Return Fare: Rs. 5175.00
        AgentA->>DAO: insertBooking(vehicle, 5, "Passenger A", fare)
        activate DAO
        DAO->>DB: setAutoCommit(false)
        DAO->>DB: SELECT COUNT(*) ... (Check Duplicate)
        DB-->>DAO: 0 duplicates
        DAO->>DB: INSERT INTO bookings ...
        DAO->>DB: commit()
        DAO-->>AgentA: Return bookingId = 101
        deactivate DAO
        AgentA->>Engine: confirmSeat(Seat 5)
        activate Engine
        Engine->>Engine: status = BOOKED
        Engine->>Engine: seatLocks[5].notifyAll()
        deactivate Engine
    end

    Note over AgentB: Agent B wakes up from wait()!
    Engine-->>AgentB: Throws SeatAlreadyBookedException
    AgentB->>AgentB: Handles Exception & Redirects to Available Seat
```

---

## 4. Class / Component Diagram

```mermaid
classDiagram
    class Vehicle {
        <<abstract>>
        #String vehicleId
        #String vehicleType
        #String route
        #String departure
        #int totalSeats
        #double baseFare
        +calculateFare(int remainingSeats)* double
        +getVehicleId() String
        +getBaseFare() double
    }

    class Flight {
        -double SCARCITY_MULTIPLIER = 1.5
        +calculateFare(int remainingSeats) double
    }

    class Train {
        -double SCARCITY_MULTIPLIER = 1.2
        +calculateFare(int remainingSeats) double
    }

    class ReservationEngine {
        -Vehicle vehicle
        -Vector~Integer~ seatStatus
        -Object[] seatLocks
        -String[] lockOwners
        -long[] lockTimes
        -long lockTimeoutMs
        +lockSeat(int seatIndex, String agent) void
        +confirmSeat(int seatIndex) void
        +releaseSeat(int seatIndex) void
        +startExpiryDaemon() void
    }

    class PricingEngine {
        +calculateDynamicPrice(Vehicle, int bookedCount, double[] recentFares)$ double
        -calculateStdDev(double[] fares)$ double
    }

    class WaitlistManager {
        -PriorityQueue~WaitlistEntry~ waitlist
        +addToWaitlist(String vehicleId, String passenger, int priority) void
        +promoteNext(String vehicleId) WaitlistEntry
        +hasWaitlist(String vehicleId) boolean
    }

    class BookingDAO {
        +insertBooking(String vehicleId, int seat, String name, double fare) int
        +cancelBooking(int bookingId) boolean
        +getAllBookings() List~BookingRecord~
        +generateETicket(...) void
        +exportAuditReport(File file) boolean
    }

    class DBConnection {
        -static DBConnection instance
        -Connection conn
        +getInstance()$ DBConnection
        +getConnection() Connection
    }

    class BookingConsole {
        -Map~String, ReservationEngine~ engines
        -JButton[] seatButtons
        -JComboBox~Vehicle~ vehicleSelector
        +main(String[] args)$ void
        -onBookSeat() void
        -onCancelSeat() void
        -showBookingHistoryDialog() void
        -simulateAgents(int count) void
    }

    Vehicle <|-- Flight : Extends
    Vehicle <|-- Train : Extends
    ReservationEngine o-- Vehicle : Manages
    BookingConsole --> ReservationEngine : Delegates
    BookingConsole --> PricingEngine : Queries
    BookingConsole --> WaitlistManager : Uses
    BookingConsole --> BookingDAO : Persists Through
    BookingDAO --> DBConnection : Obtains Connection
```

---

## 5. Database Storage Design (ER Diagram)

```mermaid
erDiagram
    VEHICLES ||--o{ BOOKINGS : "has"
    VEHICLES ||--o{ WAITLIST : "queues"

    VEHICLES {
        TEXT vehicle_id PK "Unique vehicle identifier (e.g. AI-101)"
        TEXT vehicle_type "Type: FLIGHT or TRAIN"
        TEXT route "Origin to Destination"
        TEXT departure "Scheduled departure date and time"
        INTEGER total_seats "Total seating capacity (30)"
        REAL base_fare "Baseline ticket fare in INR"
    }

    BOOKINGS {
        INTEGER booking_id PK "Auto-incrementing unique booking number"
        TEXT vehicle_id FK "References VEHICLES(vehicle_id)"
        INTEGER seat_number "Physical seat number (1 to 30)"
        TEXT passenger "Passenger full name"
        REAL fare_paid "Calculated dynamic ticket price"
        TEXT status "CONFIRMED or CANCELLED"
        TEXT booked_at "Timestamp of transaction"
    }

    WAITLIST {
        INTEGER wait_id PK "Auto-incrementing waitlist ticket ID"
        TEXT vehicle_id FK "References VEHICLES(vehicle_id)"
        TEXT passenger "Waitlisted passenger name"
        INTEGER priority "0=Normal, 1=Senior Citizen, 2=VIP"
        TEXT added_at "Timestamp when queued"
    }
```
