package socs.network.node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedList;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.MismatchedLinkException;

/**
 * Given a socket connection and server router, requests and communication can be written or read using 
 * the streams
 */
public class ServerRequestReceiver implements Runnable {
    
    private Socket lSocket;
    private Router router;

    public ServerRequestReceiver(Socket s, Router r) {
        lSocket = s;
        router = r;
    }

    public void run() {
        // Initialize output and input stream to receive HELLO
        try {
            ObjectInputStream inFromClient = new ObjectInputStream(lSocket.getInputStream());
            ObjectOutputStream outToClient = new ObjectOutputStream(lSocket.getOutputStream());
            SOSPFPacket packetReceived;

            packetReceived = (SOSPFPacket) inFromClient.readObject();

            if (packetReceived.sospfType == 0) {
                System.out.println("received HELLO from " + packetReceived.srcIP + ";");

                // Change the status of the link to INIT
                boolean isLinked = false;
                Link link = null;
                for (int i=0; i<4; i++) {
                    if (router.ports[i] == null) {
                        continue;
                    } else if (router.ports[i].router2.simulatedIPAddress.equals(packetReceived.srcIP)) {
                        link = router.ports[i];
                        link.router2.status = RouterStatus.INIT;
                        link.router1.status = RouterStatus.INIT;
                        isLinked = true;
                        System.out.println("set " + packetReceived.srcIP + " STATE to INIT;");
                        break;
                    }
                }
                if (!isLinked) {
                    // If there are no links (i.e. ports array empty or no matchin simulatedIPAddress), throw exception
                    // outToClient.writeObject("MismatchedLinkException");
                    // throw new MismatchedLinkException("packet received from " + packetReceived.srcIP + " is not linked to this router. No further actions.");
                    int freePort = -1;
                    for (int i=0; i<4; i++) {
                        if (router.ports[i] == null) {
                            freePort = i;
                            break;
                        }
                    }
                    if (freePort == -1) {
                        outToClient.writeObject("MismatchedLinkException");
                        throw new MismatchedLinkException("No more free ports for " + packetReceived.srcIP);
                    } else {
                        RouterDescription r2 = new RouterDescription(packetReceived.srcProcessIP, packetReceived.srcProcessPort, packetReceived.srcIP);
                        router.ports[freePort] = new Link(router.rd, r2, packetReceived.weight);
                        // set init status again
                        link = router.ports[freePort];
                        link.router2.status = RouterStatus.INIT;
                        link.router1.status = RouterStatus.INIT;
                        System.out.println("set " + packetReceived.srcIP + " STATE to INIT;");
                    }
                }

                // Otherwise, create HELLO packet to set for TWO_WAY
                SOSPFPacket clientPacket = new SOSPFPacket(
                    router.rd.processIPAddress, 
                    router.rd.processPortNumber,
                    router.rd.simulatedIPAddress,
                    link.router2.simulatedIPAddress,
                    (short) 0, 
                    router.rd.simulatedIPAddress, 
                    packetReceived.srcIP
                );

                // Send return HELLO packet to client
                outToClient.writeObject(clientPacket);

                // Wait for the second HELLO packet from client
                packetReceived = (SOSPFPacket) inFromClient.readObject();
                if (packetReceived == null) {
                    System.err.println("Error: Packet is null.");
                    inFromClient.close();
                    outToClient.close();
                    lSocket.close();
                    return;
                } else if (packetReceived.sospfType == 0) {
                    // if HELLO packet received, set status to TWO_WAY
                    System.out.println("received HELLO from " + packetReceived.srcIP + ";");
                    link.router2.status = RouterStatus.TWO_WAY;
                    link.router1.status = RouterStatus.TWO_WAY;
                    System.out.println("set " + packetReceived.srcIP + " STATE set to TWO_WAY;");
                }

                inFromClient.close();
                outToClient.close();
                lSocket.close();

                System.out.print(">> ");
            
            // FOR LSD SYNCHRONIZATION/BROADCAST
            } else if (packetReceived.sospfType == 1) {
                LSA lsa = router.lsd._store.get(packetReceived.srcIP);
                
                // null means a new router, so it needs to broadcast itself
                if (lsa == null || packetReceived.lsaArray.lastElement().lsaSeqNumber > lsa.lsaSeqNumber) {
                    
                    // check for link
                    boolean isLinked = false;
                    Link link = null;
                    for (int i=0; i<4; i++) {
                        if (router.ports[i] == null) {
                            continue;
                        } else if (router.ports[i].router2.simulatedIPAddress.equals(packetReceived.srcIP)) {
                            link = router.ports[i];
                            isLinked = true;
                            break;
                        }
                    }
                    
                    // if linked, update link & LSA
                    if (isLinked) {
                        LinkedList<LinkDescription> links = packetReceived.lsaArray.lastElement().links;
                        LinkDescription ld = null;
                        for (LinkDescription l : links) {
                            if(l.linkID.equals(router.rd.simulatedIPAddress)) {
                                ld = l;
                                break;
                            }
                        }
                        
                        if (ld != null) {
                            // old weight => need to update
                            if (ld.tosMetrics > -1 && ld.tosMetrics != link.weight) {
                                link.weight = ld.tosMetrics;
                                LSA currLSA = router.lsd._store.get(router.rd.simulatedIPAddress);
                                // get the new link descriptions
                                LinkedList<LinkDescription> newLD = new LinkedList<LinkDescription>();
                                for (int i = 0; i < 4; i++) {
                                    if (router.ports[i] != null && router.ports[i].router2.status != null) {
                                        LinkDescription l = new LinkDescription();
                                        l.linkID = router.ports[i].router2.simulatedIPAddress;
                                        l.portNum = router.ports[i].router2.processPortNumber;
                                        l.tosMetrics = router.ports[i].weight;
                                        links.add(l);
                                    }
                                }
                                // update
                                currLSA.links = newLD;
                                // broadcast update
                                router.lsd._store.put(router.rd.simulatedIPAddress, currLSA);
                                router.broadcastLSAUPDATE(null);
                            }
                        }
                    }

                    router.lsd._store.put(packetReceived.srcIP, packetReceived.lsaArray.lastElement());
                
                    // forward packet to all neighbors
                    for (int i = 0; i < 4; i++) {
                        if (router.ports[i] == null) {
                            continue;
                        } else {
                            if (!router.ports[i].router2.simulatedIPAddress.equals(packetReceived.srcIP)) {
                                // forward packet
                                router.broadcastLSAUPDATE(packetReceived);
                                // broadcast yourself
                                if (lsa == null) {
                                    router.broadcastLSAUPDATE(null);
                                }
                            }
                        }
                    }
                }

            // FOR PROCESS CONNECT
            } else if (packetReceived.sospfType == 2) {

                // Change the status of the link to INIT
                boolean isLinked = false;
                Link link = null;
                for (int i=0; i<4; i++) {
                    if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(packetReceived.srcIP)) {
                        link = router.ports[i];
                        link.router2.status = RouterStatus.INIT;
                        link.router1.status = RouterStatus.INIT;
                        isLinked = true;
                        break;
                    }
                }
                if (!isLinked) {
                    int freePort = -1;
                    for (int i=0; i<4; i++) {
                        if (router.ports[i] == null) {
                            freePort = i;
                            break;
                        }
                    }
                    if (freePort == -1) {
                        outToClient.writeObject("MismatchedLinkException");
                        throw new MismatchedLinkException("No more free ports for " + packetReceived.srcIP);
                    } else {
                        RouterDescription r2 = new RouterDescription(packetReceived.srcProcessIP, packetReceived.srcProcessPort, packetReceived.srcIP);
                        router.ports[freePort] = new Link(router.rd, r2, packetReceived.weight);
                        // set init status again
                        link = router.ports[freePort];
                        link.router2.status = RouterStatus.INIT;
                        link.router1.status = RouterStatus.INIT;
                    }
                }

                // Otherwise, create CONNECT packet to set for TWO_WAY
                SOSPFPacket clientPacket = new SOSPFPacket(
                    router.rd.processIPAddress, 
                    router.rd.processPortNumber,
                    router.rd.simulatedIPAddress,
                    link.router2.simulatedIPAddress,
                    (short) 2, 
                    router.rd.simulatedIPAddress, 
                    packetReceived.srcIP
                );

                // Send return CONNECT packet to client
                outToClient.writeObject(clientPacket);

                // Wait for the second CONNECT packet from client
                packetReceived = (SOSPFPacket) inFromClient.readObject();
                if (packetReceived == null) {
                    System.err.println("Error: Packet is null.");
                    inFromClient.close();
                    outToClient.close();
                    lSocket.close();
                    return;
                } else if (packetReceived.sospfType == 2) {
                    // if CONNECT packet received, set status to TWO_WAY
                    link.router2.status = RouterStatus.TWO_WAY;
                    link.router1.status = RouterStatus.TWO_WAY;
                }

                inFromClient.close();
                outToClient.close();
                lSocket.close();

                System.out.print(">> ");

            // FOR PROCESS DISCONNECT
            } else if (packetReceived.sospfType == 3) {
                // check for link
                boolean isLinked = false;
                Link link = null;
                int port = -1;
                for (int i=0; i<4; i++) {
                    if (router.ports[i] == null) {
                        continue;
                    } else if (router.ports[i].router2.simulatedIPAddress.equals(packetReceived.srcIP)) {
                        link = router.ports[i];
                        isLinked = true;
                        port = i;
                        break;
                    }
                }
                if (!isLinked) {
                    // If there are no links (i.e. ports array empty or no matchin simulatedIPAddress), throw exception
                    outToClient.writeObject("MismatchedLinkException");
                    throw new MismatchedLinkException("packet received from " + packetReceived.srcIP + " is not linked to this router. No further actions.");
                }

                //create the response packet
                SOSPFPacket responsePacket = new SOSPFPacket(
                    router.rd.processIPAddress, 
                    router.rd.processPortNumber,
                    router.rd.simulatedIPAddress,
                    link.router2.simulatedIPAddress,
                    (short) 3, 
                    router.rd.simulatedIPAddress, 
                    packetReceived.srcIP
                );

                //send the response to the source so it can update it's link state database
                outToClient.writeObject(responsePacket);

                //proceed to update link state database
                router.ports[port] = null;
                router.broadcastLSAUPDATE(null);

                // FOR UPDATE WEIGHT
            } else if (packetReceived.sospfType == 4) {

                // Change the status of the link to INIT
                boolean isLinked = false;
                Link link = null;
                for (int i=0; i<4; i++) {
                    if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(packetReceived.srcIP)) {
                        link = router.ports[i];
                        link.router2.status = RouterStatus.INIT;
                        link.router1.status = RouterStatus.INIT;
                        isLinked = true;
                        break;
                    }
                }
                if (!isLinked) {
                    int freePort = -1;
                    for (int i=0; i<4; i++) {
                        if (router.ports[i] == null) {
                            freePort = i;
                            break;
                        }
                    }
                    if (freePort == -1) {
                        outToClient.writeObject("MismatchedLinkException");
                        throw new MismatchedLinkException("No more free ports for " + packetReceived.srcIP);
                    } else {
                        RouterDescription r2 = new RouterDescription(packetReceived.srcProcessIP, packetReceived.srcProcessPort, packetReceived.srcIP);
                        router.ports[freePort] = new Link(router.rd, r2, packetReceived.weight);
                        // set init status again
                        link = router.ports[freePort];
                        link.router2.status = RouterStatus.INIT;
                        link.router1.status = RouterStatus.INIT;
                    }
                }

                // Otherwise, create UPDATE packet to set for TWO_WAY
                SOSPFPacket clientPacket = new SOSPFPacket(
                    router.rd.processIPAddress, 
                    router.rd.processPortNumber,
                    router.rd.simulatedIPAddress,
                    link.router2.simulatedIPAddress,
                    (short) 4, 
                    router.rd.simulatedIPAddress, 
                    packetReceived.srcIP
                );

                // Send return UPDATE packet to client
                outToClient.writeObject(clientPacket);

                // Wait for the second UPDATE packet from client
                packetReceived = (SOSPFPacket) inFromClient.readObject();
                if (packetReceived == null) {
                    System.err.println("Error: Packet is null.");
                    inFromClient.close();
                    outToClient.close();
                    lSocket.close();
                    return;
                } else if (packetReceived.sospfType == 2) {
                    // if UPDATE packet received, set status to TWO_WAY
                    link.router2.status = RouterStatus.TWO_WAY;
                    link.router1.status = RouterStatus.TWO_WAY;
                }

                inFromClient.close();
                outToClient.close();
                lSocket.close();

                System.out.print(">> ");

            }
            inFromClient.close();
            outToClient.close();
            lSocket.close();
        } catch (IOException e) {
            // System.out.println(e.toString());
            // System.out.print(">> ");
        } catch (ClassNotFoundException e) {
            System.out.println(e.toString());
            System.out.print(">> ");
        } catch (MismatchedLinkException e) {
            System.out.println(e.toString());
            System.out.print(">> ");
        }
        
    }


}
