package model;

/**
 * Train.java — Concrete Subclass of Vehicle (Rail Transport)
 *
 * Implements rail-specific dynamic fare calculation based on
 * capacity and steady scarcity scaling.
 */
public class Train extends Vehicle {

    /**
     * Train-specific scarcity multiplier.
     * Rail ticket fares typically follow regulated, steady pricing.
     */
    private static final double SCARCITY_MULTIPLIER = 1.2;

    public Train(String vehicleId, String route, String departure,
                 int totalSeats, double baseFare) {
        super(vehicleId, "TRAIN", route, departure, totalSeats, baseFare);
    }

    /**
     * Calculates train fare based on seat scarcity ratio.
     *
     * Formula:
     *   scarcityRatio = totalSeats / remainingSeats
     *   fare = baseFare * (1 + (scarcityRatio - 1) * 1.2)
     */
    @Override
    public double calculateFare(int remainingSeats) {
        // Guard against division by zero
        if (remainingSeats <= 0) remainingSeats = 1;

        double scarcityRatio = (double) totalSeats / remainingSeats;
        return baseFare * (1.0 + (scarcityRatio - 1.0) * SCARCITY_MULTIPLIER);
    }
}
