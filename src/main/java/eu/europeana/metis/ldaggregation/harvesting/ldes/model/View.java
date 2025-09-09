/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;

import eu.europeana.metis.ldaggregation.harvesting.ldes.LdesConstants;
import eu.europeana.metis.ldaggregation.harvesting.ldes.NodeRelations;
import eu.europeana.metis.ldaggregation.harvesting.ldes.ValidationException;

/**
 * @author Nuno Freire
 * @since 23/05/2025
 */
public class View {
  Resource viewRs;
  
  /**
   * @param viewRs
   */
  public View(Resource viewRs) {
    this.viewRs = viewRs;
  }

  /**
   * @return
   */
  protected List<Relation> getRelations() {
    List<Relation> rels=new ArrayList<>();
    StmtIterator relsSts = viewRs.listProperties(LdesConstants.TREE_RELATION);
    if(relsSts!=null && relsSts.hasNext()) 
      relsSts.forEach(st-> rels.add(new Relation(st.getObject().asResource())));
    return rels;
  }

  /**
   * @return
   */
  public Collection<NodeRelations> getNodeRelations() {
    Map<String, NodeRelations> relationsByNode=new HashMap<>();
    getRelations().forEach(r -> {
      try {
        NodeRelations nodeRelations = relationsByNode.get(r.getNodeUrl());
        if( nodeRelations==null) {
          nodeRelations=new NodeRelations(r.getNodeUrl());
          relationsByNode.put(r.getNodeUrl(), nodeRelations);
        }
        nodeRelations.add(r);
      } catch (ValidationException e) {
//        log.warn(e.getMessage(), e);
        //ignore this relation
      }
    });
    return relationsByNode.values();
  }
  
  

}
