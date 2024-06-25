# Project 3 & 4/Identity Server

* Author: Akshey Sigdel (CS555), Kaden Davis (CS455)
* Class: Distributed Systems | Spring 2024

## Overview

In this project, we enhanced the reliability, performance, and fault tolerance of our existing RMI-based identity server
that manages login names, associated real usernames and UUIDs. It allows clients to search for user information and
ensures data persistence in case of a system crash or reboot. Like previous iteration, this server also support the
following operations: creation, lookup, reverse-lookup, modification, delete and get.

By allowing multiple servers to run concurrently, we were able to increase the reliability and performance of the
system. This required using many concepts related to distributed systems. The white paper outlines the technical details
of our implementation.

## Manifest

A listing of source files and other non-generated files and a brief (one line)
explanation of the purpose of each file:

    │── gradle/wrapper/
    │   ├── gradle-wrapper.jar  |-->  Gradle jar file
    │   ├── gradle-wrapper.properties   |--> Properties for defining build system and verisons.
    │── src/identity/
    │   │── client/
    │   │   ├── IdClient.java     |-->  Represents a client for an identity application.
    │   │   ├── ParsedArguments.java|-->  Responsible to parse command-line argument.
    │   │   ├── Query.java |-->  Represents query class. 
    |   │── resources/
    │   │   ├── Client_Truststore    |-->  Trust store for client.
    │   │   ├── Server.cer   |-->  Server certificate.
    │   │   ├── Server_Keystore   |-->  Keystore for server.
    │   │   ├── mysecurity.policy  |-->  Security policy.
    │   │   ├── serverlist  |-->  Initial serverlist
    │   |── server/
    |   │   |── middleware/
    |   │   │   ├── Event.java     |-->  Represents event class.
    |   │   │   ├── EventLoggerjava     |-->  Represents event logger class.
    |   │   │   ├── LamportClock.java     |-->  Represents lamport timestamp class.
    |   │   │   ├── LocalData.java     |-->  Provides access to local data stored in redis.
    |   │   │   ├── RedisData.java     |-->  Represents data stored in redis checkpoint.
    |   │   │   ├── ServerInfo.java     |-->  Represents information for server.
    |   │   │   ├── ServerManager.java     |-->  Represents interface for server manager  class.
    |   │   │   ├── ServerManagerImpl.java     |-->  Represents a server manager for a server.
    |   │   │   ├── Status.java     |-->  Represents operation status.
    │   │   ├── Account.java     |-->  Represents account class.
    │   │   ├── IdServer.java  |-->  Represents a server for an identity application.
    │   │   ├── IdService.java  |-->  Represents service interface for the server.
    │   │   ├── TimedClientSocketFactory.java  |-->  Represents a timed client socket factory with 2 seconds timeout.
    │   |── AddressUtlis.java   |-->  Represents utils to remove unavailable server.
    │── test/identity/
    │   ├── 
    │── Dockerfile                    |-->  Dockerfile.
    │── Makefile                    |-->  Makefile.
    │── README.md                    |-->  Readme file.
    │── build.gradle                |-->  Gradle build configuration.
    │── gradlew                    |-->  Gradle wrapper.
    │── gradlew.bat           |-->  Gradle wrapper(windows OS).
    │── p3/4-grade.txt       
    │── runClient.sh       |-->  Client run shell script
    │── runServer.sh       |-->  Server run shell script
    │── runNServersDocker.sh        |-->  Run multiple servers in docker shell script

## Building the Project

We have used gradle as a build tool for this project. Moreover, we have provided a Makefile that invokes gradle commands
that should simplify the build process for the user. For users who simply want to run the project on a *nix OS, they can
use the run scripts provided in this folder.

Follow the instructions below:

### Prerequisites

- Ensure that you have Gradle installed on your system. You can download it
  from [Gradle's official website](https://gradle.org/install/).
- Java JDK 21 or higher installed on your system. You can download it
  from [Oracle's official website](https://www.oracle.com/java/technologies/downloads/#java21).
- Redis server installed on your system. You can download it from [Redis's official website](https://redis.io/download).

### Building Steps

1. **Navigate to the Project Directory**: Open a terminal or command prompt and navigate to the root directory of the
   project.

    2. **Execute the Makefile Targets**:

        - To build the project, run the following command:
          ```bash
          make build
          ```
          The `build` target compiles and builds the server and client applications (tests are not built through this
          command).

        - To start the server, execute one of the following options:
          ```bash
          make build
          cd build/install/p2/bin/
          ./server [-n <PORT>] [-d <DEBUG_LEVEL>]
          ```
          ```bash
          make server ARGS="[-n <PORT>] [-d <DEBUG_LEVEL>]"
          ```
          ```bash
          ./runServer.sh [-n <PORT>] [-d <DEBUG_LEVEL>]
          ```
          Note: without arguments, the chat server is launched on port 5185 with the default debug level.

        - To start the client, execute one of the following options (more details on the queries can be found in the
          Features and Usage section):
          ```bash
          make build
          cd build/install/p2/bin/
          ./client [-s <SERVER_ADDRESS> -n <PORT>] <QUERY>
          ```
          ```bash
          make client ARGS="[-s <SERVER_ADDRESS> -n <PORT>] <QUERY>"
          ```
          ```bash
          ./runClient.sh [-s <SERVER_ADDRESS> -n <PORT>] <QUERY>
          ```
            Note: In contrast to p2, the client may be started without specifying a server address and port. In this case, the client will read the serverlist file to find an available server.

        - To clean the project, run:
          ```bash
          make clean
          ```
          Warning: the clean target will also remove the save file for redis.

### Running servers in docker
The makefile has a target to run the server in docker. To run the server in docker, an image must be built first. To
build the image, run the following command:
```bash
sudo make docker-build
```
This command will build the docker image with the name `identity-server`. Once the image is built, the server can be run
in docker using the following command:
```bash
sudo make docker-server PORT=<port_num> [ARGS="-v"]
```
The specified port must be unused and for our project we used the values 5187-5189. If other ports are used you will
need to update the serverlist file with the new ports. The optional argument `-v` can be used to run the server in
verbose mode. The server will run in the background and can be stopped using the following command:


To stop all running containers, run the following command:
```bash
sudo make docker-killall
```

To aid in running multiple servers in docker, the runNServersDocker.sh script can be used. This script will run the
specified number of docker servers in the background. The script can be run using the following command:
```bash
sudo ./runNServersDocker.sh <num_servers>
```
Note: The script will run the servers on ports 5187-5187+num_servers. Additionally, if num_servers is large, the client
may have to wait for a long time before all servers complete their election process. 3 worked well for our testing.

### Running servers on onyx nodes
The runServer.sh script can be used to run the server on onyx nodes in the same way as described above. The only
consideration  that must be made is the behaviour of the redis server.
Since all onyx nodes share the same data, the redis server might revert to data on another node. To avoid this, each
node can be run with a different copy of the project in separate directories. This is somewhat tedious and if there are
better solutions, please let us know. 

## Features and usage

This Phase 2 of the project included following features:

- Phase 2:
    - RMI based implementation
    - RMI over SSL
    - SHA-2 password encryption
    - Command-line query parsing
    - Periodic checkpoint
    - Shutdown hook
    - Operations: creation, lookup, reverse-lookup, modification, delete and fetch.

We consolidated the phase 3 & 4 into a single objective and designed the project accordingly:

- Phase 3 & 4:
    - Server discovery
    - Sequential Consistency
    - Bully election algorithm
    - Replication and synchronization
    - Failure detection and recovery

As per usage, one may simply build the project as described above and then initiate a server on a multiple terminal and
initiate the client on a separate terminal. From there, begin using the above commands to make use of any desired
functionality.

The available queries options for the client are as follows:

```
-c,--create <LOGIN_NAME> [REAL_NAME] [-p <PASSWORD>]            Create a new user
-d,--delete <LOGIN_NAME>  [-p <PASSWORD>]                       Delete a user
-g,--get <users|uuids|all>                                      Get a list of all login names | UUIDs | login names + real names + UUIDs + Account descriptions
-h,--help                                                       Display a help message
-l,--lookup <LOGIN_NAME>                                        Lookup a user
-m,--modify <OLD_LOGIN_NAME> <NEW_LOGIN_NAME> [-p <PASSWORD>]   Modify a user
-p,--password <PASSWORD>                                        The password if required
-r,--reverse-lookup <UUID>                                      Lookup a user by UUID
```

## Testing

We tested the client previously in p2 using JUnit 5. However, for this iteration of the project, it did not make sense
to use unit tests.
We instead used a manual testing process to verify the correctness of the server with a focus on election, replication,
synchronization, and fault tolerance. The scenarios we tested can be found in the testScenarios.txt file.

## Demonstration web Link

- YouTube Link: [Project Demonstration](https://youtu.be/8GfvanHNMcs)

## Reflection

This project definitely forced me to learn a lot about distributed systems. The project was challenging and required a
lot of time debugging issues that were more difficult to identify. Each time an error was detected during testing, it
was usually due to an edge case that was not considered. 
In particular was the issue of long wait times when a server
attempted an RMI connection to an unreachable or incorrect address. Many different solutions were attempted to solve
this and the final solution was to use to filter the server lists by attempting to connect with a normal socket. This
worked because the connect method has a connection timeout that can be set. Setting the timeout of the Ssl-RMI sockets
proved to be ineffective for whatever reason. 

A clear technical documentation upfront does clarify the problem, approach, and anticipates challenges. However, it is
still necessary to remain flexible and adapt as needed.

## Contribution

Both of us contributed equally to the project success, each focusing on different aspects. At first, we started working
on white paper where we clearly outlined the technical details for the project. Akshey then implemented the
functionalities for server setup, consistency and election protocol. After which, Kaden cleaned and improved the code
structure while simultaneously working on data synchronization and server recovery. Finally, Kaden conducted through
manual testing to identify and rectify minor issues.

## Sources used

The lecture notes and example files were sufficient as reference for the development of the project.
The official Java documentation on Oracle's website was also invaluable in the debugging process.
 

