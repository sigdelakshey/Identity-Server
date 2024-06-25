package identity.server.middleware;

import identity.server.Account;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The LocalData class is a singleton object that provides access to local data stored in Redis.
 * It handles the synchronization and access to the Redis database using Jedis.
 * This class is responsible for creating, modifying, and deleting accounts, as well as reading account information.
 */
public class LocalData {
    private static LocalData instance;
    private static final Logger logger = LogManager.getLogger();
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 5186;
    private static final long CHECKPOINT_INTERVAL = 30000;
    private static boolean verbose = false;
    private final JedisPool pool;
    private Timer timer;
    private final ReentrantReadWriteLock lock; // Used to lock data during synchronization

    private LocalData() {
        Configurator.setLevel(logger.getName(), verbose ? Level.DEBUG : Level.INFO);
        lock = new ReentrantReadWriteLock();
        logger.info("Initializing Redis...");
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        pool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, 0);
        set("timestamp", "0", false);
        logger.debug("Redis initialized successfully.");
        startCheckpointingThread();
    }

    /**
     * Returns the singleton instance of the LocalData class.
     *
     * @return the LocalData instance
     */
    public static synchronized LocalData getInstance() {
        if (instance == null) {
            instance = new LocalData();
        }
        return instance;
    }

    /**
     * Sets the verbosisty mode for the LocalData class.
     *
     * @param verbose the boolean value indicating whether to enable verbose mode or not
     */
    public static void setVerbose(boolean verbose) {
        LocalData.verbose = verbose;
    }

    /**
     * Allpies the specified event to the local data.
     *
     * @param event the event to apply
     * @return the status of the operation
     */
    public Status applyEvent(Event event) {
        logger.info("Applying event locally: {}", event);
        String methodName = event.methodName();
        Object[] args = event.args();
        Status result;
        switch (methodName) {
            case "createAccount": {
                result = createAccount((String) args[0], (String) args[1], (String) args[2], (String) args[3], false);
                set("timestamp", String.valueOf(event.timestamp()), false);
                break;
            }
            case "modifyAccount": {
                result = modifyAccount((String) args[0], (String) args[1], (String) args[2], false);
                set("timestamp", String.valueOf(event.timestamp()), false);
                break;
            }
            case "deleteAccount": {
                result = deleteAccount((String) args[0], (String) args[1], false);
                set("timestamp", String.valueOf(event.timestamp()), false);
                break;
            }
            default:
                result = Status.INVALID_OPERATION;
        }
        return result;
    }

    /**
     * Reads the value associated with the given key from the local data store.
     *
     * @param key     the key to retrieve the value for
     * @param syncing indicates whether the read operation is part of a synchronization process
     * @return the value associated with the key, or null if the key is not found
     */
    public String read(String key, boolean syncing) {
        String value;
        try {
            if (!syncing) {
                lock.readLock().lock();
            } else {
                assert lock.isWriteLockedByCurrentThread() : "Attempted sync on unlocked data";
            }
            Jedis jedis = pool.getResource();
            value = jedis.get(key);
            jedis.close();
        } finally {
            if (!syncing) {
                lock.readLock().unlock();
            }
        }
        return value;
    }

    /**
     * Reads a map from the Redis database using the specified key.
     *
     * @param key     the key used to retrieve the map from the database
     * @param syncing a flag indicating whether the read operation is part of a synchronization process
     * @return the map retrieved from the database
     */
    public Map<String, String> readMap(String key, boolean syncing) {
        Map<String, String> map;
        try {
            if (!syncing) {
                lock.readLock().lock();
            } else {
                assert lock.isWriteLockedByCurrentThread() : "Attempted sync on unlocked data";
            }
            Jedis jedis = pool.getResource();
            map = jedis.hgetAll(key);
            jedis.close();
        } finally {
            if (!syncing) {
                lock.readLock().unlock();
            }
        }
        return map;
    }

    /**
     * Sets the value for the given key in the local data store.
     * Redis will handle simultaneous reads and writes during normal operation.
     * If it is not part of a synchronization process the method will acquire a lock.
     *
     * @param key     the key to set the value for
     * @param value   the value to set
     * @param syncing true if the data is being synchronized, false otherwise
     */
    private void set(String key, String value, boolean syncing) {
        // read lock is used here only to restrict access during synchronization
        // redis will handle simultaneous reads and writes during normal operation
        try {
            if (!syncing) {
                lock.readLock().lock();
            } else {
                assert lock.isWriteLockedByCurrentThread() : "Attempted sync on unlocked data";
            }
            Jedis jedis = pool.getResource();
            jedis.set(key, value);
            jedis.close();
        } finally {
            if (!syncing) {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Sets the values in the map associated with the given key.
     *
     * @param key     the key to associate the values with
     * @param fields  an array of field names
     * @param values  an array of corresponding values
     * @param syncing a flag indicating whether the operation is for synchronization
     * @throws AssertionError if the lengths of fields and values arrays are not the same
     */
    private void setMap(String key, String[] fields, String[] values, boolean syncing) {
        // readlock is used here only to restrict access during synchronization
        // redis will handle simultaneous reads and writes during normal operation
        assert fields.length == values.length : "Fields and values must be the same length";
        try {
            if (!syncing) {
                lock.readLock().lock();
            } else {
                assert lock.isWriteLockedByCurrentThread() : "Attempted sync on unlocked data";
            }
            Jedis jedis = pool.getResource();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] != null) jedis.hset(key, fields[i], values[i]);
            }
            jedis.close();
        } finally {
            if (!syncing) {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Deletes the value associated with the given key from the local data store.
     * A read lock is used to restrict access during synchronization.
     * Note that Redis will handle simultaneous reads and writes during normal operation.
     *
     * @param key     the key of the value to be deleted
     * @param syncing true if the deletion is performed during synchronization, false otherwise
     */
    private void delete(String key, boolean syncing) {
        // readlock is used here only to restrict access during synchronization
        // redis will handle simultaneous reads and writes during normal operation
        try {
            if (!syncing) {
                lock.readLock().lock();
            } else {
                assert lock.isWriteLockedByCurrentThread() : "Attempted sync on unlocked data";
            }
            Jedis jedis = pool.getResource();
            jedis.del(key);
            jedis.close();
        } finally {
            if (!syncing) {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Deletes an entry from the map stored in Redis.
     *
     * @param key     the key of the map
     * @param field   the field to be deleted from the map
     * @param syncing indicates whether the deletion is part of a synchronization process
     */
    private void deleteMapEntry(String key, String field, boolean syncing) {
        // readlock is used here only to restrict access during synchronization
        // redis will handle simultaneous reads and writes during normal operation
        try {
            if (!syncing) {
                lock.readLock().lock();
            } else {
                assert lock.isWriteLockedByCurrentThread() : "Attempted sync on unlocked data";
            }
            Jedis jedis = pool.getResource();
            jedis.hdel(key, field);
            jedis.close();
        } finally {
            if (!syncing) {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Creates an account locally on the coordinator server.
     * This method performs the actual account creation logic using Redis.
     *
     * @param loginName the login name for the account
     * @param realName  the real name for the account
     * @param password  the password for the account
     * @return a message indicating the status of the account creation
     */
    private Status createAccount(String loginName, String realName, String password, String uuid, boolean syncing) {
        if (loginName.equals("uuids") || loginName.equals("timestamp")) {
            return Status.INVALID_LOGIN_NAME;
        }
        if (readMap(loginName, syncing) != null && !readMap(loginName, syncing).isEmpty()) {
            logger.info("Login name already exists: {}", loginName);
            return Status.ACCOUNT_ALREADY_EXISTS;
        }
        String[] fields = {"uuid", "realName", "password"};
        String[] values = {uuid, realName, password};
        if (realName == null) {
            fields[1] = null;
        }
        if (password == null) {
            fields[2] = null;
        }
        setMap(loginName, fields, values, syncing);
        setMap("uuids", new String[]{uuid}, new String[]{loginName}, syncing);
        logger.info("Account created successfully. UUID: {}", uuid);
        return Status.SUCCESS;
    }

    /**
     * Retrieves an Account object based on the provided login name.
     *
     * @param loginName The login name of the account.
     * @param syncing   A boolean indicating whether the operation is part of a synchronization process.
     * @return The Account object associated with the login name, or null if not found.
     */
    public Account getAccount(String loginName, boolean syncing) {
        if (loginName.equals("uuids") || loginName.equals("timestamp")) {
            return null;
        }
        logger.debug("Looking up account for login name: {}", loginName);
        Map<String, String> accountData = readMap(loginName, syncing);
        if (accountData.isEmpty()) {
            logger.info("Login name not found: {}", loginName);
            return null;
        }
        logger.info("Account found");
        return new Account(loginName, accountData.get("realName"), accountData.get("password"), accountData.get("uuid"));
    }

    /**
     * Retrieves an Account object based on the provided UUID.
     *
     * @param uuid    The UUID of the account to retrieve.
     * @param syncing A boolean indicating whether the operation is part of a synchronization process.
     * @return The Account object corresponding to the provided UUID, or null if not found.
     */
    public Account getAccountByUUID(String uuid, boolean syncing) {
        logger.info("Reverse lookup for UUID: {}", uuid);
        Map<String, String> uuids = readMap("uuids", syncing);
        if (uuids == null || uuids.get(uuid) == null) {
            logger.info("UUID not found: {}", uuid);
            return null;
        }
        return getAccount(uuids.get(uuid), syncing);
    }

    /**
     * Deletes an account locally. If the account is found and the password is correct, the account is deleted.
     *
     * @param loginName The login name of the account to delete.
     * @param password  The password of the account to delete. May be null.
     * @param syncing   A boolean indicating whether the operation is part of a synchronization process.
     * @return The status of the operation.
     */
    private Status deleteAccount(String loginName, String password, boolean syncing) {
        if (loginName.equals("uuids") || loginName.equals("timestamp")) {
            return Status.INVALID_LOGIN_NAME;
        }
        logger.info("Deleting account for login name: {}", loginName);
        Map<String, String> accountData = readMap(loginName, syncing);
        if (accountData == null || accountData.isEmpty()) {
            logger.info("Login name not found: {}", loginName);
            return Status.ACCOUNT_NOT_FOUND;
        }
        String storedPassword = accountData.get("password");
        if (storedPassword != null && !storedPassword.equals(password)) {
            logger.warn("Incorrect password for login name: {}", loginName);
            return Status.INVALID_PASSWORD;
        }
        String uuid = accountData.get("uuid");
        deleteMapEntry("uuids", uuid, syncing);
        delete(loginName, syncing);
        logger.info("Account deleted successfully");
        return Status.SUCCESS;
    }

    /**
     * Modifies the login name of an account.
     * If the account is found and the password is correct, the account is modified.
     *
     * @param oldLoginName The old login name of the account to modify.
     * @param newLoginName The new login name to assign to the account.
     * @param password  The password of the account to modify. May be null.
     * @param syncing   A boolean indicating whether the operation is part of a synchronization process.
     * @return The status of the operation.
     */
    private Status modifyAccount(String oldLoginName, String newLoginName, String password, boolean syncing) {
        if (oldLoginName.equals("uuids") || oldLoginName.equals("timestamp")) {
            return Status.INVALID_LOGIN_NAME;
        }
        if (newLoginName.equals("uuids") || newLoginName.equals("timestamp")) {
            return Status.INVALID_LOGIN_NAME;
        }
        logger.info("Modifying account for login name: {}", oldLoginName);
        Map<String, String> accountData = readMap(oldLoginName, syncing);
        if (accountData == null || accountData.isEmpty()) {
            logger.info("Login name not found: {}", oldLoginName);
            return Status.ACCOUNT_NOT_FOUND;
        }
        String uuid = accountData.get("uuid");
        String realName = accountData.get("realName");
        String storedPassword = accountData.get("password");
        if (storedPassword != null && !storedPassword.equals(password)) {
            logger.warn("Incorrect password for login name: {}", oldLoginName);
            return Status.INVALID_PASSWORD;
        }
        deleteAccount(oldLoginName, password, syncing);
        String[] fields = {"uuid", "realName", "password"};
        String[] values = {uuid, realName, storedPassword};
        if (realName == null) {
            fields[1] = null;
        }
        if (storedPassword == null) {
            fields[2] = null;
        }
        setMap(newLoginName, fields, values, syncing);
        setMap("uuids", new String[]{uuid}, new String[]{newLoginName}, syncing);
        logger.info("Login name modified successfully");
        return Status.SUCCESS;
    }

    /**
     * Retrieves a list of all accounts.
     *
     * @param syncing a boolean indicating whether the operation is part of a synchronization process
     * @return a list of Account objects
     */
    public List<Account> getAccounts(boolean syncing) {
        logger.info("Retrieving accounts");
        List<Account> accounts = new ArrayList<>();
        Collection<String> loginNames = readMap("uuids", syncing).values();
        loginNames.forEach(loginName -> {
            Map<String, String> accountData = readMap(loginName, syncing);
            if (accountData != null) {
                accounts.add(new Account(loginName, accountData.get("realName"), accountData.get("password"), accountData.get("uuid")));
            }
        });
        logger.info("Retrieval successful");
        return accounts;
    }

    /**
     * Saves to disk then retrieves the data stored in Redis.
     */
    public RedisData getCheckpoint() {
        lockData();
        Jedis jedis = pool.getResource();
        jedis.save(); // save checkpoint synchronously
        RedisData data = new RedisData();
        Map<String, String> uuids = readMap("uuids", true);
        data.setUuids(uuids);
        uuids.forEach((uuid, loginName) -> {
            Map<String, String> accountData = readMap(loginName, true);
            if (accountData != null) {
                data.addAccount(loginName, accountData);
            }
        });
        data.setTimestamp(Long.parseLong(read("timestamp", true)));
        unlockData();
        return data;
    }


    /**
     * Acquires a write lock to protect the data during synchronization.
     */
    public void lockData() {
        lock.writeLock().lock();
    }

    /**
     * Releases the write lock after synchronization is complete.
     */
    public void unlockData() {
        lock.writeLock().unlock();
    }

    /**
     * Checkpoints the data to Redis.
     * This method performs a background save of the data to Redis using the bgsave command.
     */
    private void checkpointToRedis() {
        if (lock.isWriteLocked()) {
            logger.debug("Data is syncing, skipping checkpoint...");
            return;
        }
        logger.info("Checkpointing to Redis...");
        try (Jedis jedis = pool.getResource()) {
            jedis.bgsave();
            logger.debug("Checkpointing to Redis completed.");
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * Starts the checkpointing thread.
     * This method initializes a timer and schedules checkpointToRedis() to run at a fixed rate.
     */
    private void startCheckpointingThread() {
        logger.debug("Starting checkpointing thread...");
        if (timer != null) {
            timer.cancel();
        } else {
            timer = new Timer();
        }
        timer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                checkpointToRedis();
            }
        }, 0, CHECKPOINT_INTERVAL);
    }

    /**
     * Closes the LocalData instance gracefully.
     */
    public void close() {
        timer.cancel();
        checkpointToRedis();
        pool.close();
        instance = null;
    }

    /**
     * Restores the checkpoint by flushing all existing data in Redis and
     * populating it with the data from the given RedisData object.
     *
     * @param checkpoint The RedisData object containing the data to restore.
     */
    public void restoreCheckpoint(RedisData checkpoint) {
        logger.info("Restoring checkpoint...");
        assert lock.isWriteLockedByCurrentThread() : "Attempted to restore checkpoint without write lock";
        Jedis jedis = pool.getResource();
        jedis.flushAll();
        checkpoint.getAccounts().forEach(jedis::hset);
        Map<String, String> uuids = checkpoint.getUuids();
        if (uuids != null && !uuids.isEmpty()) jedis.hset("uuids", checkpoint.getUuids());
        set("timestamp", String.valueOf(checkpoint.getTimestamp()), true);
        logger.info("Checkpoint restored successfully.");
    }
}
