/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import eu.europeana.metis.ldaggregation.harvesting.ldes.model.LdesNode;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Relation;

/**
 * @author Nuno Freire
 * @since 27/05/2025
 */
public class NodeRelations {
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

}
