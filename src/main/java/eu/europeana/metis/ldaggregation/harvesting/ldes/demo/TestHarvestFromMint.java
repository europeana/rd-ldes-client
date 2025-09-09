package eu.europeana.metis.ldaggregation.harvesting.ldes.demo;

public class TestHarvestFromMint {

	public static void main(String[] args) throws Exception {
	  String aggregator="museu";
//	  String dataset="1052";	//92  
//	  String dataset="1105";	  //error
	  String dataset="1158";	  
		EdmAggregationDemonstrator.main(new String[] {
//				"-u", "https://mint-ldes.ails.ece.ntua.gr/euscreenxl/1008/ldes", 
				"-u", "https://mint-ldes.ails.ece.ntua.gr/"+aggregator+"/"+dataset+"/ldes", 
				"-d", "target/syncdb", 
				"-m", "target/metadata-repository/"+aggregator+"-"+dataset});
	}
}
