package identity.server.middleware;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface ServerManager extends Remote {

    /**
     * Sets a new coordinator for the server.
     *
     * @param serverAddress the address of the new coordinator server
     * @return true if the new coordinator is set successfully, false otherwise
     * @throws RemoteException if a remote communication error occurs
     */
    boolean setNewCoordinator(String serverAddress) throws RemoteException;

    /**
     * Retrieves the log of events starting from the specified timestamp.
     *
     * @param startTimestamp the timestamp from which to start retrieving the log
     * @return a list of Event objects representing the log
     * @throws RemoteException if a remote communication error occurs
     */
    List<Event> getLog(long startTimestamp) throws RemoteException;

    /**
     * Replicates the given event to other servers in the system.
     *
     * @param event the event to be replicated
     * @throws RemoteException if a remote communication error occurs
     */
    void replicate(Event event) throws RemoteException;

    /**
     * Receives an acknowledgment from a server with the specified serverId and timestamp.
     *
     * @param serverId  the ID of the server sending the acknowledgment
     * @param timestamp the timestamp of the acknowledgment
     * @throws RemoteException if a remote communication error occurs
     */
    void receiveAcknowledgment(String serverId, long timestamp) throws RemoteException;

    /**
     * Receives an election message from a server and compares the timestamps.
     * If the timestamps are the same, the server with the higher ID wins.
     *
     * @param serverId  the ID of the server sending the election message
     * @param timestamp the timestamp of the origin server
     * @return true if the server has a higher timestamp, false otherwise
     * @throws RemoteException if a remote communication error occurs
     */
    boolean receiveElection(String serverId, long timestamp) throws RemoteException;

    /**
     * Retrieves the current coordinators address in the form ip:port
     *
     * @return the current coordinator as a String.
     * @throws RemoteException if a remote communication error occurs.
     */
    String getCurrentCoordinator() throws RemoteException;

    /**
     * Adds a server to the server list.
     *
     * @param address  the address of the server in the form ip:port
     * @param serverId the ID of the server
     * @param role     the role of the server
     * @return true if the server was successfully added, false otherwise
     * @throws RemoteException if a remote communication error occurs
     */
    boolean addServer(String address, String serverId, ServerInfo.ServerRole role) throws RemoteException;

    /**
     * Adds a new server to the server list. This method is used by the coordinator to add a new server to the system.
     * The updated server list is then replicated to all servers in the system.
     *
     * @param address  the address of the server
     * @param serverId the ID of the server
     * @return true if the server was successfully added, false otherwise
     * @throws RemoteException if a remote exception occurs
     */
    boolean addNewServer(String address, String serverId) throws RemoteException;

    /**
     * Retrieves the list of available servers.
     *
     * @return a map containing server names as keys and corresponding server information as values
     * @throws RemoteException if a remote exception occurs during the retrieval process
     */
    Map<String, ServerInfo> getServerList() throws RemoteException;

    /**
     * Checks if the server is alive.
     *
     * @return true if the server is alive, nothing otherwise.
     * @throws RemoteException if a remote exception occurs.
     */
    boolean isAlive() throws RemoteException;

    /**
     * If the server is the coordinator, it handles the read request locally and replicates the operation to backup servers.
     * If the server is not the coordinator, it forwards the read request to the coordinator server.
     */
    Status handleWriteRequest(String methodName, Object... args) throws RemoteException;

    /**
     * Retrieves the current checkpoint data from Redis.
     *
     * @return the checkpoint data stored in Redis.
     * @throws RemoteException if there is a problem with the remote method invocation.
     */
    RedisData getCheckpoint() throws RemoteException;
}
