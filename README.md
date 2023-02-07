# Simulate Link State Routing Protocol with Java Socket Programming

A pure user-space program which simulates the major functionalities of a routing device running a simplified Link State Routing protocol.

## How To Run:

```bash
$mvn compile assembly:single
$java -cp target/COMP535-{version}-SNAPSHOT-jar-with-dependencies.jar socs.network.Main conf/router{number}.conf

```

## Available Commands:
**Note**
to be updated with more commands soon

| Command   | Description                                                                                                                                                                                                                                                |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| attach    | <ul><li> attach the link to the remote router, which is identified by the given simulated ip; </li><li> to establish the connection via socket, you need to indentify the process IP and process Port;</li><li> additionally, weight is the cost to transmitting data through the link</li></ul> |
| start     | broadcast Hello to all links <ul><li>through a socket connection, send HELLO packets to all linked routers;</li><li>waits for response from the links;</li><li>sets status of the linked router to TWO_WAY and sends another Hello </li><li>neighbor relationship is established</li>                                                                                                                                                                                                                               |
| neighbors | output the neighbors of the routers                                                                                                                                                                                                                        |

NOTE: for attack, processID should always be localhost (i.e. 127.0.0.1) since this is a simulation on our own machines.