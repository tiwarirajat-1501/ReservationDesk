package model;

/**
 * Vehicle.java — Abstract Base Class for Bookable Transport
 *
 * Represents an abstract travel inventory item (flight, train, etc.).
 * Subclasses implement calculateFare to apply vehicle-specific
 * surge and discount policies based on seat occupancy.
 */
public abstract class Vehicle {

    /* ── Protected fields: accessible to subclasses (Flight, Train) ── */
    protected String vehicleId;    // Unique identifier, e.g., "AI-101"
    protected String vehicleType;  // "FLIGHT" or "TRAIN"
    protected String route;        // e.g., "Delhi -> Mumbai"
    protected String departure;    // e.g., "2026-09-15 06:00"
    protected int    totalSeats;   // Total seat capacity (e.g., 30)
    protected double baseFare;     // Starting ticket price in INR

    /**
     * Parameterised constructor called by subclass constructors.
     */
    public Vehicle(String vehicleId, String vehicleType, String route,
                   String departure, int totalSeats, double baseFare) {
        this.vehicleId   = vehicleId;
        this.vehicleType = vehicleType;
        this.route       = route;
        this.departure   = departure;
        this.totalSeats  = totalSeats;
        this.baseFare    = baseFare;
    }

    /**
     * Computes the dynamic fare based on remaining seat inventory.
     *
     * @param remainingSeats Number of seats still available.
     * @return The calculated ticket fare.
     */
    public abstract double calculateFare(int remainingSeats);

    /**
     * Returns a one-line human-readable summary of this vehicle.
     * This is a concrete method in the abstract class — demonstrating
     * that abstract classes CAN have concrete implementations.
     */
    public String getSummary() {
        return String.format("[%s] %s | %s | Dep: %s | Seats: %d | Base Fare: ₹%.2f",
                vehicleType, vehicleId, route, departure, totalSeats, baseFare);
    }

    /* ── Getters (Encapsulation) ── */
    public String getVehicleId()   { return vehicleId;   }
    public String getVehicleType() { return vehicleType; }
    public String getRoute()       { return route;       }
    public String getDeparture()   { return departure;   }
    public int    getTotalSeats()  { return totalSeats;  }
    public double getBaseFare()    { return baseFare;    }

    /**
     * toString() — used by JComboBox to display vehicle in the GUI.
     */
    @Override
    public String toString() {
        return vehicleId + " — " + route;
    }
}
