/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import eu.europeana.metis.ldaggregation.harvesting.ldes.LdesConstants;

/**
 * @author Nuno Freire
 * @since 26/05/2025
 */
public enum RelationType {
  GreaterThanRelation(LdesConstants.TREE_NS+"GreaterThanRelation"), 
  GreaterThanOrEqualToRelation(LdesConstants.TREE_NS+"GreaterThanOrEqualToRelation"),
  LessThanRelation(LdesConstants.TREE_NS+"LessThanRelation"),
  LessThanOrEqualToRelation(LdesConstants.TREE_NS+"LessThanOrEqualToRelation"),
  EqualToRelation(LdesConstants.TREE_NS+"EqualToRelation"),
  NotEqualToRelation(LdesConstants.TREE_NS+"NotEqualToRelation");

  private String uri;
  
  private RelationType(String uri) {
    this.uri = uri;
  }

  public static RelationType fromResource(Resource res) {
    if(res.isURIResource()) {
      for(RelationType relType : RelationType.values()) {
        if(relType.uri.equals(res.getURI()))
          return relType;
      }
    }
    return null;
  }
  
  public Resource asResource() {
    return ResourceFactory.createResource();
  }

  
}
