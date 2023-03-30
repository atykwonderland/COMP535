package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;
import socs.network.util.MismatchedLinkException;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;

public class Router {

  protected LinkStateDatabase lsd;
  protected Boolean started = false;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  public Router(Configuration config) {
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    rd.processPortNumber = config.getShort("socs.network.router.portNumber");
    rd.processIPAddress = "127.0.0.1";

    // Start LSD
    lsd = new LinkStateDatabase(rd);

    // Start server side
    Thread serverSim = new Thread(new ServerSimulator(this, rd.processPortNumber));
    serverSim.start();
  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip address of the destination simulated router
   */
  private void processDetect(String destinationIP) {
    String path = this.lsd.getShortestPath(destinationIP);
    System.out.println(path);
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
  private SOSPFPacket createLSAPacket(Link link, short pType, LSA lsa) {
    SOSPFPacket p = new SOSPFPacket(
      rd.processIPAddress,
      rd.processPortNumber,
      rd.simulatedIPAddress,
      link.router2.simulatedIPAddress,
      pType,
      rd.simulatedIPAddress,
      rd.simulatedIPAddress,
      lsa
    );
    return p;
  }

  /*
   * updates the current LSD: creates new LSAs, forwards received LSAs
   */
  void broadcastLSAUPDATE(SOSPFPacket toForward) {
    if ( toForward == null ) {
      // make new LSA
      LSA L = new LSA();
      L.linkStateID = this.rd.simulatedIPAddress;

      //init seqNumber to 0
      if (lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber == Integer.MIN_VALUE) {
          L.lsaSeqNumber = 0;
      //else increment seqNumber
      } else {
          int seq = lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber;
          L.lsaSeqNumber = seq + 1;
      }

      // get links from ports array to initialize the linkedlist of LSA
      LinkedList<LinkDescription> tempLinks = new LinkedList<LinkDescription>();
      for (int i=0; i<ports.length; i++) {
        if (ports[i] != null && ports[i].router2.status != null) {
          // create new LinkDescription to add to links
          LinkDescription description = new LinkDescription();
          description.linkID = ports[i].router2.simulatedIPAddress;
          description.portNum = ports[i].router2.processPortNumber;
          description.tosMetrics = ports[i].weight;
          tempLinks.add(description);
        }
        L.links = tempLinks;
      }

      // update LSD
      lsd._store.put(L.linkStateID, L);

      // send LSAUPDATE to all neighbors
      for (int i = 0; i < ports.length; i++) {
        if (ports[i] != null) {
          try {
            Socket client = new Socket(ports[i].router2.processIPAddress, ports[i].router2.processPortNumber);
            ObjectOutputStream outToServer = new ObjectOutputStream(client.getOutputStream());
            SOSPFPacket LSAUPDATE = createLSAPacket(ports[i], (short) 1, L);
            outToServer.writeObject(LSAUPDATE);

            //clean
            client.close();
            outToServer.close();
          } catch (UnknownHostException e) {
            System.err.println("Error: Socket could not be created. IP address of host could not be found.");
            return;
          } catch (IOException e) {
            System.err.println("Error: I/O error occured during socket creation. Stream headers could not be written.");
            return;
          } 
        }
      }
    } else {
      // forward packet to all neighbors
      for (int i = 0; i < ports.length; i++) {
        if (ports[i] != null) {
          try {
            Socket client = new Socket(ports[i].router2.processIPAddress, ports[i].router2.processPortNumber);
            ObjectOutputStream outToServer = new ObjectOutputStream(client.getOutputStream());
            outToServer.writeObject(toForward);

            //clean
            client.close();
            outToServer.close();
          } catch (UnknownHostException e) {
            System.err.println("Error: Socket could not be create. IP address of host could not be found.");
            return;
          } catch (IOException e) {
            System.err.println("Error: I/O error occured during socket creation. Stream headers could not be written.");
            return;
          } 
        }
      }
    }
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

    started = true;

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

        //broadcast LSAUPDATE to neighbors
        broadcastLSAUPDATE(null);

        // Close streams and socket
        client.close();
        outToServer.close();
        inFromServer.close();

      } catch (UnknownHostException e) {
        System.err.println("Error: Socket could not be created. IP address of host could not be found.");
        return;
      } catch (IOException e) {
        System.err.println("Error: I/O error occured during socket creation. Stream headers could not be written.");
        return;
      } 
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
    if (started) {
      System.err.println("Error: Router has not started the process. Please start the router using the command \"start\".");
      return;
    }
    
    // Attach the remote router to the current router
    processAttach(processIP, processPort, simulatedIP, weight);

    // Get the index in ports of the previously attached remote router
    int index = -1;
    for (int i=0; i<4; i++) {
      if (ports[i].router2.simulatedIPAddress.equals(simulatedIP)) {
        index = i;
      }
    }

    // Start the connection and set to TWO_WAY
    Socket client;
    SOSPFPacket clientPacket;
    SOSPFPacket serverPacket = null;
    ObjectOutputStream outToServer;
    ObjectInputStream inFromServer;

    String serverName = ports[index].router2.processIPAddress;
    short port = ports[index].router2.processPortNumber;
    
    try {
      // initialize the CONNECT packet for the remote router
      clientPacket = createPacket(ports[index], (short) 2);
      
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

      // Check that response is a CONNECT packet
      if (serverPacket != null && serverPacket.sospfType == 2) {
        // If CONNECT received, set status of R2 as TWO_WAY
        ports[index].router2.status = RouterStatus.TWO_WAY;

        // Respond with CONNECT packet for server to set state to TWO_WAY as well
        outToServer.writeObject(clientPacket);
      } else {
        System.err.println("Error: Connection was unsuccessfull!");
        client.close();
        outToServer.close();
        inFromServer.close();
        return;
      }

      //broadcast LSAUPDATE to neighbors
      broadcastLSAUPDATE(null);

      // Close streams and socket
      client.close();
      outToServer.close();
      inFromServer.close();

    } catch (UnknownHostException e) {
      System.err.println("Error: Socket could not be created. IP address of host could not be found.");
      return;
    } catch (IOException e) {
      System.err.println("Error: I/O error occured during socket creation. Stream headers could not be written.");
      return;
    } 

  }

/**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {
    //is port number valid AND not null AND two-way 
    if (portNumber < 0 || 
        portNumber > 3 || 
        ports[portNumber] == null || 
        ports[portNumber].router2.status != RouterStatus.TWO_WAY) {

      System.err.println("Invalid port error.");
      return;
    }

    RouterDescription remote = ports[portNumber].router2;

    // try to delete links
    boolean deleted = true;
    // connect with router
    try {
      // send disconnect request packet
      Socket client = new Socket(remote.processIPAddress, remote.processPortNumber);
      ObjectOutputStream outToServer = new ObjectOutputStream(client.getOutputStream());
      ObjectInputStream inFromServer = new ObjectInputStream(client.getInputStream());
      SOSPFPacket disconnectRequest = createPacket(ports[portNumber], (short) 3);
      outToServer.writeObject(disconnectRequest);

      // see if response is also disconnect request packet
      SOSPFPacket serverPacket = (SOSPFPacket) inFromServer.readObject();
      if (serverPacket.sospfType == 3) {
        deleted = true;
      } else {
        deleted =  false;
      }
      // Close streams and socket
      client.close();
      outToServer.close();
      inFromServer.close();
    } catch (ClassNotFoundException e){
      System.err.println("Error: couldn't read message from server");
    } catch (UnknownHostException e) {
      System.err.println("Error: Socket could not be created. IP address of host could not be found.");
    } catch (IOException e) {
      System.err.println("Error: I/O error occured during socket creation. Stream headers could not be written.");
    } 

    // Broadcast disconnect update
    if (deleted) {
        ports[portNumber] = null;
        broadcastLSAUPDATE(null);
    } else {
        System.err.println("Error: couldn't broadcast disconnect.");
    }
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
    // Disconnect all connected routers
    for (Link port : this.ports) {
      if (port != null && port.router1.status == RouterStatus.TWO_WAY) {
        processDisconnect((short) 0);
      }
    }
    System.exit(0);
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
