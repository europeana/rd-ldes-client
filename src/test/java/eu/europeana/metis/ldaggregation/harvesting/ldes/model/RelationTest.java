package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RelationTest {

  @ParameterizedTest
  @MethodSource("lowerBoundRelations")
  void lowerBoundRelationsCannotBePruned(String relationType) {
    Relation relation = relation(relationType, "2025-01-01T00:00:00Z");

    assertFalse(relation.mayBePrunned(Instant.parse("2025-02-01T00:00:00Z")));
  }

  @ParameterizedTest
  @MethodSource("upperBoundRelations")
  void obsoleteUpperBoundRelationsArePruned(String relationType) {
    Relation relation = relation(relationType, "2025-01-01T00:00:00Z");

    assertTrue(relation.mayBePrunned(Instant.parse("2025-02-01T00:00:00Z")));
  }

  private static Stream<Arguments> lowerBoundRelations() {
    return Stream.of(Arguments.of("GreaterThanRelation"), Arguments.of("GreaterThanOrEqualToRelation"));
  }

  private static Stream<Arguments> upperBoundRelations() {
    return Stream.of(Arguments.of("LessThanRelation"), Arguments.of("LessThanOrEqualToRelation"));
  }

  private static Relation relation(String type, String value) {
    Model model = ModelFactory.createDefaultModel();
    RDFParser.fromString("""
        @prefix tree: <https://w3id.org/tree#> .
        @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
        <https://example.test/relation> a tree:%s;
          tree:node <https://example.test/node>;
          tree:value "%s"^^xsd:dateTime .
        """.formatted(type, value)).lang(Lang.TURTLE).parse(model);
    Resource resource = model.getResource("https://example.test/relation");
    return new Relation(resource);
  }
}
