package identity.server.middleware;

import identity.AddressUtils;
import identity.server.TimedClientSocketFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of RMI methods for inter-server communication
 * Handles server discovery, election, and replication
 * Write requests are forwarded to the coordinator server for processing
 */
public class ServerManagerImpl implements ServerManager {
    private static final long HEARTBEAT_INTERVAL = 3000; // 3 seconds
    private static final long ELECTION_TIMEOUT = 5000; // 5 seconds
    private static final int ACKNOWLEDGMENT_TIMEOUT_SECONDS = 5;
    private static final Logger logger = LogManager.getLogger();
    private final LocalData localData;
    private final int port;
    private final String serverId; // Unique identifier for this server
    private final Map<String, ServerInfo> serverList; // Information about all servers
    private final ReentrantLock serverListLock; // Lock for thread-safe access to server list
    private final LamportClock lamport; // Lamport timestamp
    private final EventLogger eventLogger; // Event logger
    private final AtomicInteger electionCounter;
    private final AtomicBoolean electionRunning;
    private ServerManager coordinator;
    private Timer timer;
    private ExecutorService electionExecutor;
    private String coordinatorServerAddress; // Address of the current coordinator server
    private Event lastEvent;
    private final String ownAddress;
    private long lastHeartbeat;

    /**
     * The ServerManagerImpl class represents the implementation of the server manager.
     * It manages the server's state, handles communication with other servers, and performs various server-related tasks.
     */
    public ServerManagerImpl(int port, boolean verbose) throws RemoteException {
        this.port = port;
        if (verbose) {
            Configurator.setLevel(logger, Level.DEBUG);
        } else {
            Configurator.setLevel(logger, Level.INFO);
        }
        serverId = UUID.randomUUID().toString();
        serverList = new HashMap<>();
        serverListLock = new ReentrantLock();
        lamport = new LamportClock();
        eventLogger = EventLogger.getInstance();
        electionExecutor = Executors.newCachedThreadPool();
        electionCounter = new AtomicInteger(0);
        electionRunning = new AtomicBoolean(false);
        lastHeartbeat = System.currentTimeMillis();
        localData = LocalData.getInstance();
        // Get local IP address
        String ip;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ip = "localhost";
            logger.log(Level.ERROR, "Failed to get own address: " + e.getMessage());
        }
        ownAddress = ip + ":" + port;
        registerRMI();
        // Connect to other servers and start the election process
        discoverServers();
        syncData();
        startElection(true);
        startCoordinatorFailureDetection();

    }

    /**
     * Registers the ServerManager as an RMI service.
     * It uses a custom RMIClientSocketFactory and RMIServerSocketFactory for secure communication.
     *
     * @throws RuntimeException if there is a remote exception during the registration process.
     */
    private void registerRMI() {
        try {
            RMIClientSocketFactory rmiClientSocketFactory = new TimedClientSocketFactory();
            RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
            ServerManager listener = (ServerManager) UnicastRemoteObject.exportObject(this, 0, rmiClientSocketFactory, rmiServerSocketFactory);
            Registry registry = LocateRegistry.getRegistry(port);
            registry.rebind("ServerManager", listener);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets a new coordinator for the server.
     *
     * @param serverAddress the address of the new coordinator server
     * @return true if the new coordinator is set successfully, false otherwise
     * @throws RemoteException if an error occurs while getting the coordinator from the registry
     */
    @Override
    public boolean setNewCoordinator(String serverAddress) throws RemoteException {
        boolean success = true;
        try {
            setServerRoles(serverAddress);
            coordinator = getServer(serverAddress);
            logger.info("New coordinator set: " + serverAddress);
        } catch (NotBoundException e) {
            logger.error("Coordinator " + serverAddress + " not found in registry");
            success = false;
        } finally {
            electionRunning.set(false);
        }
        //syncData();
        return success;
    }

    /**
     * Retrieves the log of events starting from the specified timestamp.
     *
     * @param startTimestamp The timestamp from which to start retrieving the log.
     * @return A list of events that occurred after the specified timestamp.
     * @throws RemoteException If a remote communication error occurs.
     */
    @Override
    public List<Event> getLog(long startTimestamp) throws RemoteException {
        List<Event> log = eventLogger.getEventLog();
        return log.stream().filter(e -> e.timestamp() >= startTimestamp).sorted().toList();
    }

    /**
     * Receives an ELECTION message from a server.
     * Compares the timestamp of the origin server with the server's own timestamp.
     * If the origin server has a lower timestamp, the server starts an election.
     *
     * @param senderServerId  The ID of the origin server.
     * @param senderTimestamp The latest timestamp of the origin server.
     * @return True if this server should become the coordinator, false otherwise.
     */
    @Override
    public boolean receiveElection(String senderServerId, long senderTimestamp) {
        logger.debug("Received ELECTION message from " + senderServerId);
        if (lamport.timestamp() > senderTimestamp) {
            new Thread(() -> startElection(false)).start();
            return true;
        }
        if (lamport.timestamp() == senderTimestamp && serverId.compareTo(senderServerId) > 0) {
            new Thread(() -> startElection(false)).start();
            return true;
        }
        return false;
    }

    /**
     * Returns the current coordinator server address.
     *
     * @return the current coordinator server address
     * @throws RemoteException if a remote exception occurs
     */
    @Override
    public String getCurrentCoordinator() throws RemoteException {
        return coordinatorServerAddress;
    }

    /**
     * Adds a new server to the system with the specified address and server ID.
     * The server is added as a backup server.
     *
     * @param address  the address of the new server
     * @param serverId the ID of the new server
     * @return true if the server was successfully added, false otherwise
     * @throws RemoteException if a remote exception occurs during the operation
     */
    @Override
    public boolean addNewServer(String address, String serverId) throws RemoteException {
        addServer(address, serverId, ServerInfo.ServerRole.BACKUP);
        for (String addr : otherServers().keySet()) {
            try {
                ServerManager server = getServer(addr);
                server.addServer(address, serverId, ServerInfo.ServerRole.BACKUP);
                logger.debug("Sent new server information to " + addr);
            } catch (Exception ignored) {
            }
        }

        return true;
    }

    /**
     * Returns the list of servers.
     *
     * @return a map containing server names as keys and server information as values
     * @throws RemoteException if a remote exception occurs
     */
    @Override
    public Map<String, ServerInfo> getServerList() throws RemoteException {
        return serverList;
    }

    /**
     * Receives and logs an acknowledgment event from a server.
     *
     * @param sourceServerId The ID of the source server.
     * @param timestamp      The timestamp of the acknowledgment event.
     * @throws RemoteException If a remote exception occurs.
     */
    @Override
    public void receiveAcknowledgment(String sourceServerId, long timestamp) throws RemoteException {
        eventLogger.logAcknowledgment(new Event(sourceServerId, timestamp, "ACK", null), serverId);
    }

    /**
     * Replicates the given event to ensure consistency across the distributed system.
     * If the event's timestamp is older than the current Lamport timestamp, it indicates an out-of-order event.
     * In this case, the method restores the checkpoint, syncs the data, and updates the Lamport timestamp.
     * If the event's timestamp is newer than the current Lamport timestamp, the method applies the event to the local data.
     * Finally, the method updates the Lamport timestamp and sends an acknowledgment for the event.
     *
     * @param event The event to be replicated.
     * @throws RemoteException If a remote communication error occurs.
     */
    @Override
    public void replicate(Event event) throws RemoteException {
        logger.info("Received replication request: " + event);
        if (event.timestamp() < lamport.timestamp()) {
            logger.info("Received out of order event. Fixing...");
            restoreCheckpoint(coordinator.getCheckpoint());
            syncData();
            logger.info("Data synced successfully");
        } else if (event.timestamp() >= lamport.timestamp()) {
            localData.applyEvent(event);
            logger.info("Event applied to local data.");
        }
        lamport.update(event.timestamp());
        sendAcknowledgment(event);
    }

    /**
     * Sends an acknowledgment to the coordinator for the given event.
     *
     * @param event The event for which the acknowledgment is being sent.
     */
    private void sendAcknowledgment(Event event) {
        try {
            coordinator.receiveAcknowledgment(serverId, event.timestamp());
            logger.debug("Acknowledgment sent to coordinator");
        } catch (Exception e) {
            // Handle exception
            logger.error("Failed to send acknowledgment to coordinator: " + e.getMessage());
        }
    }

    /**
     * Adds a server to the server list with the specified role.
     *
     * @param serverAddress the address of the server
     * @param role          the role of the server (COORDINATOR, BACKUP, or UNKNOWN)
     */
    @Override
    public boolean addServer(String serverAddress, String id, ServerInfo.ServerRole role) {
        serverListLock.lock();
        try {
            if (!serverList.containsKey(serverAddress)) {
                serverList.put(serverAddress, new ServerInfo(serverAddress, id, role));
            }
        } finally {
            serverListLock.unlock();
        }
        logger.info("Server added to server list: " + serverAddress);
        return true;
    }

    /**
     * Checks if the server is alive and updates its last heartbeat time.
     *
     * @return true indicating that the server is alive.
     * @throws RemoteException if a remote communication error occurs.
     */
    @Override
    public synchronized boolean isAlive() throws RemoteException {
        lastHeartbeat = System.currentTimeMillis();
        return true;
    }

    /**
     * Shuts down the server gracefully.
     */
    public void shutdownServer() {
        electionExecutor.shutdown();
        timer.cancel();
        localData.close();
    }


    /**
     * Discovers available servers and sets the current server as the coordinator if no other servers are available.
     * If other servers are available, it sets the current server's coordinator based on the existing server's coordinator.
     * It also notifies the coordinator of the new server and retrieves the updated server list.
     */
    private void discoverServers() {
        logger.info("Discovering servers...");
        // load current server list from file
        serverListLock.lock();
        serverList.putAll(loadServerList());
        // find an available server
        ServerManager server = null;
        for (String serverAddress : otherServers().keySet()) {
            try {
                server = getServer(serverAddress);
                logger.info("Server found: " + serverAddress);
            } catch (Exception e) {
                logger.debug("Could not connect to server at " + serverAddress);
                serverList.remove(serverAddress);
            }
        }
        serverListLock.unlock();
        // get the current coordinator
        try {
            if (server == null || server.getCurrentCoordinator() == null) {
                logger.info("No other available servers found. Setting self as coordinator.");
                setServerRoles(ownAddress);
                coordinator = this;
                coordinatorServerAddress = ownAddress;
                return;
            }
            setNewCoordinator(server.getCurrentCoordinator());
            // notify coordinator of new server
            coordinator.addNewServer(ownAddress, serverId);
            // retrieve updated list
            serverListLock.lock();
            serverList.putAll(coordinator.getServerList());
            serverListLock.unlock();
            logger.debug("Server discovery completed. Found servers: " + serverList.keySet());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a map of other servers excluding the current server.
     *
     * @return a map of other servers
     */
    private Map<String, ServerInfo> otherServers() {
        Map<String, ServerInfo> otherServers = new HashMap<>(serverList);
        otherServers.remove(ownAddress);
        return otherServers;
    }

    /**
     * Loads the server list from a file and returns a map of server information.
     * Only servers with the same first octet as the current server are added to the list,
     * reducing the number of servers to check during startup.
     *
     * @return A map of server information, where the key is the server address and the value is the server information.
     */
    private Map<String, ServerInfo> loadServerList() {
        Map<String, ServerInfo> serverList = new HashMap<>();
        AddressUtils.loadServerList().forEach(address ->
                serverList.put(address, new ServerInfo(address, UUID.randomUUID().toString(), ServerInfo.ServerRole.UNKNOWN)));
        return serverList;
    }

    /**
     * Updates the coordinator server address in the server list and adjusts roles accordingly.
     *
     * @param coordinatorAddress the address of the new coordinator server
     */
    private void setServerRoles(String coordinatorAddress) {
        serverListLock.lock();
        try {
            if (this.coordinatorServerAddress != null && !this.coordinatorServerAddress.equals(coordinatorAddress)) {
                // Revert the old coordinator server to backup
                if (serverList.containsKey(this.coordinatorServerAddress)) {
                    serverList.get(this.coordinatorServerAddress).setRole(ServerInfo.ServerRole.BACKUP);
                }
            }
            // Set the new coordinator server
            this.coordinatorServerAddress = coordinatorAddress;
            if (serverList.containsKey(coordinatorAddress)) {
                serverList.get(coordinatorAddress).setRole(ServerInfo.ServerRole.COORDINATOR);
            }
        } finally {
            serverListLock.unlock();
        }
    }

    /**
     * Starts the coordinator failure detection process.
     * This method schedules a timer task to periodically check the coordinator's heartbeat.
     * If the coordinator has not received a heartbeat in 3 intervals,
     * it initiates the election process.
     */
    private void startCoordinatorFailureDetection() {
        logger.debug("Starting coordinator failure detection process...");
        TimerTask coordinatorFailureDetector = new TimerTask() {
            @Override
            public void run() {
                if (!isCoordinator()) {
                    logger.debug("Checking coordinator's heartbeat...");
                    if (!isCoordinatorAlive()) {
                        logger.info("Coordinator failure detected. Initiating election process...");
                        startElection(true);
                    }
                } else if (System.currentTimeMillis() - lastHeartbeat > 3 * HEARTBEAT_INTERVAL) {
                    logger.info("Coordinator has not received a heartbeat in 3 intervals. Initiating election process...");
                    startElection(true);
                }
            }
        };

        // Schedule the coordinator failure detection task to run periodically
        timer = new Timer();
        timer.scheduleAtFixedRate(coordinatorFailureDetector, 0, HEARTBEAT_INTERVAL);
    }

    /**
     * Prunes the server list by removing servers that are not responding to heartbeat requests.
     */
    private void pruneServerList() {
        serverListLock.lock();
        try {
            for (String serverAddress : otherServers().keySet()) {
                try {
                    ServerManager server = getServer(serverAddress);
                    server.isAlive();
                } catch (Exception e) {
                    serverList.remove(serverAddress);
                }
            }
        } finally {
            serverListLock.unlock();
        }
    }

    /**
     * Checks if the coordinator is alive by sending a heartbeat request.
     *
     * @return true if the coordinator is responding to heartbeat requests, false otherwise.
     */
    private boolean isCoordinatorAlive() {
        // Check if the coordinator is responding to heartbeat requests
        AtomicBoolean coordinatorResponding = new AtomicBoolean(false);
        try {
            // Attempt to send a heartbeat request to the coordinator
            Thread timedThread = new Thread(()-> {
                try {
                    coordinatorResponding.set(coordinator.isAlive());
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            });
            timedThread.start();
            timedThread.join(HEARTBEAT_INTERVAL * 3);
        } catch (Exception e) {
            // Coordinator is not responding
            logger.debug("Coordinator is not responding to heartbeat requests: " + e.getMessage());
        }
        return coordinatorResponding.get();
    }

    /**
     * Starts the election process.
     * If the election is already in progress and force is false, the method will skip starting a new election.
     * If force is true, the method will forcefully start a new election even if one is already in progress.
     * <p>
     * The method sends an election message to all other servers in the server list and waits for their responses.
     * If no other servers respond to the election message, the current server declares itself as the coordinator.
     * After the election process is finished, the method synchronizes data with the new coordinator.
     */
    private void startElection(boolean force) {
        if (!electionRunning.compareAndSet(false, true) && !force) {
            logger.info("Election already in progress. Skipping...");
            return;
        }
        electionCounter.set(0);
        serverListLock.lock();
        try {
            if (electionExecutor.isShutdown()) {
                electionExecutor = Executors.newCachedThreadPool();
            }
            // Send election message to all other servers
            for (Map.Entry<String, ServerInfo> entry : otherServers().entrySet()) {
                String serverAddress = entry.getKey();
                electionExecutor.execute(() -> sendElectionMessage(serverAddress));
            }
        } finally {
            serverListLock.unlock();
        }
        // Wait for responses from other servers
        try {
            if (!electionExecutor.awaitTermination(ELECTION_TIMEOUT, TimeUnit.MILLISECONDS)) {
                logger.info("Election timed out");
            }
        } catch (InterruptedException ignored) {
        }
        // Check if any other servers responded to the election message
        if (electionCounter.get() == 0) {
            logger.info("No other servers responded to the election message. Declaring as coordinator...");
            declareAsCoordinator();
        } else {
            logger.debug("Received " + electionCounter.get() + " OK responses.");
        }
        logger.info("Election finished");
        syncData();
    }

    /**
     * Sends an election message to the specified server.
     *
     * @param serverAddress the address of the server to send the message to
     */
    private void sendElectionMessage(String serverAddress) {
        try {
            ServerManager server = getServer(serverAddress);
            long ownLamportTimestamp = lamport.timestamp();
            if (server.receiveElection(serverId, ownLamportTimestamp)) {
                logger.debug("Received OK from " + serverAddress);
                electionCounter.incrementAndGet();
                electionExecutor.shutdownNow();
            }
        } catch (Exception e) {
            logger.debug("Error sending ELECTION message to " + serverAddress);
        }
    }

    /**
     * Declares the current server as the coordinator.
     * This method updates the server's role, and broadcasts a coordinator message.
     */
    private void declareAsCoordinator() {
        logger.info("I am the new coordinator.");
        broadcastCoordinatorMessage();
        setServerRoles(ownAddress);
        electionRunning.set(false);
    }

    /**
     * Broadcasts the 'I AM COORDINATOR' message to all servers in the server list.
     * This method iterates through each server and sends the message to set the new coordinator.
     * If an error occurs while sending the message to a server, a warning is logged.
     */
    private void broadcastCoordinatorMessage() {
        serverListLock.lock();
        logger.debug("Broadcasting 'I AM COORDINATOR' message to all servers...");
        try {
            for (Map.Entry<String, ServerInfo> entry : otherServers().entrySet()) {
                String serverAddress = entry.getKey();
                try {
                    ServerManager server = getServer(serverAddress);
                    server.setNewCoordinator(ownAddress);
                } catch (Exception e) {
                    logger.warn("Error sending 'I AM COORDINATOR' message to " + serverAddress);
                }
            }
        } finally {
            serverListLock.unlock();
        }
        logger.debug("Broadcast complete.");
    }


    /**
     * Replicates the given event to backup servers.
     *
     * @param event The event to be replicated.
     */
    private void replicateToBackups(Event event) {
        // Store the method name and its arguments for potential retries
        lastEvent = event;

        // Log the event
        eventLogger.logEvent(event);

        // Replicate the event to all backup servers
        serverListLock.lock();
        try {
            for (ServerInfo serverInfo : serverList.values()) {
                String backupServerAddress = serverInfo.getAddress();
                if (!backupServerAddress.equals(coordinatorServerAddress)) {
                    try {
                        logger.info("Replicating to backup server: " + backupServerAddress);
                        ServerManager backupServer = getServer(backupServerAddress);
                        backupServer.replicate(event);
                    } catch (Exception e) {
                        // Handle replication failure
                        logger.warn("Failed to replicate to backup server: " + backupServerAddress);
                    }
                }
            }
        } finally {
            serverListLock.unlock();
        }
    }

    /**
     * Waits for acknowledgments from backup servers.
     * If the acknowledgments are not received within the timeout period,
     * the method retries replication up to 3 times.
     */
    private void waitForAcknowledgments() {
        int maxRetries = 3;
        int retryCount = 0;
        int acknowledgmentsReceived = 0;
        int acknowledgmentsNeeded = (otherServers().size() + 1) / 2;
        long startTime = System.currentTimeMillis();
        while (acknowledgmentsReceived <= acknowledgmentsNeeded && retryCount < maxRetries) {
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            if (TimeUnit.MILLISECONDS.toSeconds(elapsedTime) >= ACKNOWLEDGMENT_TIMEOUT_SECONDS) {
                retryCount++;
                logger.warn("Acknowledgment timeout occurred. Retrying replication... (Retry " + retryCount + ")");
                replicateToBackups(lastEvent);
                startTime = System.currentTimeMillis();
                continue;
            }
            Event acknowledgmentEvent = eventLogger.getAcknowledgmentEvent(serverId);
            if (acknowledgmentEvent != null) {
                acknowledgmentsReceived++;
                logger.debug("Acknowledgment received from backup server: " + acknowledgmentEvent.serverId());
            }
        }
        if (acknowledgmentsReceived <= acknowledgmentsNeeded) {
            pruneServerList();
            acknowledgmentsNeeded = (otherServers().size() + 1) / 2;
            if (acknowledgmentsReceived <= acknowledgmentsNeeded) {
                logger.warn("Maximum number of acknowledgment retries reached. Could not complete replication to at least 1/2 backup servers.");
            }
        }
    }

    /**
     * Checks if the current server is the coordinator server.
     *
     * @return true if the current server is the coordinator, false otherwise
     */
    private boolean isCoordinator() {
        return ownAddress.equals(coordinatorServerAddress);
    }

    /**
     * If the server is the coordinator, it handles the read request locally and replicates the operation to backup servers.
     * If the server is not the coordinator, it forwards the read request to the coordinator server.
     */
    @Override
    public Status handleWriteRequest(String methodName, Object... args) throws RemoteException {
        logger.info("Received write request: " + methodName);
        Status result;
        try {
            if (isCoordinator()) {
                lamport.increment();
                if (methodName.equals("createAccount")) {
                    // assign UUID
                    args = Arrays.copyOf(args, 4);
                    args[3] = UUID.randomUUID().toString();
                }
                Event event = new Event(serverId, lamport.timestamp(), methodName, args);
                // Serve the request locally
                result = localData.applyEvent(event);
                // Replicate the write operation to backup servers
                logger.info("Replicating write request to backup servers...");
                replicateToBackups(event);
                waitForAcknowledgments();
            } else {
                // Forward the request to the coordinator
                logger.info("Forwarding write request to coordinator: " + methodName);
                result = coordinator.handleWriteRequest(methodName, args);
            }
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * Retrieves the latest checkpoint data from the local data store.
     *
     * @return The RedisData object representing the checkpoint data.
     * @throws RemoteException if there is a communication error.
     */
    @Override
    public RedisData getCheckpoint() throws RemoteException {
        return localData.getCheckpoint();
    }

    /**
     * Restores the server's state from a Redis checkpoint.
     *
     * @param checkpoint The RedisData object representing the checkpoint to restore.
     */
    private void restoreCheckpoint(RedisData checkpoint) {
        localData.restoreCheckpoint(checkpoint);
    }

    /**
     * Synchronizes data with the coordinator.
     * If the server is the coordinator, the method returns without performing any synchronization.
     * Otherwise, it retrieves the log from the coordinator and applies the events to the local data.
     * If there is a timestamp mismatch, it retrieves the checkpoint from the coordinator and restores it first.
     */
    private void syncData() {
        if (isCoordinator()) {
            return;
        }
        logger.info("Syncing data with coordinator...");
        localData.lockData();
        logger.debug("Locked local data");
        long redisTimestamp = Long.parseLong(localData.read("timestamp", true));
        List<Event> events;
        try {
            logger.debug("Requesting log from coordinator...");
            events = coordinator.getLog(redisTimestamp);
            logger.debug("Received log from coordinator: " + events.size() + " events");
            if (events.isEmpty() || events.getFirst().timestamp() != redisTimestamp) {
                logger.debug("Timestamp mismatch. Retrieving checkpoint from coordinator...");
                restoreCheckpoint(coordinator.getCheckpoint());
            }
            for (int i = 1; i < events.size(); i++) {
                localData.applyEvent(events.get(i));
            }
            logger.info("Data synced successfully");
        } catch (RemoteException e) {
            logger.error("Failed to sync data with coordinator: " + e.getMessage());
        } finally {
            localData.unlockData();
            logger.debug("Unlocked local data");
        }
    }

    /**
     * Retrieves the ServerManager instance from the specified server address.
     *
     * @param serverAddress the address of the server in the format "ip:port"
     * @return the ServerManager instance
     * @throws NotBoundException if the server is not bound in the registry
     * @throws RemoteException   if there is a remote communication error
     */
    private ServerManager getServer(String serverAddress) throws NotBoundException, RemoteException {
        String serverIp = serverAddress.split(":")[0];
        int serverPort = Integer.parseInt(serverAddress.split(":")[1]);
        Registry registry = LocateRegistry.getRegistry(serverIp, serverPort);
        ServerManager server = (ServerManager) registry.lookup("ServerManager");
        if (!server.isAlive()) {
            throw new RemoteException("Server is unresponsive");
        }
        return server;
    }
}
