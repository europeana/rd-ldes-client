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
  public static final Property LDES_SEQUENCE_PATH = createProperty(LDES_NS, "sequencePath");
  public static final Property LDES_TRANSACTION_PATH = createProperty(LDES_NS, "transactionPath");
  public static final Property LDES_TRANSACTION_FINALIZED_PATH = createProperty(LDES_NS, "transactionFinalizedPath");
  public static final Property LDES_TRANSACTION_FINALIZED_OBJECT = createProperty(LDES_NS, "transactionFinalizedObject");
  public static final Property LDES_IMMUTABLE = createProperty(LDES_NS, "immutable");
  public static final Property LDES_POLLING_INTERVAL = createProperty(LDES_NS, "pollingInterval");
  public static final Property LDES_RETENTION_POLICY = createProperty(LDES_NS, "retentionPolicy");
  public static final Property LDES_VERSION_TIMESTAMP_PATH = createProperty(LDES_NS, "versionTimestampPath");
  public static final Property LDES_VERSION_SEQUENCE_PATH = createProperty(LDES_NS, "versionSequencePath");
  
  public static final String TREE_NS = "https://w3id.org/tree#";
  public static final Property TREE_VIEW = createProperty(TREE_NS, "view");
  public static final Property TREE_MEMBER = createProperty(TREE_NS, "member");
  public static final Property TREE_RELATION = createProperty(TREE_NS, "relation");
  public static final Property TREE_SHAPE = createProperty(TREE_NS, "shape");
  public static final Property TREE_VALUE = createProperty(TREE_NS, "value");
  public static final Property TREE_NODE = createProperty(TREE_NS, "node");
  public static final Property TREE_PATH = createProperty(TREE_NS, "path");
  public static final Property TREE_VIEW_DESCRIPTION = createProperty(TREE_NS, "viewDescription");

  public static final String SHACL_NS = "http://www.w3.org/ns/shacl#";
  public static final Property SH_ALTERNATIVE_PATH = createProperty(SHACL_NS, "alternativePath");
  public static final Property SH_INVERSE_PATH = createProperty(SHACL_NS, "inversePath");
  public static final Property SH_ZERO_OR_MORE_PATH = createProperty(SHACL_NS, "zeroOrMorePath");
  public static final Property SH_ONE_OR_MORE_PATH = createProperty(SHACL_NS, "oneOrMorePath");
  public static final Property SH_ZERO_OR_ONE_PATH = createProperty(SHACL_NS, "zeroOrOnePath");
  
  public static final String ACTIVITY_STREAMS_NS = "https://www.w3.org/ns/activitystreams#";
  public static final Property AS_PUBLISHED = createProperty(ACTIVITY_STREAMS_NS, "published");
//  public static final Property AS_END_TIME = createProperty(ACTIVITY_STREAMS_NS, "endTime");
//  public static final Property AS_START_TIME = createProperty(ACTIVITY_STREAMS_NS, "startTime");
  public static final Property AS_OBJECT = createProperty(ACTIVITY_STREAMS_NS, "object");
  public static final Property AS_TARGET = createProperty(ACTIVITY_STREAMS_NS, "target");
  

}
