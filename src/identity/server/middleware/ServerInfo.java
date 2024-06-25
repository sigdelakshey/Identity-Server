package identity.server.middleware;

import java.io.Serial;

public class ServerInfo implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 7907773446779136716L;
    private String address;
    private String id;
    private ServerRole role;
    private ServerStatus status;

    public ServerInfo(String address, String id, ServerRole role) {
        this.address = address;
        this.id = id;
        this.role = role;
        this.status = ServerStatus.ACTIVE;
    }

    // Getter for address
    public String getAddress() {
        return address;
    }

    // Setter for address
    public void setAddress(String address) {
        this.address = address;
    }

    // Getter for id
    public String getId() {
        return id;
    }

    // Setter for id
    public void setId(String id) {
        this.id = id;
    }

    // Getter for role
    public ServerRole getRole() {
        return role;
    }

    // Setter for role
    public void setRole(ServerRole role) {
        this.role = role;
    }

    // Getter for status
    public ServerStatus getStatus() {
        return status;
    }

    // Setter for status
    public void setStatus(ServerStatus status) {
        this.status = status;
    }

    public enum ServerRole {
        COORDINATOR,
        BACKUP,
        UNKNOWN
    }

    public enum ServerStatus {
        ACTIVE,
        INACTIVE
    }
}
