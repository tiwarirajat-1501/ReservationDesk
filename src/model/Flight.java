package model;

/**
 * Flight.java — Concrete Subclass of Vehicle (Air Transport)
 *
 * Implements airline-specific dynamic fare calculation based on
 * capacity and scarcity.
 */
public class Flight extends Vehicle {

    /**
     * Flight-specific scarcity multiplier.
     * Reflects steeper price acceleration as seats diminish.
     */
    private static final double SCARCITY_MULTIPLIER = 1.5;

    public Flight(String vehicleId, String route, String departure,
                  int totalSeats, double baseFare) {
        super(vehicleId, "FLIGHT", route, departure, totalSeats, baseFare);
    }

    /**
     * Calculates flight fare based on seat scarcity ratio.
     *
     * Formula:
     *   scarcityRatio = totalSeats / remainingSeats
     *   fare = baseFare * (1 + (scarcityRatio - 1) * 1.5)
     */
    @Override
    public double calculateFare(int remainingSeats) {
        // Guard against division by zero
        if (remainingSeats <= 0) remainingSeats = 1;

        double scarcityRatio = (double) totalSeats / remainingSeats;
        return baseFare * (1.0 + (scarcityRatio - 1.0) * SCARCITY_MULTIPLIER);
    }
}
