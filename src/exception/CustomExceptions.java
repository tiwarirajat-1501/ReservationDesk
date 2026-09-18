package exception;

/**
 * Custom application checked exceptions for the reservation system.
 * Handles concurrency seat conflicts, transactional database rollback failures,
 * and invalid transport vehicle configurations.
 */
public class CustomExceptions {

    /**
     * Thrown when an agent thread attempts to lock or book a seat
     * that is already reserved or held by another thread.
     */
    public static class SeatAlreadyBookedException extends Exception {
        public SeatAlreadyBookedException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when a database reservation transaction encounters an error
     * and rolls back without committing changes.
     */
    public static class BookingFailedException extends Exception {
        public BookingFailedException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when an unsupported vehicle type or route configuration is parsed.
     */
    public static class InvalidVehicleException extends Exception {
        public InvalidVehicleException(String message) {
            super(message);
        }
    }
}
