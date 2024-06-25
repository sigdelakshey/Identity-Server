package identity.client;

import identity.AddressUtils;
import identity.server.IdService;
import org.apache.commons.cli.*;

import java.nio.charset.StandardCharsets;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The IdClient class represents a client for interacting with the identity service using RMI.
 * It provides clients with methods for creating, looking up, modifying, and deleting user accounts,
 * as well as retrieving user information.
 */
public class IdClient {

    public static final String TRUSTSTORE_PASS = "test123";
    private String serverHost;
    private int port;
    private IdService idService;

    public IdClient(String serverHost, int port) {
        this.serverHost = serverHost;
        this.port = port;
        this.idService = null;
    }

    /**
     * Hashes the given password using the SHA-256 algorithm and returns the hashed password as a hexadecimal string.
     *
     * @param password the password to be hashed
     * @return the hashed password as a hexadecimal string
     * @throws NoSuchAlgorithmException if the SHA-256 algorithm is not available
     */
    private String hashPassword(String password) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedHash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
        for (byte b : encodedHash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Creates a new user account using RMI with the specified login name, name, and password.
     *
     * @param loginName the login name for the new user account
     * @param name      the name of the new user
     * @param password  the password for the new user account
     */
    public void create(String loginName, String name, String password) {
        try {
            System.out.println("Creating user");
            String encodedPassword = password == null ? null : hashPassword(password);
            String response = idService.createAccount(loginName, name, encodedPassword);
            System.out.println(response);
        } catch (RemoteException | NoSuchAlgorithmException e) {
            System.err.println("Failed to create user: " + loginName);
        }
    }

    /**
     * Performs a lookup for a user account using RMI based on the provided login name.
     * Prints the response from the identity service.
     *
     * @param loginName the login name of the user account to lookup
     */
    public void lookup(String loginName) {
        try {
            System.out.println("Looking up user");
            String response = idService.lookupAccount(loginName);
            System.out.println(response);
        } catch (RemoteException e) {
            System.err.println("Failed to lookup user: " + loginName);
        }
    }

    /**
     * Performs a reverse lookup using RMI of a user based on the given UUID.
     * Prints the response from the identity service.
     *
     * @param uuid the UUID of the user to perform the reverse lookup on
     */
    public void reverseLookup(UUID uuid) {
        try {
            System.out.println("Reverse looking up user");
            String response = idService.reverseLookup(uuid.toString());
            System.out.println(response);
        } catch (RemoteException e) {
            System.err.println("Failed to reverse lookup user: " + uuid);
        }
    }


    /**
     * Modifies a user account using RMI by changing the login name.
     *
     * @param oldLoginName The current login name of the user.
     * @param newLoginName The new login name to be assigned to the user.
     * @param password     The new password to be assigned to the user.
     */
    public void modify(String oldLoginName, String newLoginName, String password) {
        try {
            System.out.println("Modifying user");
            String encodedPassword = password != null ? hashPassword(password) : null;
            String response = idService.modifyAccount(oldLoginName, newLoginName, encodedPassword);
            System.out.println(response);
        } catch (RemoteException | NoSuchAlgorithmException e) {
            System.err.println("Failed to modify user: " + oldLoginName);
        }
    }

    /**
     * Deletes a user account with the specified login name and password.
     *
     * @param loginName the login name of the user to be deleted
     * @param password  the password of the user to be deleted
     */
    public void delete(String loginName, String password) {
        try {
            System.out.println("Deleting user");
            String encodedPassword = password != null ? hashPassword(password) : null;
            String response = idService.deleteAccount(loginName, encodedPassword);
            System.out.println(response);
        } catch (RemoteException | NoSuchAlgorithmException e) {
            System.err.println("Failed to delete user: " + loginName);
        }
    }

    /**
     * Retrieves the type of data of all accounts from the identity service.
     *
     * @param type the type of data to retrieve
     */
    public void get(String type) {
        try {
            System.out.println("Getting " + type);
            String response = idService.getAllAccounts(type);
            System.out.println(response);
        } catch (RemoteException e) {
            System.err.println("Failed to get " + type);
        }
    }

    /**
     * Executes the given query based on its type.
     *
     * @param query the query to be executed
     */
    private void executeQuery(Query query) {
        assert query != null;
        assert query.getQueryType() != null;
        switch (query.getQueryType()) {
            case "create" -> create(query.getLoginName(), query.getName(), query.getPassword());
            case "lookup" -> lookup(query.getLoginName());
            case "reverse-lookup" -> reverseLookup(query.getUuid());
            case "modify" -> modify(query.getLoginName(), query.getNewLoginName(), query.getPassword());
            case "delete" -> delete(query.getLoginName(), query.getPassword());
            case "get" -> get(query.getType());
            default -> {
                System.err.println("Invalid query type: " + query.getQueryType());
                System.exit(0);
            }
        }
    }

    /**
     * Returns an instance of Options that contains the available command-line options for the IdClient program.
     *
     * @return an instance of Options with the available command-line options
     */
    private static Options getOptions() {
        Options options = new Options();
        options.addOption(Option.builder("s")
                .longOpt("server")
                .desc("The server to connect to")
                .hasArg()
                .argName("SERVER_HOST")
                .build());
        options.addOption(Option.builder("n")
                .longOpt("numport")
                .desc("The port to connect to")
                .hasArg()
                .argName("PORT")
                .build());
        options.addOption(Option.builder("c")
                .longOpt("create")
                .desc("Create a new user, password optional")
                .hasArgs()
                .argName("<LOGIN_NAME> [REAL_NAME]")
                .build());
        options.addOption(Option.builder("l")
                .longOpt("lookup")
                .desc("Lookup a user")
                .hasArg()
                .argName("LOGIN_NAME")
                .build());
        options.addOption(Option.builder("r")
                .longOpt("reverse-lookup")
                .desc("Reverse lookup a user")
                .hasArg()
                .argName("UUID")
                .build());
        options.addOption(Option.builder("m")
                .longOpt("modify")
                .desc("Modify a user")
                .hasArgs()
                .numberOfArgs(2)
                .argName("<OLD_LOGIN_NAME> <NEW_LOGIN_NAME>")
                .build());
        options.addOption(Option.builder("d")
                .longOpt("delete")
                .desc("Delete a user")
                .hasArg()
                .argName("LOGIN_NAME")
                .build());
        options.addOption(Option.builder("g")
                .longOpt("get")
                .desc("Get a user")
                .hasArg()
                .argName("users|uuids|all")
                .build());
        options.addOption(Option.builder("p")
                .longOpt("password")
                .desc("The password if required by query")
                .hasArg()
                .argName("PASSWORD")
                .build());
        options.addOption("h", "help", false, "Display this help message");
        return options;
    }

    /**
     * Parses the command line arguments and returns a ParsedArguments object.
     *
     * @param args the command line arguments
     * @return a ParsedArguments object containing the parsed arguments
     */
    private static ParsedArguments parseArguments(String[] args) {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = null;
        Options options = getOptions();
        try {
            commandLine = parser.parse(options, args);
            assert commandLine != null;
        } catch (ParseException e) {
            System.err.println("invalid arguments. Use -h for help." + e.getMessage());
            System.exit(0);
        }
        if (commandLine.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setWidth(100);
            formatter.printHelp("java identity.client.IdClient --server <serverhost> [--numport <port#>] <query>", options);
            System.exit(0);
        }
        String serverHost = null;
        if (commandLine.hasOption("s")) {
            serverHost = commandLine.getOptionValue("s");
        }
        int port = 5185;
        if (commandLine.hasOption("n")) {
            try {
                port = Integer.parseInt(commandLine.getOptionValue("n"));
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number");
                System.exit(0);
            }
        }
        Query query = null;
        int numQueries = 0;
        if (commandLine.hasOption("c")) {
            query = new Query("create");
            String[] values = commandLine.getOptionValues("c");
            if (values == null || values.length == 0 || values.length > 2) {
                System.err.println("Invalid number of arguments for create. Use -h for help.");
                System.exit(0);
            }
            query.setLoginName(values[0]);
            if (values.length == 2) {
                query.setName(values[1]);
            }
            numQueries++;
        }
        if (commandLine.hasOption("l")) {
            query = new Query("lookup");
            query.setLoginName(commandLine.getOptionValue("l"));
            numQueries++;
        }
        if (commandLine.hasOption("r")) {
            query = new Query("reverse-lookup");
            try {
                query.setUuid(UUID.fromString(commandLine.getOptionValue("r")));
            } catch (IllegalArgumentException e) {
                System.err.println("Invalid UUID");
                System.exit(0);
            }
            numQueries++;
        }
        if (commandLine.hasOption("m")) {
            query = new Query("modify");
            String[] values = commandLine.getOptionValues("m");
            if (values == null || values.length != 2) {
                System.err.println("Invalid number of arguments for modify. Use -h for help.");
                System.exit(0);
            }
            query.setLoginName(values[0]);
            query.setNewLoginName(values[1]);
            numQueries++;
        }
        if (commandLine.hasOption("d")) {
            query = new Query("delete");
            query.setLoginName(commandLine.getOptionValue("d"));
            numQueries++;
        }
        if (commandLine.hasOption("g")) {
            query = new Query("get");
            String type = commandLine.getOptionValue("g");
            if (!type.equals("users") && !type.equals("uuids") && !type.equals("all")) {
                System.err.println("Invalid argument for get. Use -h for help.");
                System.exit(0);
            }
            query.setType(type);
            numQueries++;
        }
        if (numQueries != 1) {
            System.err.println("Invalid query");
            System.exit(0);
        }
        if (commandLine.hasOption("p")) {
            query.setPassword(commandLine.getOptionValue("p"));
        }
        return new ParsedArguments(serverHost, port, query);
    }


    /**
     * Connects to the Identity Server by loading the server list and attempting to connect to each server address.
     * If a server host is specified, it is added to the server list and attempted to connect first.
     * Returns true if a successful connection is established, false otherwise.
     */
    public boolean connect() {
        List<String> addresses = loadServerList();
        if (serverHost != null) {
            addresses.addFirst(serverHost + ":" + port);
        }
        System.out.println("Addresses to try:");
        addresses.forEach(System.out::println);
        System.out.println();
        while (!addresses.isEmpty()) {
            String address = addresses.removeFirst();
            String ip = address.split(":")[0];
            int port = Integer.parseInt(address.split(":")[1]);
            try {
                Registry reg = LocateRegistry.getRegistry(ip, port);
                idService = (IdService) reg.lookup("IdentityServer");
                System.out.println("Connected to server at " + address);
                serverHost = ip;
                this.port = port;
                return true;
            } catch (RemoteException | NotBoundException e) {
                System.err.println("Failed to connect to server at " + address);
            }
        }
        return false;
    }

    /**
     * Loads the server list from a file and returns it as a list of strings.
     * The server list file should be located at "src/identity/resources/serverlist" relative to the project root.
     * The order of servers in the list is randomized to distribute the load.
     *
     * @return The server list as a list of strings.
     */
    private List<String> loadServerList() {
        List<String> serverList = AddressUtils.loadServerList();
        Collections.shuffle(serverList); // Randomize the order of servers to distribute load
        return serverList;
    }

    /**
     * The main entry point of the IdClient application.
     * It sets the necessary system properties for SSL and security policy,
     * parses command line arguments, creates an instance of IdClient,
     * establishes a connection to the server, and executes the specified query.
     *
     * @param args The command line arguments passed to the application.
     */
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.trustStore", "src/identity/resources/Client_Truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASS);
        System.setProperty("java.security.policy", "src/identity/resources/mysecurity.policy");
        ParsedArguments parsedArgs = parseArguments(args);
        IdClient client = new IdClient(parsedArgs.serverHost(), parsedArgs.port());
        Query query = parsedArgs.query();

        if (!client.connect()) {
            System.err.println("Failed to connect to server");
            System.exit(0);
        }
        client.executeQuery(query);
    }
}
