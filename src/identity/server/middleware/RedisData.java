package identity.server.middleware;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the data stored in a Redis checkpoint.
 */
public class RedisData implements Serializable {
    @Serial
    private static final long serialVersionUID = -4051323488435584209L;
    private final Map<String, Map<String, String>> accounts;
    private Map<String, String> uuids;
    private long timestamp;

    public RedisData() {
        accounts = new HashMap<>();
    }

    public Map<String, String> getUuids() {
        return uuids;
    }

    public void setUuids(Map<String, String> uuids) {
        this.uuids = uuids;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void addAccount(String loginName, Map<String, String> account) {
        accounts.put(loginName, account);
    }

    public Map<String, Map<String, String>> getAccounts() {
        return accounts;
    }
}
