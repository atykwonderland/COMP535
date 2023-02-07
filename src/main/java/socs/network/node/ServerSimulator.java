package socs.network.node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

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
            e.printStackTrace();
        }
    }

    public void run() {

        while (true) {
            try {
                Socket lSocket = socket.accept();
                Thread requestReceiver = new Thread(new ServerRequestReceiver(lSocket, router));
                requestReceiver.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        }
    }
    
}
