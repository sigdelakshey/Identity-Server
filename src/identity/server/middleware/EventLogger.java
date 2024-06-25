package identity.server.middleware;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The EventLogger class is responsible for logging events and acknowledgments in a thread-safe manner.
 * It provides methods to log events, log acknowledgments, retrieve acknowledgment events, and retrieve the event log.
 */
public class EventLogger {
    private static EventLogger instance;
    private final List<Event> eventLog;
    private final Map<String, Event> acknowledgmentLog;

    private EventLogger() {
        eventLog = new ArrayList<>();
        acknowledgmentLog = new HashMap<>();
    }

    /**
     * Returns the singleton instance of the EventLogger class.
     *
     * @return the singleton instance of the EventLogger class
     */
    public static synchronized EventLogger getInstance() {
        if (instance == null) {
            instance = new EventLogger();
        }
        return instance;
    }

    /**
     * Logs an event to the event log.
     *
     * @param event the event to be logged
     */
    public synchronized void logEvent(Event event) {
        eventLog.add(event);
    }

    /**
     * Logs an acknowledgment event.
     *
     * @param acknowledgmentEvent The acknowledgment event to be logged.
     * @param sourceServerId      The ID of the source server.
     */
    public synchronized void logAcknowledgment(Event acknowledgmentEvent, String sourceServerId) {
        acknowledgmentLog.put(sourceServerId, acknowledgmentEvent);
    }

    /**
     * Retrieves the acknowledgment event for a specific source server.
     *
     * @param sourceServerId The ID of the source server.
     * @return The acknowledgment event associated with the specified source server.
     */
    public synchronized Event getAcknowledgmentEvent(String sourceServerId) {
        return acknowledgmentLog.get(sourceServerId);
    }

    /**
     * Returns the event log.
     *
     * @return the event log
     */
    public synchronized List<Event> getEventLog() {
        return eventLog;
    }
}
