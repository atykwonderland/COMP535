package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class LinkStateDatabase {

  // linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * Returns the LSA object from the set of unsettled LSAs that has the smallest
   * distance from the source.
   * 
   * @param unsettledLSAs    a set of unsettled nodes for the Dijkstra's algorithm
   * @param distanceToSource contains LSAs and their distance to the source LSA
   * @return the LSA with the lowest distance
   */
  private static LSA getLowestDistanceLSA(Set<LSA> unsettledLSAs, Map<LSA, Integer> distanceToSource) {
    LSA lowestDistanceLSA = null;
    for (LSA lsa : unsettledLSAs) {
      int dist = (distanceToSource.get(lsa) != null) ? distanceToSource.get(lsa) : Integer.MAX_VALUE;
      int lowestDistance = (distanceToSource.get(lowestDistanceLSA) != null) ? distanceToSource.get(lowestDistanceLSA) : Integer.MAX_VALUE;
      if (dist < lowestDistance) {
        lowestDistanceLSA = lsa;
      }
    }
    return lowestDistanceLSA;
  }

  /**
   * Adds or updates the shortest distance for the neighbour if the path
   * containing the current target LSA
   * is shorter.
   * 
   * @param currentLSA       current target node that is being evaluated
   * @param neighbour        one of the links of the current LSA
   * @param distanceToSource contains LSAs and their distance to the source LSA
   */
  private static void calculateMinimumDistance(LSA currentLSA, LSA neighbour, Map<LSA, Integer> distanceToSource,
      Map<LSA, LSA> shortestEdges) {
    Integer neighbourDistance = (distanceToSource.get(neighbour) != null) ? distanceToSource.get(neighbour)
        : Integer.MAX_VALUE;
    Integer currentLSADistance = (distanceToSource.get(currentLSA) != null) ? distanceToSource.get(currentLSA)
        : Integer.MAX_VALUE;
    Integer edgeWeight = -1;
    // find the edge weight from current node to its neighbour
    for (LinkDescription l : currentLSA.links) {
      if (l.linkID.equals(neighbour.linkStateID)) {
        edgeWeight = l.tosMetrics;
      }
    }

    if (edgeWeight < 0 || edgeWeight == null) {
      System.err.println("Error: Cannot get shortest path. Edge weight is not valid.");
      return;
    }

    // if the distance to source of the neighbour previously recorded (or not
    // recorded, meaning Integer.MAX_VALUE) is greater than
    // the distance of the path with the currentLSA, then update
    if (currentLSADistance + edgeWeight < neighbourDistance) {

      // update distance of neighbour node
      distanceToSource.put(neighbour, currentLSADistance + edgeWeight);

      // update smallest weight edge from current node to neighbour node
      shortestEdges.put(neighbour, currentLSA);
    }
  }

  /**
   * Converts the given path of LSAs to a string.
   * 
   * @param path a linked list of LSAs
   * @return the path as a string in the required formats
   */
  private static String convertPathToString(LinkedList<LSA> path) {
    String stringPath = "";
    int weight = -1;
    ListIterator<LSA> pathIter = path.listIterator(0);

    while (pathIter.hasNext()) {
      LSA currentLSA = pathIter.next();
      stringPath = stringPath + currentLSA.linkStateID;

      if (pathIter.hasNext()) {
        stringPath = stringPath + " ->";
        int nextIndex = pathIter.nextIndex();
        String nextLSA = path.get(nextIndex).linkStateID;

        for (LinkDescription l : currentLSA.links) {
          if (l.linkID.equals(nextLSA)) {
            weight = l.tosMetrics;
          }
        }

        stringPath = stringPath + "(" + weight + ") ";
      }
    }
    return stringPath;
  }

  /**
   * output the shortest path from this router to the destination with the given
   * IP address
   */
  String getShortestPath(String destinationIP) {

    // build the weighted graph
    Set<LSA> unsettledLSAs = new HashSet<LSA>();
    Set<LSA> settledLSAs = new HashSet<LSA>();

    // store neighbours and their distances of the source node
    Map<LSA, Integer> distanceToSource = new HashMap<LSA, Integer>(); // <node, weight>
    LinkedList<LSA> shortestPath = new LinkedList<LSA>();
    Map<LSA, LSA> shortestEdges = new HashMap<LSA, LSA>(); // <end node, start node> direction of edge "<--"

    // add the source node
    LSA source = _store.get(rd.simulatedIPAddress);
    unsettledLSAs.add(source);
    distanceToSource.put(source, 0);

    // keep looping while there are still unsettled nodes
    while (unsettledLSAs.size() > 0) {
      LSA currentLSA = getLowestDistanceLSA(unsettledLSAs, distanceToSource);
      unsettledLSAs.remove(currentLSA);

      // get neighbours of the node
      LinkedList<LSA> adjacentLSAs = new LinkedList<LSA>();

      for (LinkDescription l : currentLSA.links) {
        if (l.linkID != null) {
          adjacentLSAs.add(_store.get(l.linkID));
        }
      }

      // iterate through each neighbor, and update the distance to the shortest from
      // source for each
      // add to unsettled set if node has not been settled yet
      for (LSA neighbour : adjacentLSAs) {
        // if neighbour linkID is null, skip to next
        if (neighbour == null)
          continue;

        // if neighbour is already in settled list, then skip
        if (!settledLSAs.contains(neighbour)) {
          calculateMinimumDistance(currentLSA, neighbour, distanceToSource, shortestEdges);
          unsettledLSAs.add(neighbour);
        }
      }
      settledLSAs.add(currentLSA);
    }

    // Generate the shortest path
    LSA lastLSA = _store.get(destinationIP);

    // Starting from the destinationIP router, add the routers that lead up to the
    // current router
    if (shortestEdges.get(lastLSA) != null) {
      shortestPath.add(lastLSA);
      while (shortestEdges.get(lastLSA) != null) {
        lastLSA = shortestEdges.get(lastLSA);
        shortestPath.add(lastLSA);
      }
      Collections.reverse(shortestPath);
    } else {
      return "Warning: There is no path.";
    }
    // Convert the path to a string to return

    return convertPathToString(shortestPath);
  }

  // initialize the linkstate database by adding an entry about the router itself
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
    for (LSA lsa : _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
