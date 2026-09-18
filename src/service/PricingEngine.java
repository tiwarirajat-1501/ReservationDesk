package service;

import model.Vehicle;

/**
 * PricingEngine.java — Dynamic Pricing Engine
 *
 * Combines vehicle-level scarcity scaling with statistical surge pricing.
 * When recent bookings show high variance (measured via sample standard
 * deviation and coefficient of variation), a surge multiplier is applied.
 */
public class PricingEngine {

    /**
     * Calculates the dynamic ticket price considering:
     * 1. Seat scarcity (via vehicle-specific fare calculations)
     * 2. Booking velocity surge (via standard deviation of recent transactions)
     *
     * @param vehicle      The Flight or Train being booked.
     * @param bookedCount  Number of seats already booked.
     * @param recentFares  Array of recently paid fares for surge detection.
     * @return             The dynamically calculated fare, rounded to 2 decimals.
     */
    public static double calculateDynamicPrice(Vehicle vehicle,
                                                int bookedCount,
                                                double[] recentFares) {
        int totalSeats     = vehicle.getTotalSeats();
        int remainingSeats = totalSeats - bookedCount;
        if (remainingSeats <= 0) remainingSeats = 1;

        // Base vehicle fare according to remaining capacity
        double baseDynamicFare = vehicle.calculateFare(remainingSeats);

        /* ── Step 2: Surge detection via Standard Deviation ───
         * If recent fares have high variance (coefficient of
         * variation), it means prices have been fluctuating,
         * indicating a booking surge. We apply a modest
         * surge multiplier to capitalise on demand.
         * ─────────────────────────────────────────────────── */
        double surgeFactor = 1.0;
        if (recentFares != null && recentFares.length >= 2) {
            double mean   = calculateMean(recentFares);
            double stdDev = calculateStdDev(recentFares);
            if (mean > 0) {
                // Coefficient of Variation (CV) — normalised measure of dispersion
                double cv = stdDev / mean;
                surgeFactor = 1.0 + (cv * 0.3);  // 30% weight to surge
            }
        }

        /* ── Step 3: Final price ────────────────────────────── */
        double finalPrice = baseDynamicFare * surgeFactor;

        // Round to 2 decimal places
        return Math.round(finalPrice * 100.0) / 100.0;
    }

    /* ══════════════════════════════════════════════════════
     * Standard Deviation (Sample) — σ
     *
     * Formula:
     *   σ = sqrt[ Σ(xᵢ − x̄)² / (n − 1) ]
     *
     * Uses (n−1) denominator (Bessel's correction) for
     * unbiased sample standard deviation.
     * ══════════════════════════════════════════════════════ */
    public static double calculateStdDev(double[] values) {
        if (values == null || values.length < 2) return 0.0;

        double mean = calculateMean(values);

        // Sum of squared differences from mean
        double sumSquaredDiffs = 0.0;
        for (double v : values) {
            double diff = v - mean;
            sumSquaredDiffs += diff * diff;
        }

        // Sample variance (Bessel's correction: n − 1)
        double variance = sumSquaredDiffs / (values.length - 1);

        return Math.sqrt(variance);
    }

    /**
     * Arithmetic Mean — x̄ = Σxᵢ / n
     */
    public static double calculateMean(double[] values) {
        if (values == null || values.length == 0) return 0.0;
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
