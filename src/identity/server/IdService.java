
package identity.server;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * The IdService interface defines the methods for managing user accounts and performing account lookups.
 * It extends the Remote interface to support RMI.
 */
public interface IdService extends Remote {
    /**
     * Creates a new user account with the specified login name, real name, and password.
     *
     * @param loginName the login name for the new account
     * @param realName  the real name for the new account
     * @param password  the password for the new account
     * @return the UUID of the newly created account
     * @throws RemoteException if a remote communication error occurs
     */
    String createAccount(String loginName, String realName, String password) throws RemoteException;

    /**
     * Looks up an account by its login name and returns the corresponding UUID.
     *
     * @param loginName the login name of the account to lookup
     * @return the UUID of the account
     * @throws RemoteException if a remote communication error occurs
     */
    String lookupAccount(String loginName) throws RemoteException;

    /**
     * Performs a reverse lookup of an account by its UUID and returns the corresponding login name.
     *
     * @param uuid the UUID of the account to reverse lookup
     * @return the login name of the account
     * @throws RemoteException if a remote communication error occurs
     */
    String reverseLookup(String uuid) throws RemoteException;

    /**
     * Modifies an existing account by changing its login name and/or password.
     *
     * @param oldLoginName the current login name of the account to modify
     * @param newLoginName the new login name for the account
     * @param password     the new password for the account
     * @return the UUID of the modified account
     * @throws RemoteException if a remote communication error occurs
     */
    String modifyAccount(String oldLoginName, String newLoginName, String password) throws RemoteException;

    /**
     * Deletes an account by its login name and password.
     *
     * @param loginName the login name of the account to delete
     * @param password  the password of the account to delete
     * @return the UUID of the deleted account
     * @throws RemoteException if a remote communication error occurs
     */
    String deleteAccount(String loginName, String password) throws RemoteException;

    /**
     * Retrieves all accounts of the specified type.
     *
     * @param type the type of accounts to retrieve
     * @return a string representation of all accounts of the specified type
     * @throws RemoteException if a remote communication error occurs
     */
    String getAllAccounts(String type) throws RemoteException;
}
