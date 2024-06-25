package identity.server.middleware;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an event in the system.
 * An event consists of a server ID, timestamp, method name, and arguments.
 */
public record Event(String serverId, long timestamp, String methodName,
                    Object[] args) implements Serializable, Comparable<Event> {
    @Serial
    private static final long serialVersionUID = 4683688742648193908L;

    @Override
    public String toString() {
        return "Event[" +
                "serverId=" + serverId + ", " +
                "timestamp=" + timestamp + ", " +
                "methodName=" + methodName + ", " +
                "args=" + Arrays.toString(args) + ']';
    }

    /**
     * Compares this Event object with another Event object.
     * The comparison is based on the timestamp and serverId of the events.
     *
     * @param o the Event object to be compared
     * @return a negative integer, zero, or a positive integer as this Event is less than, equal to, or greater than the specified Event
     */
    @Override
    public int compareTo(Event o) {
        int r = Long.compare(this.timestamp, o.timestamp);
        return r != 0 ? r : this.serverId.compareTo(o.serverId);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Event event = (Event) object;
        return timestamp == event.timestamp && Objects.equals(serverId, event.serverId) && Objects.equals(methodName, event.methodName) && Arrays.equals(args, event.args);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(serverId, timestamp, methodName);
        result = 31 * result + Arrays.hashCode(args);
        return result;
    }
}
