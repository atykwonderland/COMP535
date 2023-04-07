# Simulate Link State Routing Protocol with Java Socket Programming

A pure user-space program which simulates the major functionalities of a routing device running a simplified Link State Routing protocol.

## How To Run:

```bash
$mvn compile assembly:single
$java -cp target/COMP535-1.0-SNAPSHOT-jar-with-dependencies.jar socs.network.Main conf/router1.conf

```

## Supported Commands:

| Command   | Description                                                                                                                                                                                                                                                |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| attach processIP processPort simulatedIP weight   | <ul><li> attach the link to the remote router, identified by the given simulated ip; </li><li> weight is the cost to transmitting data through the link</li></ul> **NOTE1**: processIP should always be localhost (i.e. 127.0.0.1) since this is a simulation on our own machines. </br>**NOTE2**: processPort must match the corresponding port as listed in the conf files.|
| start     | Client side: broadcast HELLO to all links <ul><li>through a socket connection, send HELLO packets to all linked routers;</li><li>waits for response from each link;</li><li>sets status of the linked router to TWO_WAY and sends another HELLO </li><li>neighbor relationship is established</li></ul> Server side: waits for packets to arrive <ul><li>receives a HELLO packet and sets router state to INIT</li><li>returns a HELLO packet to the source IP address</li><li>neighbor relationship established</li></ul>                                                                                                                                                                                                                          |
| neighbors | output the neighbors of the routers                                                                                                                                                                                                                        |
| detect simulatedIP | output the shortest path from current router to the target router with their weights                                                                                                                                                                                                                        |
| connect processIP processPort simulatedIP weight | similar to attach command, but directly trigger the database synchronization without the necessity to run start on the origin router </br>**NOTE**: destination router must still run start before it can be detected. |
| disconnect port | remove the link between this router and the remote one which is connected at port |