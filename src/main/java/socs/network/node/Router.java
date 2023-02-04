package socs.network.node;

import socs.network.util.Configuration;

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
    lsd = new LinkStateDatabase(rd);
  }

  // helper functions

  static void close(ObjectInputStream in, ObjectOutputStream out, Socket socket) throws IOException {
    if ( in != null ) {
      in.close();
    }
    if ( out != null ) {
      out.close();
    }
    socket.close();
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

  //TODO
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
      if ( ports[i] != null ) {
        freePort = i;
        break;
      }
    }
    
    // case 3: no free ports
    if ( freePort == -1 ) { 
      System.err.println("attach denied: no ports available");
      return;
    } 

    // try to establish new link between processIP <-> simulatedIP

    try {
      Socket socket = new Socket(processIP, processPort);
      ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
      ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

      // check connection
      out.writeObject(processIP + " is attempting attach with " + simulatedIP + ". do you accept this request? (Y/N)");

      try {
        // if server accepts attachment, add link to ports
        String input = (String) in.readObject();
        // case 4: attachment is established
        if ( input.equalsIgnoreCase("Y") ) {
          ports[freePort] = new Link(rd, remote, weight);
          close(in, out, socket);
        } else {
          System.err.println("attach response error: " + in);
        }
      } catch ( IOException e ) {
        close(in, out, socket);
        throw new RuntimeException(e);
      } catch ( ClassNotFoundException e ) {
        close(in, out, socket);
        throw new RuntimeException(e);
      }
      // case 5: invalid IP
    } catch ( UnknownHostException e ) {
      System.err.println("attach error: unknown host exception");
    } catch ( IOException e ) {
      System.err.println("attach error: no i/o for the connection");
      // case 6: invalid port
    } catch ( IllegalArgumentException e ) {
      System.err.println("attach error: port number not in allowed range");
    }
  }

  /** NOT NEEDED ANYMORE
   * process request from the remote router. 
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request. 
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   *
   * private void requestHandler() {
   * 
   * }
   */

  //TODO
  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {

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
    // check if all ports are empty
    boolean empty = true;
    for ( Link port : ports ) {
      if ( port != null ) {
          empty = false;
      }
    } 
    
    if ( empty ) {
        System.err.println("Ports are empty. No neighbors.");
    } else {
        for ( int i = 0; i < ports.length; i++ ) {
            // if there's a connection on both sides, then they are neighbors (two ways)
            if ( ports[i] != null && ports[i].router2.status != null ) {
                System.out.println("IP Address of neighbor " + (i + 1) + ": " + ports[i].router2.simulatedIPAddress);
            }
        }
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
      e.printStackTrace();
    }
  }

}
