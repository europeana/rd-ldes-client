package eu.europeana.metis.ldaggregation.harvesting.ldes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RdfUtilTest {

  private static final String SUBJECT = "https://example.test/member/1";
  private static final String PREDICATE = "https://example.test/value";

  @ParameterizedTest(name = "parses {0}")
  @MethodSource("supportedRepresentations")
  @Tag("spec-compliance-pending")
  void parsesEveryRequiredRdfRepresentation(String name, Lang language, String rdf) {
    Dataset dataset = RdfUtil.readRdfQuads(rdf.getBytes(StandardCharsets.UTF_8), language);

    assertEquals(1, dataset.asDatasetGraph().size(), name);
  }

  @Test
  @Tag("spec-compliance-pending")
  void advertisesEveryRequiredRdfRepresentation() {
    String accept = RdfUtil.CONTENT_TYPES_QUADS_ACCEPT_HEADER;

    assertTrue(accept.contains("application/n-quads"));
    assertTrue(accept.contains("application/n-triples"));
    assertTrue(accept.contains("application/trig"));
    assertTrue(accept.contains("text/turtle"));
    assertTrue(accept.contains("application/ld+json"));
  }

  private static Stream<Arguments> supportedRepresentations() {
    return Stream.of(
        Arguments.of("N-Quads", Lang.NQUADS,
            "<" + SUBJECT + "> <" + PREDICATE + "> \"one\" <https://example.test/graph> ."),
        Arguments.of("N-Triples", Lang.NTRIPLES,
            "<" + SUBJECT + "> <" + PREDICATE + "> \"one\" ."),
        Arguments.of("TriG", Lang.TRIG,
            "<https://example.test/graph> { <" + SUBJECT + "> <" + PREDICATE + "> \"one\" }"),
        Arguments.of("Turtle", Lang.TURTLE,
            "<" + SUBJECT + "> <" + PREDICATE + "> \"one\" ."),
        Arguments.of("JSON-LD", Lang.JSONLD,
            "{\"@id\":\"" + SUBJECT + "\",\"" + PREDICATE + "\":\"one\"}"));
  }
}
