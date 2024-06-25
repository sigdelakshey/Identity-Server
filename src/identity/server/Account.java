package identity.server;

import java.io.Serial;
import java.io.Serializable;

/**
 * The Account class represents a user account in the system.
 * It stores information such as login name, real name, password, and UUID.
 */
public class Account implements Serializable {
    @Serial
    private static final long serialVersionUID = -9161124086041886754L;
    private final String loginName;
    private final String realName;
    private final String password;
    private final String uuid;

    public Account(String loginName, String realName, String password, String uuid) {
        this.loginName = loginName;
        this.realName = realName;
        this.password = password;
        this.uuid = uuid;
    }

    public String getLoginName() {
        return loginName;
    }

    public String getRealName() {
        return realName;
    }

    public String getPassword() {
        return password;
    }

    public String getUUID() {
        return uuid;
    }

    /**
     * Returns a string representation of the Account object without including the password.
     * The string includes the login name, real name, and UUID of the Account.
     *
     * @return a string representation of the Account object without the password
     */
    public String toStringWithoutPassword() {
        return "Login Name: " + loginName + "\nReal Name: " + realName + "\nUUID: " + uuid;
    }

    /**
     * Returns a string representation of the Account object.
     *
     * @return a formatted string containing the login name, real name, UUID, password status, and last change timestamp
     */
    @Override
    public String toString() {
        return String.format("Login Name: %s\nReal Name: %s\nUUID: %s\nPassword: %s",
                loginName, realName, uuid, password != null ? "Encrypted" : "Not set");
    }
}

