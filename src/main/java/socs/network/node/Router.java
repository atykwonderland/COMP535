package socs.network.node;

import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;
import socs.network.util.MismatchedLinkException;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    rd.processPortNumber = config.getShort("socs.network.router.portNumber");
    rd.processIPAddress = "127.0.0.1";
    lsd = new LinkStateDatabase(rd);

    // Start server socket
    Thread serverSim = new Thread(new ServerSimulator(this, rd.processPortNumber));
    serverSim.start();
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
  private void processAttach(String processIP, short processPort, String simulatedIP, short weight) {
    
    RouterDescription remote = new RouterDescription(processIP, processPort, simulatedIP);
    
    // case 1: processIP = simulatedIP
    if ( simulatedIP.equals(this.rd.processIPAddress) ) {
      System.err.println("attach denied: cannot attach to self");
      return;
    }

    // case 2: simulatedIP is already attached to a port
    for ( Link port : ports ) {
      if ( port != null && port.router2.simulatedIPAddress.equals(simulatedIP) ) {
        System.err.println("attach denied: already attached to router " + simulatedIP);
        return;
      }
    }

    int freePort = -1;
    for ( int i = 0; i < 4; i++ ) {
      if ( ports[i] == null ) {
        freePort = i;
        break;
      }
    }
    
    // case 3: no free ports
    if ( freePort == -1 ) { 
      System.err.println("attach denied: no ports available");
      return;
    } 

    // case 4: port number out of range (0 and 65535)
    if ( processPort < 0 || processPort > 65535 ) {
      System.err.println("attach denied: invalid port number");
      return;
    }

    // case 5: processIP <-> simulatedIP attached
    ports[freePort] = new Link(rd, remote, weight);
    System.out.println("attach accepted");
  }

  /**
   * helper: create packet to be broadcast
   */
  private SOSPFPacket createPacket(Link link, short pType) {
    SOSPFPacket p = new SOSPFPacket(
      rd.processIPAddress,
      rd.processPortNumber,
      rd.simulatedIPAddress,
      link.router2.simulatedIPAddress,
      pType,
      rd.simulatedIPAddress,
      rd.simulatedIPAddress
    );

    return p;
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() throws MismatchedLinkException {
    Socket client;
    SOSPFPacket clientPacket;
    SOSPFPacket serverPacket = null;
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
      return;
    } 

    // Send HELLO to every connected router
    for (int i=0; i<ports.length; i++) {
      
      // Check that the ports element is not empty, otherwise skip to next array element
      if (ports[i] == null) {
        continue;
      }

      String serverName = ports[i].router2.processIPAddress;
      short port = ports[i].router2.processPortNumber;

      // TODO add any other checks to the specific neighbor
      
      try {
        // initialize the HELLO packet to broadcast to neighbors
        clientPacket = createPacket(ports[i], (short) 0);
        
        // send packet to server and wait for response
        client = new Socket(serverName, port);

        outToServer = new ObjectOutputStream(client.getOutputStream());
        outToServer.writeObject(clientPacket);

        inFromServer = new ObjectInputStream(client.getInputStream());
        try {
          serverPacket = (SOSPFPacket) inFromServer.readObject();
        } catch (ClassNotFoundException e) {
          System.err.println("Packet received is not correct or cannot be used.");
          client.close();
          outToServer.close();
          inFromServer.close();
          return;
        } catch (ClassCastException e) {
          System.err.println("Unexpected packet type.");
          client.close();
          outToServer.close();
          inFromServer.close();
          return;
        }

        // Check that response is a HELLO
        if (serverPacket != null && serverPacket.sospfType == 0) {
          System.out.println("received HELLO from " + serverPacket.srcIP + ";");
          // If HELLO received, set status of R2 as TWO_WAY
          ports[i].router2.status = RouterStatus.TWO_WAY;
          System.out.println("set " + serverName + " STATE to TWO_WAY");

          // Respond with HELLO packet for server to set state to TWO_WAY as well
          outToServer.writeObject(clientPacket);
        } else {
          System.out.println("HELLO packet not returned. STATE unchanged.");
          client.close();
          outToServer.close();
          inFromServer.close();
          return;
        }

        // Close streams and socket
        client.close();
        outToServer.close();
        inFromServer.close();

      } catch (UnknownHostException e) {
        System.err.println("Error: Socket could not be create. IP address of host could not be found.");
        return;
      } catch (IOException e) {
        System.err.println("Error: I/O error occured during socket creation. Stream headers could not be written.");
        return;
      } 

      // TODO initialize database sychronization process LSAUPDATE

    }

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort, String simulatedIP, short weight) {

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    int oneWay = 0, twoWay = 0;    
    
    // check if all ports are empty for current router
    boolean empty = true;
    for ( Link port : ports ) {
      if ( port != null ) {
          empty = false;
          oneWay += 1;
      }
    } 
    
    // case 1: all ports of processIP are empty
    if ( empty ) {
        System.err.println("Ports are empty. No neighbors.");
    } else {
        for ( int i = 0; i < ports.length; i++ ) {
            
          // case 2: if there's a connection on both sides, then they are neighbors (two ways)
            if ( ports[i] != null && ports[i].router2.status != null ) {
                System.out.println("IP Address of neighbor " + (i + 1) + ": " + ports[i].router2.simulatedIPAddress);
                twoWay += 1;
            }
        }
    }
    
    // case 3: only one way attaches => not neighbors
    if ( twoWay == 0 && oneWay > 0 ) {
      System.err.println("No neighbors.");
    }
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  /**
   * update the weight of an attached link
   */
  private void updateWeight(String processIP, short processPort, String simulatedIP, short weight){

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
      System.out.println(e.toString());
    }
  }

}
