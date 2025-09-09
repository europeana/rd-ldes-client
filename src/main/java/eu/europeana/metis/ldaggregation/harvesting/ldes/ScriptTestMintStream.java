/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes;

import java.io.IOException;
import java.time.Instant;

import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Member;

/**
 * @author Nuno Freire
 * @since 23/05/2025
 */
public class ScriptTestMintStream {

  
  public static void main(String[] args) throws Exception {
    LdesCrawlActivityStreamsAlgorithm crawler=new LdesCrawlActivityStreamsAlgorithm(new ActivityHandler() {
      
      @Override
      public void processActivity(Member activity) throws IOException {
        System.out.println(activity.toString());
        
      }
      
      @Override
      public void log(String message, Exception ex) {
      }
      
      @Override
      public void log(String message) {
        System.out.println(message);
      }
      
      @Override
      public boolean isSupportedResourceType(String type) {
        return false;
      }
      
      @Override
      public Instant getLastCrawlTimestamp(String streamUri) {
        return null;
      }
      
      @Override
      public void crawlStart(String streamUri) {
      }
      
      @Override
      public void crawlFail(String errorMessage, Exception cause) throws Exception {
        cause.printStackTrace();
      }
      
      @Override
      public void crawlEnd(Instant latestTimestamp) throws Exception {
      }
    });
    CrawlReport report=crawler.processStream("https://mint-ldes.ails.ece.ntua.gr/euscreenxl/1008/ldes");
    System.out.println(report);
  }
}
