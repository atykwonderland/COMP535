package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  private static LSA getLowestDistanceLSA(Set <LSA> unsettledLSAs, Map <LSA, Integer> distanceToSource) {
    LSA lowestDistanceLSA = null;
    int lowestDistance = Integer.MAX_VALUE;
    for (LSA lsa: unsettledLSAs) {
      int dist = (distanceToSource.get(lsa) != null) ? distanceToSource.get(lsa) : Integer.MAX_VALUE;
      if (dist < lowestDistance) {
        lowestDistance = dist;
        lowestDistanceLSA = lsa;
      }
    }
    return lowestDistanceLSA;
  }

  private static void calculateMinimumDistance(LSA currentLSA, LSA neighbour, Map<LSA,Integer> distanceToSource) {
    Integer neighbourDistance = (distanceToSource.get(neighbour) != null) ? distanceToSource.get(neighbour) : Integer.MAX_VALUE;
    Integer currentLSADistance = (distanceToSource.get(currentLSA) != null) ? distanceToSource.get(currentLSA) : Integer.MAX_VALUE;
    Integer edgeWeight = -1;
    // find the edge weight from current node to its neighbour
    for (LinkDescription l: currentLSA.links) {
      if (l.linkID.equals(neighbour.linkStateID)) {
        edgeWeight = l.tosMetrics;
      }
    }

    // TODO: return error
    if (edgeWeight < 0) {
      return;
    }

    // if the distance to source of the neighbour previously recorded (or not recorded, meaning Integer.MAX_VALUE) is greater than 
    // the distance of the path with the currentLSA, then update
    if (currentLSADistance + edgeWeight < neighbourDistance) {
      distanceToSource.put(neighbour, currentLSADistance + edgeWeight);
    }
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  String getShortestPath(String destinationIP) {
    
     // build the weighted graph
     Set<LSA> unsettledLSAs = new HashSet<LSA>();
     Set<LSA> settledLSAs = new HashSet<LSA>();
 
     // store neighbours and their distances of the source node
     Map<LSA, Integer> distanceToSource = new HashMap<LSA, Integer>();    // <node, weight>
 
     // add the source node
     LSA source = _store.get(rd.simulatedIPAddress);
     unsettledLSAs.add(source);
     distanceToSource.put(source, 0);
 
     // keep looping while there are still unsettled nodes
     while (unsettledLSAs.size() != 0) {
       LSA currentLSA = getLowestDistanceLSA(unsettledLSAs, distanceToSource);
       unsettledLSAs.remove(currentLSA);
       // get neighbours of the node
       LinkedList<LSA> adjacentLSAs = new LinkedList<LSA>();
       for (LinkDescription l: currentLSA.links) {
        adjacentLSAs.add(_store.get(l.linkID));
       }
       
       // iterate through each neighbor, and update the distance to the shortest from source for each
       // add to unsettled set if node has not been settled yet
       for (LSA neighbour: adjacentLSAs) {
        // if neighbour linkID is null, skip to next
        if (neighbour == null) continue;

        // if neighbour is already in settled list, then skip
        if (!settledLSAs.contains(neighbour)) {
          calculateMinimumDistance(currentLSA, neighbour, distanceToSource);
          unsettledLSAs.add(neighbour);
        }
       }  
       settledLSAs.add(currentLSA);
     }
    

    return null;
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    lsa.links.add(ld);
    return lsa;
  }


  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
