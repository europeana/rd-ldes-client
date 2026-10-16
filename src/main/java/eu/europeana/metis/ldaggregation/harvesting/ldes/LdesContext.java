package eu.europeana.metis.ldaggregation.harvesting.ldes;

import java.util.List;

import org.apache.jena.query.Dataset;

/** Context information communicated by an LDES root node. */
public record LdesContext(
    String eventStream,
    String rootNode,
    Dataset dataset,
    List<String> shapes,
    List<String> viewDescriptions,
    List<String> retentionPolicies,
    /** Retention policies the page announces without describing them further. */
    List<String> emptyRetentionPolicies,
    String timestampPath,
    String sequencePath,
    String versionTimestampPath,
    String versionSequencePath,
    Integer pollingInterval) {
}
