package socs.network.message;

import java.io.*;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

public class SOSPFPacket implements Serializable {

  //for inter-process communication
  public String srcProcessIP;
  public short srcProcessPort;

  //simulated IP address
  public String srcIP;
  public String dstIP;

  //common header
  public short sospfType; //0 - HELLO, 1 - LinkState Update, 2 - Connect, 3 - Disconnect, 4 - Update Weight
  public String routerID;

  //used by HELLO message to identify the sender of the message
  //e.g. when router A sends HELLO to its neighbor, it has to fill this field with its own
  //simulated IP address
  public String neighborID; //neighbor's simulated IP address

  //used by LSAUPDATE
  public Vector<LSA> lsaArray = null;

  //used by CONNECT
  public int weight;

  public SOSPFPacket(String srcProcessIP, short srcProcessPort, String srcIP, String dstIP, short sospfType, String routerID, String neighborID) {
    this.srcProcessIP = srcProcessIP;
    this.srcProcessPort = srcProcessPort;
    this.srcIP = srcIP;
    this.dstIP = dstIP;
    this.sospfType = sospfType;
    this.routerID = routerID;
    this.neighborID = neighborID;
  }

  public SOSPFPacket(String srcProcessIP, short srcProcessPort, String srcIP, String dstIP, short sospfType, String routerID, String neighborID, LSA lsa) {
    this.srcProcessIP = srcProcessIP;
    this.srcProcessPort = srcProcessPort;
    this.srcIP = srcIP;
    this.dstIP = dstIP;
    this.sospfType = sospfType;
    this.routerID = routerID;
    this.neighborID = neighborID;
     
    Vector<LSA> L = new Vector<LSA>();
    L.add(lsa);
    this.lsaArray = L;
  }

  public SOSPFPacket(String srcProcessIP, short srcProcessPort, String srcIP, String dstIP, short sospfType, String routerID, String neighborID, int weight) {
    this.srcProcessIP = srcProcessIP;
    this.srcProcessPort = srcProcessPort;
    this.srcIP = srcIP;
    this.dstIP = dstIP;
    this.sospfType = sospfType;
    this.routerID = routerID;
    this.neighborID = neighborID;
    this.weight = weight;
  }
}
