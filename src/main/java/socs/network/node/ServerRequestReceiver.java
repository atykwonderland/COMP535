package socs.network.node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

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
                        isLinked = true;
                        System.out.println("set " + packetReceived.srcIP + " state to INIT;");
                        break;
                    }
                }
                if (!isLinked) {
                    // If there are no links (i.e. ports array empty or no matchin simulatedIPAddress), throw exception
                    outToClient.writeObject("MismatchedLinkException");
                    throw new MismatchedLinkException("packet received from " + packetReceived.srcIP + " is not linked to this router. No further actions.");
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
                    System.out.println("set " + packetReceived.srcIP + " STATE set to TWO_WAY;");
                }

                inFromClient.close();
                outToClient.close();
                lSocket.close();

                System.out.print(">> ");
            }
        } catch (IOException e) {
            System.out.println(e.toString());
            System.out.print(">> ");
        } catch (ClassNotFoundException e) {
            System.out.println(e.toString());
            System.out.print(">> ");
        } catch (MismatchedLinkException e) {
            System.out.println(e.toString());
            System.out.print(">> ");
        }
        
    }


}
