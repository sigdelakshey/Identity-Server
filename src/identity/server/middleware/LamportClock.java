package identity.server.middleware;

/**
 * The LamportClock class represents a Lamport logical clock used for timestamping events in a distributed system.
 * It provides methods to increment the clock, retrieve the current timestamp, and update the clock based on a received timestamp.
 */
public class LamportClock {
    private long clock = 0;

    /**
     * Increments the clock by 1.
     */
    public synchronized void increment() {
        clock++;
    }

    /**
     * Retrieves the current timestamp of the clock.
     *
     * @return The current timestamp of the clock.
     */
    public synchronized long timestamp() {
        return clock;
    }

    /**
     * Updates the clock based on a received timestamp.
     * The clock is set to the maximum value between its current value and the received timestamp plus 1.
     *
     * @param receivedTimestamp The received timestamp to update the clock with.
     */
    public synchronized void update(long receivedTimestamp) {
        clock = Math.max(clock, receivedTimestamp + 1);
    }
}
