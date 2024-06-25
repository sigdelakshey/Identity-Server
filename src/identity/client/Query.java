package identity.client;

import java.util.UUID;

/**
 * The Query class represents a query object used in the identity client.
 * It contains various properties related to the query, such as query type, login name, name, password, UUID, new login name, and type.
 */
public class Query {
    private final String queryType;
    private String loginName;
    private String name;
    private String password;
    private UUID uuid;
    private String newLoginName;
    private String type;

    public Query(String queryType) {
        this.queryType = queryType;
    }

    public String getQueryType() {
        return queryType;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getNewLoginName() {
        return newLoginName;
    }

    public void setNewLoginName(String newLoginName) {
        this.newLoginName = newLoginName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
