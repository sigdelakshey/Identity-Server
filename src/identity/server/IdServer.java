package identity.server;

import identity.server.middleware.LocalData;
import identity.server.middleware.ServerManagerImpl;
import identity.server.middleware.Status;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

/**
 * The IdServer class implements a non-primary backup protocol for a distributed identity server.
 */
public class IdServer implements IdService {

    private static final int DEFAULT_PORT = 5185;
    public static final String KEYSTORE_PASS = "test123";
    public static final String TRUSTSTORE_PASS = "test123";
    private static final Logger logger = LogManager.getLogger();
    private final int port;
    private final boolean verbose;
    private final LocalData localData;
    private ServerManagerImpl serverManager;

    /**
     * Represents an identity server that handles client requests for generating unique IDs.
     * The server is initialized with a specified port, verbosity level, and server ID.
     */
    public IdServer(int port, boolean verbose) throws RemoteException {
        this.port = port;
        this.verbose = verbose;
        if (verbose) {
            Configurator.setLevel(logger, Level.DEBUG);
        } else {
            Configurator.setLevel(logger, Level.INFO);
        }
        localData = LocalData.getInstance();
    }

    /**
     * Creates an account with the given login name, real name, and password.
     * This method checks if the current server is the coordinator before performing the write operation.
     * If not, it forwards the request to the coordinator server.
     *
     * @param loginName the login name for the account
     * @param realName  the real name for the account
     * @param password  the password for the account
     * @return a message indicating the status of the account creation
     * @throws RemoteException if a remote communication error occurs
     */
    @Override
    public String createAccount(String loginName, String realName, String password) throws RemoteException {
        Status status = serverManager.handleWriteRequest("createAccount", loginName, realName, password);
        switch (status) {
            case SUCCESS:
                String uuid = null;
                while (uuid == null) { // Wait for the account to be created
                    uuid = localData.getAccount(loginName, false).getUUID();
                }
                return "Account created successfully.\nUUID: " + localData.getAccount(loginName, false).getUUID();
            case INVALID_LOGIN_NAME:
                return "Error: Invalid login name.";
            case ACCOUNT_ALREADY_EXISTS:
                return "Error: Account already exists.";
            default:
                return "Error: Account creation failed.";
        }
    }

    /**
     * Looks up an account for the given login name.
     *
     * @param loginName the login name of the account to lookup
     * @return the account information as a string if found, or an error message if not found or an error occurred
     * @throws RemoteException if a remote exception occurs during the lookup process
     */
    @Override
    public String lookupAccount(String loginName) throws RemoteException {
        Account account = localData.getAccount(loginName, false);
        if (account != null) {
            return account.toStringWithoutPassword();
        } else {
            return "Error: Login name not found.";
        }
    }

    /**
     * Performs a reverse lookup in the identity server using the provided UUID.
     *
     * @param uuid the UUID to perform the reverse lookup for
     * @return a string representation of the account associated with the UUID, or an error message if the lookup fails
     * @throws RemoteException if a remote exception occurs during the lookup process
     */
    @Override
    public String reverseLookup(String uuid) throws RemoteException {
        Account account = localData.getAccountByUUID(uuid, false);
        if (account != null) {
            return account.toStringWithoutPassword();
        } else {
            return "Error: UUID not found.";
        }
    }

    /**
     * Modifies an account with the given oldlogin name, newlogin name, and password.
     * This method checks if the current server is the coordinator before performing the write operation.
     * If not, it forwards the request to the coordinator server.
     *
     * @param oldLoginName The current login name of the account.
     * @param newLoginName The new login name to be set for the account.
     * @param password     The password to be set for the account.
     * @return A message indicating the result of the account modification.
     * @throws RemoteException If a remote communication error occurs.
     */
    @Override
    public String modifyAccount(String oldLoginName, String newLoginName, String password) throws RemoteException {
        Status status = serverManager.handleWriteRequest("modifyAccount", oldLoginName, newLoginName, password);
        return switch (status) {
            case SUCCESS -> "Account modified successfully.";
            case ACCOUNT_NOT_FOUND -> "Error: Login name not found.";
            case INVALID_LOGIN_NAME -> "Error: Invalid login name.";
            case INVALID_PASSWORD -> "Error: Invalid password.";
            default -> "Error: Account modification failed.";
        };
    }

    /**
     * Delete an account with the given login name and password.
     * This method checks if the current server is the coordinator before performing the write operation.
     * If not, it forwards the request to the coordinator server.
     *
     * @param loginName the login name of the account to be deleted
     * @param password  the password of the account to be deleted
     * @return a message indicating the result of the account deletion
     * @throws RemoteException if a remote exception occurs during the account deletion process
     */
    @Override
    public String deleteAccount(String loginName, String password) throws RemoteException {
        Status status = serverManager.handleWriteRequest("deleteAccount", loginName, password);
        return switch (status) {
            case SUCCESS -> "Account deleted successfully.";
            case ACCOUNT_NOT_FOUND -> "Error: Account not found.";
            case INVALID_LOGIN_NAME -> "Error: Invalid login name.";
            case INVALID_PASSWORD -> "Error: Invalid password.";
            default -> "Error: Account deletion failed.";
        };
    }

    /**
     * Retrieves all accounts of the specified type.
     *
     * @param type the type of accounts to retrieve ("users", "uuids", or "all")
     * @return a string representation of the retrieved accounts
     * @throws RemoteException if a remote exception occurs during the retrieval process
     */
    @Override
    public String getAllAccounts(String type) throws RemoteException {
        logger.info("Retrieving {}", type);
        if (!type.equals("users") && !type.equals("uuids") && !type.equals("all")) {
            logger.warn("Invalid type specified: {}", type);
            return "Error: Invalid type specified.";
        }
        List<Account> accounts = localData.getAccounts(false);
        StringBuilder result = new StringBuilder();
        accounts.forEach(account -> {
            switch (type) {
                case "users":
                    result.append(account.getLoginName()).append("\n");
                    break;
                case "uuids":
                    result.append(account.getUUID()).append("\n");
                    break;
                case "all":
                    result.append(account.toStringWithoutPassword()).append("\n");
                    break;
            }
        });
        logger.info("Retrieval successful");
        return result.toString();

    }

    /**
     * Starts the RMI registry, the Identity Server, and the checkpointing thread.
     * Adds a shutdown hook to close the Redis connection.
     *
     * @throws RemoteException if a remote communication error occurs
     */
    public void start() throws RemoteException {
        logger.info("Starting Identity Server....");
        RMIClientSocketFactory rmiClientSocketFactory = new TimedClientSocketFactory();
        RMIServerSocketFactory rmiServerSocketFactory = new SslRMIServerSocketFactory();
        IdService ccAuth = (IdService) UnicastRemoteObject.exportObject(this, 0, rmiClientSocketFactory, rmiServerSocketFactory);
        Registry registry = LocateRegistry.createRegistry(port);
        logger.debug("Registry created");
        registry.rebind("IdentityServer", ccAuth);
        logger.info("Server started on port: {}", port);
        serverManager = new ServerManagerImpl(port, verbose);

        logger.debug("Server Manager created");
        String ip;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        System.out.printf("Server started at %s:%d %s\n", ip, port, verbose ? "(verbose)" : "");
        addShutdownHook();
        logger.debug("shutdown hook added");
    }

    /**
     * Adds a shutdown hook to gracefully shutdown the server.
     * This method registers a shutdown hook that will be executed when the JVM is shutting down.
     * The shutdown hook cancels the timer, checkpoints data to Redis one final time and closes the Redis connection.
     */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serverManager.shutdownServer();
            localData.close();
            System.out.println("Server shutdown gracefully.");
        }));
    }

    /**
     * The main method is the entry point of the IdServer application.
     * It parses command line arguments, sets up system properties for SSL,
     * and starts the IdServer.
     *
     * @param args The command line arguments passed to the application.
     */
    public static void main(String[] args) {
        IdServer server;
        int port = DEFAULT_PORT;
        boolean verbose = false;
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addOption("n", "numport", true, "The port to listen on");
        options.addOption("v", "verbose", false, "Prints detailed messages to the console");
        options.addOption("h", "help", false, "Display this help message");
        options.addOption("i", "id", true, "The server ID (optional, defaults to a random UUID)");
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(options, args);
            assert commandLine != null;
        } catch (ParseException e) {
            System.err.println("invalid arguments. Use -h for help.");
            System.exit(0);
        }
        if (commandLine.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java identity.server.IdServer [OPTION]...", options);
            System.exit(0);
        }
        if (commandLine.hasOption("n")) {
            try {
                port = Integer.parseInt(commandLine.getOptionValue("n"));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number");
                System.exit(0);
            }
        }
        if (commandLine.hasOption("v")) {
            verbose = true;
            Configurator.setLevel(logger, Level.DEBUG);
        } else {
            Configurator.setLevel(logger, Level.INFO);
        }
        LocalData.setVerbose(verbose);
        logger.debug("Setting System Properties for SSL...");
        System.setProperty("javax.net.ssl.keyStore", "src/identity/resources/Server_Keystore");
        System.setProperty("javax.net.ssl.keyStorePassword", KEYSTORE_PASS);
        System.setProperty("javax.net.ssl.trustStore", "src/identity/resources/Client_Truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASS);
        System.setProperty("java.security.policy", "src/identity/resources/mysecurity.policy");

        try {
            server = new IdServer(port, verbose);
            server.start();
        } catch (RemoteException e) {
            logger.fatal(e.getMessage());
        }
    }
}
