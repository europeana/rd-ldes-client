/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes;

/**
 * @author Nuno Freire
 * @since 29/09/2025
 */
public interface TreeTraversal {

  /**
   * executes the processing algorithm
   * 
   * @param streamUrl the URL of the activity stream, which points to an
   *                  OrderedCollection
   * @return a report of the result of the crawl
   * @throws Exception if some unrecoverable exception occours during the crawl
   */
  public CrawlReport processStream(String streamUrl) throws Exception;
  
}
