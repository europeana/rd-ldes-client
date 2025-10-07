/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Relation;

/**
 * @author Nuno Freire
 * @since 27/05/2025
 */
public class NodeRelations implements Comparable<NodeRelations>{
  String nodeUrl;
  
  List<Relation> relations;
  
  /**
   * @param nodeUrl
   */
  public NodeRelations(String nodeUrl) {
    this.nodeUrl = nodeUrl;
    relations=new ArrayList<>();
  }
  
  public boolean mayBePrunned(Instant previousCrawlTs) {
    for(Relation rel: relations) { 
      if(!rel.mayBePrunned(previousCrawlTs)) 
        return false;
    }
    return true;
  }

  /**
   * @param r
   */
  public void add(Relation r) {
    relations.add(r);
  }

  /**
   * @return
   */
  public String getNodeUrl() {
    return nodeUrl;
  }

  @Override
  public int compareTo(NodeRelations o) {
    //comparing for ascending order
    Instant thisTs=getSortingTimestamp();    
    Instant otherTs=o.getSortingTimestamp();
    long diffMillis = ChronoUnit.MILLIS.between(otherTs, thisTs);
//    long diffMillis = ChronoUnit.MILLIS.between(thisTs, otherTs);
    if(diffMillis>Integer.MAX_VALUE)
      return Integer.MAX_VALUE;
    if(diffMillis<Integer.MIN_VALUE)
      return Integer.MIN_VALUE;
    return (int) diffMillis;    
  }

  /**
   * @return
   */
  private Instant getSortingTimestamp() {
    for(Relation rel: relations) { 
      switch (rel.getType()) {
      case EqualToRelation:
      case GreaterThanOrEqualToRelation:
      case GreaterThanRelation:
        return rel.getValueAsInstant();
      default:
        //not relevant for sorting
        break;
      }
    }
    return Instant.parse("1970-01-01");
  }
}
