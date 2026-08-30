package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

class MemberTest {

  private static final String MEMBER = "https://example.test/member";

  @Test
  void extractsDefaultGraphClosureAndTheMemberNamedGraphOnly() throws Exception {
    Dataset source = DatasetFactory.createTxnMem();
    RDFParser.fromString("""
        @prefix ex: <https://example.test/> .
        ex:member ex:value "default"; ex:detail [ ex:value "nested"; ex:back ex:member ] .
        ex:unrelated ex:value "exclude" .
        ex:member { ex:member ex:value "named" . ex:other ex:value "named too" }
        ex:otherGraph { ex:member ex:value "wrong graph" }
        """).lang(Lang.TRIG).parse(source);
    Resource memberResource = source.getDefaultModel().getResource(MEMBER);

    Dataset extracted = new Member(memberResource, source, null, null).getDataset();

    assertEquals(4, extracted.getDefaultModel().size());
    assertTrue(extracted.containsNamedModel(MEMBER));
    assertEquals(2, extracted.getNamedModel(MEMBER).size());
    assertFalse(extracted.containsNamedModel("https://example.test/otherGraph"));
    assertFalse(extracted.getDefaultModel().containsResource(
        extracted.getDefaultModel().getResource("https://example.test/unrelated")));
  }
}
