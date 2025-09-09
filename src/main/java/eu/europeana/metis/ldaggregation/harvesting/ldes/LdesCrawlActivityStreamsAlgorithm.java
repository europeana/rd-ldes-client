package eu.europeana.metis.ldaggregation.harvesting.ldes;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RiotException;
import org.apache.jena.vocabulary.RDF;

import eu.europeana.metis.ldaggregation.harvesting.ldes.http.AccessException;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.ActivityType;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.LdesNode;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Member;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Relation;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.RelationType;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.View;
import eu.europeana.metis.ldaggregation.harvesting.ldes.util.RdfUtil;

/**
 * Implementation of the processing algorithm for an LDES stream using timestamps
 */
/**
 * @author Nuno Freire
 * @since 08/09/2025
 */
public class LdesCrawlActivityStreamsAlgorithm {

	/**
	 * Holds the current state of an ongoing crawl
	 */
	class ProcessState {
		boolean onlyDelete = false;
		Instant lastCrawl = null;
		HashSet<String> processedItems = new HashSet<String>();
		Instant latestCrawledTimestamp = null;
		CrawlReport report;

		public ProcessState(Instant lastCrawl) {
			this.lastCrawl = lastCrawl;
			report = new CrawlReport(streamUrl, lastCrawl);
		}
	}

	ActivityHandler crawler;
	HttpClient httpClient;

	String streamUrl;
	ProcessState status;

	/**
	 * @param crawler the handler for the Activities found during the crawl
	 * @throws Exception
	 */
	public LdesCrawlActivityStreamsAlgorithm(ActivityHandler crawler) {
		  this.crawler = crawler;
		  this.httpClient = new JavaNetHttpClient();
	}
	

	/**
	 * @param crawler the handler for the Activities found during the crawl
	 * @param httpClient
	 * @throws Exception
	 */
	public LdesCrawlActivityStreamsAlgorithm(ActivityHandler crawler, HttpClient httpClient) {
    super();
    this.crawler = crawler;
    this.httpClient = httpClient;
  }



  /**
	 * executes the processing algorithm
	 * 
	 * @param streamUrl the URL of the activity stream, which points to an
	 *                  OrderedCollection
	 * @return a report of the result of the crawl
	 * @throws Exception if some unrecoverable exception occours during the crawl
	 */
	public CrawlReport processStream(String streamUrl) throws Exception {
		this.streamUrl = streamUrl;
		status = new ProcessState(crawler.getLastCrawlTimestamp(streamUrl));
		processRootNode();
		return status.report;
	}

	protected void processRootNode() throws ValidationException, Exception {
		try {
			crawler.crawlStart(streamUrl);
			crawler.log("Last crawl: " + status.lastCrawl);
			crawler.log("Processing view " + streamUrl);

			Dataset nodeQuads = RdfUtil.readRdfQuadsFromUri(streamUrl);
			Model rootNode = nodeQuads.getDefaultModel();
			
			LdesNode node=new LdesNode(rootNode, nodeQuads);
//			LdesNode node=new LdesNode(streamRs);
			validateStream(node);
			processNode(node);

      status.report.setLatestCrawledTimestamp(status.latestCrawledTimestamp);
			crawler.crawlEnd(status.latestCrawledTimestamp);
		} catch (Exception e) {
		  e.printStackTrace();
			status.report.setCrawlFailure(e.getMessage());
			crawler.crawlFail(e.getMessage(), e);
		}
	}

	 protected void processNode(LdesNode stream) throws ValidationException, AccessException, InterruptedException, IOException {
	      Optional<View> view=stream.getView();
	      if(view.isPresent())
	        processView(view.get());
	      List<Member> members=stream.getMembers();
	      processMembers(members);
	}
	
	protected void processNode(String nodeUrl) throws ValidationException, AccessException, InterruptedException, IOException  {
	    crawler.log("Processing node " + nodeUrl);
	    try {
        Dataset nodeQuads = RdfUtil.readRdfQuadsFromUri(nodeUrl);
        Model node = nodeQuads.getDefaultModel();
  	    Resource streamRs = RdfUtil.findFirstResourceWithProperties(node, RDF.type, LdesConstants.LDES_EVENT_STREAM, null, null);
  	    if(streamRs==null)
  	      throw new ValidationException("LDES stream not found at "+nodeUrl);
        LdesNode stream=new LdesNode(node, nodeQuads);
        processNode(stream);
      } catch (AccessException | IOException | ValidationException | RiotException e) {
        crawler.log("Error processing node "+ nodeUrl+"\nskipping it.");
        e.printStackTrace(System.out);
      }
	}


  /**
   * @param stream
   */
  private void validateStream(LdesNode stream) throws ValidationException{
    if(stream.getTimestampPath()==null)
      throw new ValidationException("LDES feed is not compatible with the crawler algorithm: ldes:timestampPath not present.");
  }


  /**
   * @param members
   */
  private void processMembers(List<Member> members) throws ValidationException, IOException {
    for(Member member: members) 
      member.validate();
    List<Member> membersSortedDescending=new ArrayList<Member>(members);
//    DEV.print("Processing "+membersSortedDescending.size()+ " members");
    Collections.sort(membersSortedDescending);
    for (Member activity : membersSortedDescending) {
      if (status.lastCrawl != null && !activity.getTimestamp().isAfter(status.lastCrawl) ) {
//        DEV.traceResource(activity.getObject().getURI(), activity.getTypeOfActivity()+ " before last crawl");
//        DEV.print(activity.getTypeOfActivity()+ " before last crawl on "+ activity.getObject().getURI());
//        DEV.print(activity.getTypeOfActivity()+" " +activity.getTimestamp()+" before last crawl on "+ activity.getObject().getURI());
//        crawler.log("Crawl finished - timestamp of last crawl reached.");
//        continue;
        break;
      }
//      DEV.traceResource(activity.getObject().getURI(), activity.getTypeOfActivity()+ "");
//      DEV.print(activity.getTypeOfActivity()+" " +activity.getTimestamp()+" on "+ activity.getObject().getURI());
      if (status.latestCrawledTimestamp == null)
        status.latestCrawledTimestamp = activity.getTimestamp();
//      if (activity.getTypeOfActivity() == ActivityType.Refresh) {
//        if (status.lastCrawl == null) {
//          continueToNextPage = false;
//        } else {
//          status.onlyDelete = true;
//        }
//        status.report.incrementActivity(activity.getTypeOfActivity());
//      } else if (status.processedItems.contains(activity.getObject().getId())) {
      if (status.processedItems.contains(activity.getObject().getURI())) {
//        DEV.trace("Already processed - "+ activity.getTypeOfActivity()+" on "+activity.getObject().getURI());
        continue;
//      } else if (!crawler.isSupportedResourceType(activity.getObject().getType())) {
//        status.processedItems.add(activity.getObject().getId());
//        // do nothing
//      } else if (activity.getTypeOfActivity() == ActivityType.Remove) {
//        String originId = activity.getOriginId();
//        if (originId == null || originId.equals(streamUrl)) {
//          status.report.incrementActivity(activity.getTypeOfActivity());
//          crawler.processActivity(activity);
//          status.processedItems.add(activity.getObject().getId());
//        }
      } else if (activity.getTypeOfActivity() == ActivityType.Delete) {
        status.report.incrementActivity(activity.getTypeOfActivity());
        crawler.processActivity(activity);
        status.processedItems.add(activity.getObject().getURI());
      } else if (status.onlyDelete) {
        // do nothing
//      } else if (activity.getTypeOfActivity() == ActivityType.Add) {
//        String targetId = activity.getTarget().getURI();
//        if (targetId == null || targetId.equals(streamUrl)) {
//          status.report.incrementActivity(activity.getTypeOfActivity());
//          crawler.processActivity(activity);
//          status.processedItems.add(activity.getObject().getId());
//        }
      } else if (activity.getTypeOfActivity() == ActivityType.Create
          || activity.getTypeOfActivity() == ActivityType.Update) {
        status.report.incrementActivity(activity.getTypeOfActivity());
        crawler.processActivity(activity);
        status.processedItems.add(activity.getObject().getURI());
      } else if (activity.getTypeOfActivity() == ActivityType.Move) {
//        if (activity.getObject().getType().equals(activity.getTargetType())) {
          status.report.incrementActivity(activity.getTypeOfActivity());
          crawler.processActivity(activity);
          status.processedItems.add(activity.getObject().getURI());
          status.processedItems.add(activity.getTarget().getURI());
//        }
      }
    }
  }


  /**
   * @param view
   * @param quadsDataset 
   * @throws IOException 
   * @throws InterruptedException 
   * @throws AccessException 
   * @throws ValidationException 
   */
  private void processView(View view) throws ValidationException, AccessException, InterruptedException, IOException {
    List<NodeRelations> toProcess=new ArrayList<>();
    for(NodeRelations rels:view.getNodeRelations()) {
      if(!rels.mayBePrunned(status.lastCrawl))
        toProcess.add(rels);
    }
    for(NodeRelations nRels : toProcess)
        processNode(nRels.getNodeUrl());
  }


  /**
   * @param viewRs
   */
	protected void processView(Resource viewRs) {
    
  }

//	protected String processPage(String pageUri) throws ValidationException, IOException {
//		crawler.log("Processing page " + pageUri);
//		JsonObject topLevelJson = JsonUtil.readJson(pageUri, httpClient);
//		OrderedCollectionPage orderedCollectionPage = new OrderedCollectionPage(topLevelJson);
//		orderedCollectionPage.validateJson();
//		boolean continueToNextPage = true;
//		for (Activity activity : orderedCollectionPage.getActivitiesInReverseOrder()) {
//			if (status.lastCrawl != null && activity.endsBefore(status.lastCrawl)) {
//				continueToNextPage = false;
//				crawler.log("Crawl finished - timestamp of last crawl reached.");
//				break;
//			}
////			System.out.println(activity.getTypeOfActivity()+" "+activity.getObject().getType());
//			if (status.latestCrawledTimestamp == null)
//				status.latestCrawledTimestamp = activity.getTypeOfActivity() == ActivityType.Refresh
//						? activity.getStartTime()
//						: activity.getEndTime();
//			if (activity.getTypeOfActivity() == ActivityType.Refresh) {
//				if (status.lastCrawl == null) {
//					continueToNextPage = false;
//				} else {
//					status.onlyDelete = true;
//				}
//				status.report.incrementActivity(activity.getTypeOfActivity());
//			} else if (status.processedItems.contains(activity.getObject().getId())) {
////				System.out.println("Already processed - "+ activity.getType()+" on "+activity.getObject().getId());
//				continue;
//			} else if (!crawler.isSupportedResourceType(activity.getObject().getType())) {
//				status.processedItems.add(activity.getObject().getId());
//				// do nothing
//			} else if (activity.getTypeOfActivity() == ActivityType.Remove) {
//				String originId = activity.getOriginId();
//				if (originId == null || originId.equals(streamUrl)) {
//					status.report.incrementActivity(activity.getTypeOfActivity());
//					crawler.processActivity(activity);
//					status.processedItems.add(activity.getObject().getId());
//				}
//			} else if (activity.getTypeOfActivity() == ActivityType.Delete) {
//				status.report.incrementActivity(activity.getTypeOfActivity());
//				crawler.processActivity(activity);
//				status.processedItems.add(activity.getObject().getId());
//			} else if (status.onlyDelete) {
//				// do nothing
//			} else if (activity.getTypeOfActivity() == ActivityType.Add) {
//				String targetId = activity.getTargetId();
//				if (targetId == null || targetId.equals(streamUrl)) {
//					status.report.incrementActivity(activity.getTypeOfActivity());
//					crawler.processActivity(activity);
//					status.processedItems.add(activity.getObject().getId());
//				}
//			} else if (activity.getTypeOfActivity() == ActivityType.Create
//					|| activity.getTypeOfActivity() == ActivityType.Update) {
//				status.report.incrementActivity(activity.getTypeOfActivity());
//				crawler.processActivity(activity);
//				status.processedItems.add(activity.getObject().getId());
//			} else if (activity.getTypeOfActivity() == ActivityType.Move) {
//				if (activity.getObject().getType().equals(activity.getTargetType())) {
//					status.report.incrementActivity(activity.getTypeOfActivity());
//					crawler.processActivity(activity);
//					status.processedItems.add(activity.getObject().getId());
//					status.processedItems.add(activity.getTargetId());
//				}
//			}
//		}
//		return continueToNextPage ? orderedCollectionPage.getPreviousPageId() : null;
//	}

}
