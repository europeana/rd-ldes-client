/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import java.time.Instant;
import java.util.Calendar;

import org.apache.jena.datatypes.xsd.XSDDateTime;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

import eu.europeana.metis.ldaggregation.harvesting.ldes.LdesConstants;
import eu.europeana.metis.ldaggregation.harvesting.ldes.ValidationException;

/**
 * @author Nuno Freire
 * @since 26/05/2025
 */
public class Relation {
  Resource relRs;

  public Relation(Resource relRs) {
    this.relRs = relRs;
  }
  
  public RelationType getType() {
    Resource typeRes = relRs.getPropertyResourceValue(RDF.type);
    if(typeRes!=null)
      return RelationType.fromResource(typeRes);
    return null;
  }

  /**
   * 
   */
  public Instant getValueAsInstant() {
    Statement st = relRs.getProperty(LdesConstants.TREE_VALUE);
    if(st!=null && st.getObject().isLiteral() && st.getObject().asLiteral().getDatatype()!=null
        && st.getObject().asLiteral().getDatatype().getJavaClass().equals(XSDDateTime.class)) {
      XSDDateTime xsdDateTime = (XSDDateTime) st.getObject().asLiteral().getValue();
      return xsdDateTime.asCalendar().toInstant();
    }
    return null;
  }

  /**
   * @throws ValidationException 
   * 
   */
  public String getNodeUrl() throws ValidationException {
    Statement st = relRs.getProperty(LdesConstants.TREE_NODE);
    if(st==null || !st.getObject().isURIResource()) 
      throw new ValidationException("Found a relation without a tree:node property with an URI");
    return st.getObject().asResource().getURI();  
  }

  /**
   * @param previousCrawlTs
   * @return
   */
  public boolean mayBePrunned(Instant previousCrawlTs) {
      RelationType relType = getType();
      if(relType==null)
        return true; //unknown relation type. prune it.
      Instant relValue = getValueAsInstant();
      if(relValue==null)
        return true; //not a timestamp based relation, so prune it
      if(previousCrawlTs==null)
        return false; //if first crawl, process all timestamp based relations
      switch (relType) {
      case GreaterThanOrEqualToRelation:
      case GreaterThanRelation:
        return false;
      case LessThanOrEqualToRelation:
        return previousCrawlTs.isAfter(relValue);
      case LessThanRelation:
        return ! previousCrawlTs.isBefore(relValue);
      case EqualToRelation:
        return ! previousCrawlTs.equals(relValue);
      case NotEqualToRelation:
        return  previousCrawlTs.equals(relValue);
      default:
        throw new RuntimeException("Missing implementation for relation type: "+relType);
      }  
  }

}
