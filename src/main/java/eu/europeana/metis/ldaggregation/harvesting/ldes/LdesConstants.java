/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * @author Nuno Freire
 * @since 07/05/2025
 */
public class LdesConstants {
  public static final String LDES_NS = "https://w3id.org/ldes#";
  public static final Resource LDES_EVENT_STREAM = createResource(LDES_NS+"EventStream");
  public static final Property LDES_VERSION_OF_PATH = createProperty(LDES_NS, "versionOfPath");
  public static final Property LDES_TIMESTAMP_PATH = createProperty(LDES_NS, "timestampPath");
  
  public static final String TREE_NS = "https://w3id.org/tree#";
  public static final Property TREE_VIEW = createProperty(TREE_NS, "view");
  public static final Property TREE_MEMBER = createProperty(TREE_NS, "member");
  public static final Property TREE_RELATION = createProperty(TREE_NS, "relation");
  public static final Property TREE_SHAPE = createProperty(TREE_NS, "shape");
  public static final Property TREE_VALUE = createProperty(TREE_NS, "value");
  public static final Property TREE_NODE = createProperty(TREE_NS, "node");
  
  public static final String ACTIVITY_STREAMS_NS = "https://www.w3.org/ns/activitystreams#";
  public static final Property AS_PUBLISHED = createProperty(ACTIVITY_STREAMS_NS, "published");
//  public static final Property AS_END_TIME = createProperty(ACTIVITY_STREAMS_NS, "endTime");
//  public static final Property AS_START_TIME = createProperty(ACTIVITY_STREAMS_NS, "startTime");
  public static final Property AS_OBJECT = createProperty(ACTIVITY_STREAMS_NS, "object");
  public static final Property AS_TARGET = createProperty(ACTIVITY_STREAMS_NS, "target");
  

}
