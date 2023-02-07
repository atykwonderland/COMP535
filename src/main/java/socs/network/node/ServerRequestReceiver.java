package socs.network.node;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

import socs.network.message.SOSPFPacket;

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
            SOSPFPacket packetReceived;

            packetReceived = (SOSPFPacket) inFromClient.readObject();

            if (packetReceived.sospfType == 0) {
                System.out.println("received HELLO from " + packetReceived.srcIP + ";");

                // Change the status of the link to INIT
                boolean isLinked = false;
                for (int i=0; i<4; i++) {
                    Link link = null;
                    // System.out.println(router.ports[i]);
                    // System.out.println(router.ports[i].router2);
                    if (router.ports[i] == null) {
                        continue;
                    } else if (router.ports[i].router2.simulatedIPAddress.equals(packetReceived.srcIP)) {
                        link = router.ports[i];
                        link.router2.status = RouterStatus.INIT;
                        isLinked = true;
                        System.out.println("set " + packetReceived.srcIP + " state to INIT;");
                        break;
                    } else {
                        System.out.println(router.ports[i].router2.simulatedIPAddress);
                        System.out.println(packetReceived.srcIP);
                    }
                }
                if (!isLinked) {
                    System.err.println("packet received from " + packetReceived.srcIP + " is not linked to this router. No further actions.");
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        
    }


}
