package identity.client;

/**
 * Represents the parsed arguments for the client.
 *
 * @param serverHost the server host address
 * @param port       the server port number
 * @param query      the query to be executed
 */
public record ParsedArguments(String serverHost, int port, Query query) {
}