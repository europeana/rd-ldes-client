package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

import eu.europeana.metis.ldaggregation.harvesting.ldes.ValidationException;

class LdesNodeTest {

  @Test
  void discoversTypedEventStreamAndItsView() throws ValidationException {
    Dataset dataset = dataset("""
        @prefix ldes: <https://w3id.org/ldes#> .
        @prefix tree: <https://w3id.org/tree#> .
        @prefix ex: <https://example.test/> .
        ex:stream a ldes:EventStream;
          ldes:timestampPath ex:timestamp;
          ldes:versionOfPath ex:versionOf;
          tree:view ex:root .
        """);

    LdesNode node = new LdesNode(dataset.getDefaultModel(), dataset);

    assertEquals("https://example.test/stream", node.getUri());
    assertTrue(node.getView().isPresent());
  }

  @Test
  void discoversTheStreamFromTheCurrentPageViewWithoutRequiringRdfType() {
    Dataset dataset = dataset("""
        @prefix tree: <https://w3id.org/tree#> .
        @prefix ex: <https://example.test/> .
        ex:stream tree:view ex:root; tree:member ex:member .
        ex:member ex:value "one" .
        """);

    assertDoesNotThrow(() -> new LdesNode(dataset.getDefaultModel(), dataset));
  }

  @Test
  void extractsGenericMembersWithoutActivityStreamsProperties() throws ValidationException {
    Dataset dataset = dataset("""
        @prefix ldes: <https://w3id.org/ldes#> .
        @prefix tree: <https://w3id.org/tree#> .
        @prefix ex: <https://example.test/> .
        ex:stream a ldes:EventStream; tree:view ex:root; tree:member ex:member .
        ex:member ex:value "one"; ex:detail [ ex:value "nested" ] .
        """);
    LdesNode node = new LdesNode(dataset.getDefaultModel(), dataset);

    assertEquals(1, node.getMembers().size());
  }

  private static Dataset dataset(String turtle) {
    Dataset dataset = DatasetFactory.createTxnMem();
    RDFParser.fromString(turtle).lang(Lang.TURTLE).parse(dataset);
    return dataset;
  }
}
