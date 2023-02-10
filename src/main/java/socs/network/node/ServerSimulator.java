package socs.network.node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Starts the server application of the router to be able to continuously accept clients and 
 * read incoming packets
 */
public class ServerSimulator implements Runnable {

    private Router router;
    private ServerSocket socket;
    private short port;

    public ServerSimulator(Router r, short p) {
        router = r;
        port = p;
        try {
            socket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    public void run() {

        // Every incoming client connection can run concurrently using the same socket
        while (true) {
            try {
                // Accepts client socket connection
                Socket lSocket = socket.accept();
                Thread requestReceiver = new Thread(new ServerRequestReceiver(lSocket, router));
                requestReceiver.start();
            } catch (IOException e) {
                System.out.println(e.toString());
                System.err.println("Error: server socket connection failure.");
            }
            
        }
    }
    
}
