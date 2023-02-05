package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    lsd = new LinkStateDatabase(rd);
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort,
                             String simulatedIP, short weight) {

  }


  /**
   * process request from the remote router. 
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request. 
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   */
  private void requestHandler() {

  }

  /**
   * create packet to be broadcast
   */
  private SOSPFPacket createPacket(Link link, short pType) {
    SOSPFPacket p = new SOSPFPacket();
    p.srcProcessIP = rd.processIPAddress;
    p.srcProcessPort = rd.processPortNumber;
    p.srcIP = rd.simulatedIPAddress;
    p.dstIP = link.router2.simulatedIPAddress;
    p.sospfType = pType;
    p.routerID = rd.simulatedIPAddress;
    p.neighborID = rd.simulatedIPAddress;

    // public Vector<LSA> lsaArray = null; //
    // TODO: how to populalte p.lsaArray

    return p;
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    Socket client;
    SOSPFPacket clientPacket;
    SOSPFPacket serverPacket;
    ObjectOutputStream outToServer;
    ObjectInputStream inFromServer;
    boolean isEmpty = true;

    // Ensure there exists a connection for this router
    for (Link p : ports) {
      if (p != null) {
        isEmpty = false;
        break;
      }
    }
    if (isEmpty) {
      System.err.println("Warning: No routers connected to current router " + rd.simulatedIPAddress + ".");
    } 

    // Send HELLO to every connected router
    for (int i=0; i<ports.length; i++) {
      
      // Check that the ports element is not empty, otherwise skip to next array element
      if (ports[i] == null) {
        continue;
      }

      String serverName = ports[i].router2.processIPAddress;
      short port = ports[i].router2.processPortNumber;
      RouterStatus R2Status = ports[i].router2.status;

      // Check that the ports element is not empty, otherwise skip to next array element
      if (ports[i] == null) {
        continue;
      }

      // TODO add any other checks to the specific neighbor


      
      try {
        // initialize the packet to broadcast to neighbors
        clientPacket = createPacket(ports[i], (short) 0);
        
        // send packet to server and wait for response
        client = new Socket(serverName, port);

        outToServer = new ObjectOutputStream(client.getOutputStream());
        outToServer.writeObject(clientPacket);

        inFromServer = new ObjectInputStream(client.getInputStream());
        serverPacket = (SOSPFPacket) inFromServer.readObject();

        if (serverPacket != null) {
          System.out.println("received HELLO from " + serverPacket.srcIP + ";");
        }

        // Check that response is a HELLO
        if (serverPacket.sospfType == 0) {
          // If HELLO received, set status of R2 as TWO_WAY
          R2Status = RouterStatus.TWO_WAY;
          System.out.println("set " + serverName + " STATE to TWO_WAY");
        }

      } catch (UnknownHostException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      // initialize database sychronization process LSAUPDATE


    }

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {

  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  /**
   * update the weight of an attached link
   */
  private void updateWeight(String processIP, short processPort,
                             String simulatedIP, short weight){

  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
        } else if (command.startsWith("quit")) {
          processQuit();
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("start")) {
          processStart();
        } else if (command.equals("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
        } else {
          //invalid command
          break;
        }
        System.out.print(">> ");
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
