package eu.europeana.metis.ldaggregation.harvesting.ldes.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

class ShaclPathTest {

  private static final String NS = "https://example.test/";

  @Test
  void evaluatesDirectSequenceAlternativeAndInversePaths() {
    Model model = model("""
        @prefix ex: <https://example.test/> .
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        ex:start ex:next ex:middle; ex:fallback "alternative" .
        ex:middle ex:value "sequence" .
        ex:owner ex:member ex:start .
        ex:paths ex:sequencePath (ex:next ex:value);
          ex:alternativePath [ sh:alternativePath (ex:missing ex:fallback) ];
          ex:inversePath [ sh:inversePath ex:member ] .
        """);
    Resource start = model.getResource(NS + "start");

    assertEquals(List.of("https://example.test/middle"),
        values(ShaclPath.evaluate(model, start, model.getResource(NS + "next"))));
    assertEquals(List.of("sequence"),
        values(ShaclPath.evaluate(model, start, path(model, "sequencePath"))));
    assertEquals(List.of("alternative"),
        values(ShaclPath.evaluate(model, start, path(model, "alternativePath"))));
    assertEquals(List.of("https://example.test/owner"),
        values(ShaclPath.evaluate(model, start, path(model, "inversePath"))));
  }

  @Test
  void evaluatesRepetitionPathsWithCycles() {
    Model model = model("""
        @prefix ex: <https://example.test/> .
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        ex:start ex:next ex:middle .
        ex:middle ex:next ex:start .
        ex:paths ex:zeroOrOne [ sh:zeroOrOnePath ex:next ];
          ex:zeroOrMore [ sh:zeroOrMorePath ex:next ];
          ex:oneOrMore [ sh:oneOrMorePath ex:next ] .
        """);
    Resource start = model.getResource(NS + "start");

    assertEquals(List.of(NS + "start", NS + "middle"),
        values(ShaclPath.evaluate(model, start, path(model, "zeroOrOne"))));
    assertEquals(List.of(NS + "start", NS + "middle"),
        values(ShaclPath.evaluate(model, start, path(model, "zeroOrMore"))));
    assertEquals(List.of(NS + "middle", NS + "start"),
        values(ShaclPath.evaluate(model, start, path(model, "oneOrMore"))));
  }

  private static Model model(String turtle) {
    Model model = ModelFactory.createDefaultModel();
    RDFParser.fromString(turtle).lang(Lang.TURTLE).parse(model);
    return model;
  }

  private static RDFNode path(Model model, String name) {
    return model.getResource(NS + "paths").getProperty(model.createProperty(NS + name)).getObject();
  }

  private static List<String> values(List<RDFNode> nodes) {
    return nodes.stream().map(node -> node.isLiteral()
        ? node.asLiteral().getLexicalForm() : node.asResource().getURI()).toList();
  }
}
