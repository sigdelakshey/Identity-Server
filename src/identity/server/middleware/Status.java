package identity.server.middleware;

/**
 * Enum representing the status of an operation.
 * The possible values are:
 * - SUCCESS: The operation was successful.
 * - INVALID_PASSWORD: The provided password is invalid.
 * - INVALID_LOGIN_NAME: The provided login name is invalid.
 * - ACCOUNT_NOT_FOUND: The account was not found.
 * - ACCOUNT_ALREADY_EXISTS: The account already exists.
 * - INVALID_OPERATION: The operation is invalid.
 */
public enum Status {
    SUCCESS,
    INVALID_PASSWORD,
    INVALID_LOGIN_NAME,
    ACCOUNT_NOT_FOUND,
    ACCOUNT_ALREADY_EXISTS, INVALID_OPERATION,
}
