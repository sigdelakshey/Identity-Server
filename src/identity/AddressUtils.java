package identity;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AddressUtils {
    public static List<String> loadServerList() {
        List<String> serverList = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File("src/identity/resources/serverlist"))) {
            String line;
            while (scanner.hasNextLine() && (line = scanner.nextLine()) != null) {
                serverList.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        removeUnavailableServers(serverList);
        return serverList;
    }

    public static void removeUnavailableServers(List<String> serverList) {
        serverList.removeIf(address -> !isAvailable(address));
    }

    public static boolean isAvailable(String address) {
        String ip = address.split(":")[0];
        int port = Integer.parseInt(address.split(":")[1]);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
