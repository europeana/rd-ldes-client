package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;

import eu.europeana.metis.ldaggregation.harvesting.ldes.LdesConstants;
import eu.europeana.metis.ldaggregation.harvesting.ldes.ValidationException;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.AccessException;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Member;
import eu.europeana.metis.ldaggregation.harvesting.ldes.util.RdfUtil;
import eu.europeana.metis.ldaggregation.harvesting.ldes.util.RdfUtil.Jena;

public class LdesNode {
  Resource eventStreamRes;
  Dataset quadsDataset;
  
//	public LdesNode(String nodeUri, Dataset quadsDataset) throws AccessException, InterruptedException, IOException, IllegalArgumentException {
//	  Model model=RdfUtil.readRdfFromUri(nodeUri);
//    if(model==null)
//      throw new IllegalArgumentException("Not RDF found at "+nodeUri);
//    eventStreamRes = model.listResourcesWithProperty(RDF.type, LdesConstants.LDES_EVENT_STREAM).nextOptional().orElse(null);
//    if (eventStreamRes==null) 
//      throw new IllegalArgumentException("An ldes:EventStream was not found at "+nodeUri);
//    this.quadsDataset=quadsDataset;
//	}

	/**
   * @param rootNode
	 * @throws ValidationException 
   */
  public LdesNode(Model rootNode, Dataset quadsDataset) throws ValidationException {
//    System.out.println(RdfUtil.printStatements(rootNode));
    this.quadsDataset= quadsDataset;
    eventStreamRes = RdfUtil.findFirstResourceWithProperties(rootNode, RDF.type, LdesConstants.LDES_EVENT_STREAM, null, null);
    if(eventStreamRes==null) {
//      throw new ValidationException("LDES stream not found");
      //TODO: remove this block. It is a hack to bypass the incorrect JSON-LD from RijksMuseum
      eventStreamRes = RdfUtil.findFirstResourceWithProperties(rootNode, LdesConstants.LDES_TIMESTAMP_PATH, LdesConstants.AS_PUBLISHED, null, null);
      if(eventStreamRes==null) 
        throw new ValidationException("LDES stream not found");
      //END OF BLOCK
    }   
  }

  public String getUri() {
		return eventStreamRes.getURI();
	}

	public Resource getVersionOfPath() {
		return eventStreamRes.getPropertyResourceValue(LdesConstants.LDES_VERSION_OF_PATH);
	}

	public Property getTimestampPath() {
	  return ResourceFactory.createProperty(eventStreamRes.getPropertyResourceValue(LdesConstants.LDES_TIMESTAMP_PATH).getURI());
	}

	public Resource getShaclShapeUri() {
	  return eventStreamRes.getPropertyResourceValue(LdesConstants.TREE_SHAPE);
	}

	public boolean containsRequiredProperties() {
		return getTimestampPath() != null && getVersionOfPath() != null;
	}

  /**
   * @return
   */
  public Optional<View> getView() {
    Resource viewRs = eventStreamRes.getPropertyResourceValue(LdesConstants.TREE_VIEW);
    if(viewRs!=null)
      return Optional.of(new View(viewRs));
    return Optional.empty();
  }

  /**
   * @return
   */
  public List<Member> getMembers() throws ValidationException {
    List<Member> members=new ArrayList<>();
    StmtIterator memberRss = eventStreamRes.listProperties(LdesConstants.TREE_MEMBER);
    if(memberRss!=null && memberRss.hasNext()) {
      for(Statement st: memberRss.toList()) 
        members.add(new Member(st.getObject().asResource(), quadsDataset, getTimestampPath(), getVersionOfPath()));
    }
    return members;
  }

  /**
   * @return
   */
  public Dataset getQuadsDataset() {
    return quadsDataset;
  }
}
